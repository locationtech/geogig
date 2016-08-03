/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.AbstractObjectStore;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;

public class RocksdbObjectStore extends AbstractObjectStore implements ObjectStore {

    private volatile boolean open;

    protected final String path;

    protected final boolean readOnly;

    private DBHandle dbhandle;

    private RocksDB db;

    @Inject
    public RocksdbObjectStore(Platform platform, @Nullable Hints hints) {
        super(DataStreamSerializationFactoryV2.INSTANCE);

        Optional<URI> repoUriOpt = new ResolveGeogigURI(platform, hints).call();
        checkArgument(repoUriOpt.isPresent(), "couldn't resolve geogig directory");
        URI uri = repoUriOpt.get();
        checkArgument("file".equals(uri.getScheme()));
        this.path = new File(new File(uri), "objects.rocksdb").getAbsolutePath();

        this.readOnly = hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
    }

    @Override
    public synchronized void open() {
        if (isOpen()) {
            return;
        }
        DBOptions address = new DBOptions(path, readOnly);
        this.dbhandle = RocksConnectionManager.INSTANCE.acquire(address);
        this.db = dbhandle.db;
        open = true;
    }

    @Override
    public synchronized void close() {
        if (!open) {
            return;
        }
        open = false;

        final DBHandle dbhandle = this.dbhandle;
        this.db = null;
        this.dbhandle = null;

        RocksConnectionManager.INSTANCE.release(dbhandle);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    private void checkOpen() {
        Preconditions.checkState(isOpen(), "Database is closed");
    }

    public void checkWritable() {
        checkOpen();
        if (readOnly) {
            throw new IllegalStateException("db is read only.");
        }
    }

    @Override
    protected boolean putInternal(ObjectId id, byte[] rawData) {
        checkWritable();
        byte[] key = id.getRawValue();
        boolean exists;
        try (ReadOptions ro = new ReadOptions()) {
            ro.setFillCache(false);
            ro.setVerifyChecksums(false);
            exists = exists(ro, key);
        }
        if (!exists) {
            try {
                db.put(key, rawData);
            } catch (RocksDBException e) {
                throw Throwables.propagate(e);
            }
        }
        return !exists;
    }

    @Override
    protected InputStream getRawInternal(ObjectId id, boolean failIfNotFound)
            throws IllegalArgumentException {

        byte[] bytes = getRawInternal(id.getRawValue(), null, null);

        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        if (failIfNotFound) {
            throw new IllegalArgumentException("object does not exist: " + id);
        }
        return null;
    }

    @Nullable
    private byte[] getRawInternal(byte[] key, @Nullable byte[] outBuff,
            @Nullable AtomicInteger outSize) throws IllegalArgumentException {

        try {
            if (outBuff == null) {
                outBuff = db.get(key);
            } else {
                final int size = db.get(key, outBuff);
                if (size == RocksDB.NOT_FOUND) {
                    outBuff = null;
                } else if (size > outBuff.length) {
                    int newBuffSize = 1024 * (1 + (size / 1024));
                    outBuff = new byte[newBuffSize];
                    db.get(key, outBuff);
                }
                if (outSize != null) {
                    outSize.set(size);
                }
            }
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }

        return outBuff;
    }

    @Override
    public boolean exists(ObjectId id) {
        checkOpen();
        checkNotNull(id, "argument id is null");
        try (ReadOptions ro = new ReadOptions()) {
            ro.setFillCache(false).setVerifyChecksums(false);
            return exists(ro, id.getRawValue());
        }
    }

    private static final byte[] NO_DATA = new byte[0];

    private static final StringBuffer existsBuff = new StringBuffer();

    private boolean exists(ReadOptions readOptions, byte[] key) {
        int size = RocksDB.NOT_FOUND;
        existsBuff.setLength(0);
        if (db.keyMayExist(key, existsBuff)) {
            try {
                size = db.get(key, NO_DATA);
            } catch (RocksDBException e) {
                throw Throwables.propagate(e);
            }
        }

        return size != RocksDB.NOT_FOUND;
    }

    @Override
    public void delete(ObjectId objectId) {
        checkNotNull(objectId, "argument objectId is null");
        checkWritable();
        byte[] key = objectId.getRawValue();
        try {
            db.remove(key);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, final BulkOpListener listener) {
        return getAll(ids, listener, RevObject.class);
    }

    private static class BatchGet implements Function<List<ObjectId>, Iterator<RevObject>> {

        private final RocksdbObjectStore store;

        private final BulkOpListener listener;

        private final Predicate<Object> filter;

        public BatchGet(RocksdbObjectStore store, BulkOpListener listener, final Class<?> filter) {
            this.store = store;
            this.listener = listener;
            this.filter = RevObject.class.equals(filter) ? Predicates.alwaysTrue()
                    : Predicates.instanceOf(filter);
        }

        @Override
        public Iterator<RevObject> apply(List<ObjectId> input) {
            store.checkOpen();
            SortedSet<ObjectId> sortedIds = new TreeSet<>(input);
            List<RevObject> objects = new ArrayList<>(input.size());
            try (ReadOptions ops = new ReadOptions()) {
                ops.setFillCache(false);// better for bulk ops
                ops.setVerifyChecksums(false);

                byte[] keybuff = new byte[ObjectId.NUM_BYTES];

                try (RocksIterator rocksit = store.db.newIterator(ops)) {
                    for (ObjectId id : sortedIds) {
                        RevObject object = null;
                        id.getRawValue(keybuff);
                        rocksit.seek(keybuff);
                        if (rocksit.isValid()) {
                            byte[] currentKey = rocksit.key();
                            if (Arrays.equals(keybuff, currentKey)) {
                                byte[] value = rocksit.value();
                                try {
                                    object = store.serializer.read(id,
                                            new ByteArrayInputStream(value));
                                    if (filter.apply(object)) {
                                        objects.add(object);
                                        listener.found(id, Integer.valueOf(value.length));
                                        continue;
                                    }
                                } catch (IOException e) {
                                    throw Throwables.propagate(e);
                                }
                            }
                        }
                        listener.notFound(id);
                    }
                }
            }
            return objects.iterator();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends RevObject> Iterator<T> getAll(final Iterable<ObjectId> ids,
            final BulkOpListener listener, final Class<T> type) {
        checkNotNull(ids, "ids is null");
        checkNotNull(listener, "listener is null");
        checkNotNull(type, "type is null");
        checkOpen();

        final int partitionSize = 500;
        Iterator<List<ObjectId>> partitions = Iterables.partition(ids, partitionSize).iterator();

        Iterator<Iterator<RevObject>> objects = Iterators.transform(partitions,
                new BatchGet(this, listener, type));

        Iterator<RevObject> iterator = Iterators.concat(objects);
        return (Iterator<T>) iterator;
    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
        checkNotNull(ids, "argument objectId is null");
        checkNotNull(listener, "argument listener is null");
        checkWritable();

        final boolean checkExists = !BulkOpListener.NOOP_LISTENER.equals(listener);

        byte[] keybuff = new byte[ObjectId.NUM_BYTES];

        try (ReadOptions ro = new ReadOptions()) {
            ro.setFillCache(false);
            ro.setVerifyChecksums(false);
            try (WriteOptions writeOps = new WriteOptions()) {
                writeOps.setSync(false);
                while (ids.hasNext()) {
                    ObjectId id = ids.next();
                    id.getRawValue(keybuff);
                    if (!checkExists || exists(ro, keybuff)) {
                        try {
                            db.remove(writeOps, keybuff);
                        } catch (RocksDBException e) {
                            throw Throwables.propagate(e);
                        }
                        listener.deleted(id);
                    } else {
                        listener.notFound(id);
                    }
                }
                writeOps.sync();
            }
        }
    }

    @Override
    protected List<ObjectId> lookUpInternal(byte[] idprefix) {
        List<ObjectId> matches = new ArrayList<>(2);
        try (RocksIterator it = db.newIterator()) {
            it.seek(idprefix);
            while (it.isValid()) {
                byte[] key = it.key();
                for (int i = 0; i < idprefix.length; i++) {
                    if (idprefix[i] != key[i]) {
                        break;
                    }
                }
                ObjectId id = ObjectId.createNoClone(key);
                matches.add(id);
                it.next();
            }
        }
        return matches;
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects, final BulkOpListener listener) {
        checkNotNull(objects, "objects is null");
        checkNotNull(listener, "listener is null");
        checkWritable();

        final boolean checkExists = !BulkOpListener.NOOP_LISTENER.equals(listener);

        ByteArrayOutputStream rawOut = new ByteArrayOutputStream(4096);
        byte[] keybuff = new byte[ObjectId.NUM_BYTES];

        try (WriteOptions wo = new WriteOptions()) {
            wo.setDisableWAL(true);
            wo.setSync(false);
            try (ReadOptions ro = new ReadOptions()) {
                ro.setFillCache(false);
                ro.setVerifyChecksums(false);
                while (objects.hasNext()) {
                    Iterator<? extends RevObject> partition = Iterators.limit(objects, 10_000);
                    try (WriteBatch batch = new WriteBatch()) {
                        while (partition.hasNext()) {
                            RevObject object = partition.next();
                            rawOut.reset();
                            writeObject(object, rawOut);

                            object.getId().getRawValue(keybuff);
                            final byte[] value = rawOut.toByteArray();

                            boolean exists = checkExists ? exists(ro, keybuff) : false;
                            if (exists) {
                                listener.found(object.getId(), null);
                            } else {
                                batch.put(keybuff, value);
                                listener.inserted(object.getId(), Integer.valueOf(value.length));
                            }

                        }
                        // Stopwatch sw = Stopwatch.createStarted();
                        db.write(wo, batch);
                        // System.err.printf("--- synced writes in %s\n", sw.stop());
                    }
                }
            }
            wo.sync();
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }
}
