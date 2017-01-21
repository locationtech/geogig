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
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.rocksdb.DBHandle.RocksDBReference;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2;
import org.locationtech.geogig.storage.datastream.LZFSerializationFactory;
import org.locationtech.geogig.storage.datastream.SerializationFactoryProxy;
import org.locationtech.geogig.storage.impl.AbstractObjectStore;
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;

public class RocksdbObjectStore extends AbstractObjectStore implements ObjectStore {

    private volatile boolean open;

    protected final String path;

    protected final boolean readOnly;

    protected DBHandle dbhandle;

    private ReadOptions bulkReadOptions;

    @Inject
    public RocksdbObjectStore(Platform platform, @Nullable Hints hints) {
        this(platform, hints, "objects.rocksdb");
    }

    public RocksdbObjectStore(Platform platform, @Nullable Hints hints, String databaseName) {
        Optional<URI> repoUriOpt = new ResolveGeogigURI(platform, hints).call();
        checkArgument(repoUriOpt.isPresent(), "couldn't resolve geogig directory");
        URI uri = repoUriOpt.get();
        checkArgument("file".equals(uri.getScheme()));
        this.path = new File(new File(uri), databaseName).getAbsolutePath();

        this.readOnly = hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
    }

    @Override
    public synchronized void open() {
        open(Collections.emptySet());
    }

    protected synchronized void open(Set<String> columnFamilyNames) {
        if (isOpen()) {
            return;
        }
        Map<String, String> defaultMetadata = ImmutableMap.of("version",
                RocksdbStorageProvider.VERSION, "serializer", "proxy");

        DBConfig address = new DBConfig(path, readOnly, defaultMetadata, columnFamilyNames);
        this.dbhandle = RocksConnectionManager.INSTANCE.acquire(address);

        this.bulkReadOptions = new ReadOptions();
        this.bulkReadOptions.setFillCache(false);
        this.bulkReadOptions.setVerifyChecksums(false);

        ObjectSerializingFactory defaultSerializer = new SerializationFactoryProxy();
        ObjectSerializingFactory serializer = defaultSerializer;
        final Optional<String> serializerValue = dbhandle.getMetadata("serializer");
        if (serializerValue.isPresent()) {
            String sval = serializerValue.get();
            Preconditions.checkState("proxy".equals(sval),
                    "serialization factory metadata error: expected 'proxy', got '%s'", sval);
        } else {
            // pre 1.0 serializer, for backwards compatibility with repos created before initial
            // release
            serializer = new LZFSerializationFactory(DataStreamSerializationFactoryV2.INSTANCE);
        }
        super.setSerializationFactory(serializer);
        open = true;
    }

    @Override
    public synchronized void close() {
        if (!open) {
            return;
        }
        open = false;

        final DBHandle dbhandle = this.dbhandle;
        this.dbhandle = null;
        this.bulkReadOptions.close();
        RocksConnectionManager.INSTANCE.release(dbhandle);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    protected void checkOpen() {
        Preconditions.checkState(isOpen(), "Database is closed");
    }

    protected void checkWritable() {
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
        exists = exists(bulkReadOptions, key);

        if (!exists) {
            try (RocksDBReference dbRef = dbhandle.getReference()) {
                dbRef.db().put(key, rawData);
            } catch (RocksDBException e) {
                throw Throwables.propagate(e);
            }
        }
        return !exists;
    }

    @Override
    protected InputStream getRawInternal(ObjectId id, boolean failIfNotFound)
            throws IllegalArgumentException {

        byte[] bytes = getRawInternal(id.getRawValue());

        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        if (failIfNotFound) {
            throw new IllegalArgumentException("object does not exist: " + id);
        }
        return null;
    }

    @Nullable
    private byte[] getRawInternal(byte[] key) throws IllegalArgumentException {
        checkOpen();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            return dbRef.db().get(key);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean exists(ObjectId id) {
        checkOpen();
        checkNotNull(id, "argument id is null");

        return exists(bulkReadOptions, id.getRawValue());
    }

    private static final byte[] NO_DATA = new byte[0];

    private boolean exists(ReadOptions readOptions, byte[] key) {
        int size = RocksDB.NOT_FOUND;
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            if (dbRef.db().keyMayExist(key, new StringBuffer())) {
                try {
                    size = dbRef.db().get(key, NO_DATA);
                } catch (RocksDBException e) {
                    throw Throwables.propagate(e);
                }
            }
        }
        return size != RocksDB.NOT_FOUND;
    }

    @Override
    public void delete(ObjectId objectId) {
        checkNotNull(objectId, "argument objectId is null");
        checkWritable();
        byte[] key = objectId.getRawValue();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            dbRef.db().remove(key);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, final BulkOpListener listener) {
        return getAll(ids, listener, RevObject.class);
    }

    @Override
    public <T extends RevObject> Iterator<T> getAll(final Iterable<ObjectId> ids,
            final BulkOpListener listener, final Class<T> type) {
        checkNotNull(ids, "ids is null");
        checkNotNull(listener, "listener is null");
        checkNotNull(type, "type is null");
        checkOpen();

        return new AbstractIterator<T>() {

            private Iterator<ObjectId> oids = ids.iterator();

            private byte[] keybuff = new byte[ObjectId.NUM_BYTES];

            private byte[] valueBuff = new byte[64 * 1024];

            private ReadOptions readOps = bulkReadOptions;

            @Override
            protected T computeNext() {
                checkOpen();
                try (RocksDBReference dbRef = dbhandle.getReference()) {
                    while (oids.hasNext()) {
                        ObjectId id = oids.next();
                        id.getRawValue(keybuff);
                        final int size = dbRef.db().get(readOps, keybuff, valueBuff);
                        if (RocksDB.NOT_FOUND == size) {
                            listener.notFound(id);
                            continue;
                        }
                        if (size > valueBuff.length) {
                            valueBuff = dbRef.db().get(readOps, keybuff);
                        }
                        RevObject object = serializer().read(id,
                                new ByteArrayInputStream(valueBuff));
                        if (type.isInstance(object)) {
                            listener.found(id, Integer.valueOf(size));
                            return type.cast(object);
                        } else {
                            listener.notFound(id);
                        }
                    }
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
                return endOfData();
            }
        };
    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
        checkNotNull(ids, "argument objectId is null");
        checkNotNull(listener, "argument listener is null");
        checkWritable();

        final boolean checkExists = !BulkOpListener.NOOP_LISTENER.equals(listener);

        byte[] keybuff = new byte[ObjectId.NUM_BYTES];

        try (RocksDBReference dbRef = dbhandle.getReference(); ReadOptions ro = new ReadOptions()) {
            ro.setFillCache(false);
            ro.setVerifyChecksums(false);
            try (WriteOptions writeOps = new WriteOptions()) {
                writeOps.setSync(false);
                while (ids.hasNext()) {
                    ObjectId id = ids.next();
                    id.getRawValue(keybuff);
                    if (!checkExists || exists(ro, keybuff)) {
                        try {
                            dbRef.db().remove(writeOps, keybuff);
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
        checkOpen();
        List<ObjectId> matches = new ArrayList<>(2);
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            try (RocksIterator it = dbRef.db().newIterator()) {
                it.seek(idprefix);
                while (it.isValid()) {
                    byte[] key = it.key();
                    for (int i = 0; i < idprefix.length; i++) {
                        if (idprefix[i] != key[i]) {
                            return matches;
                        }
                    }
                    ObjectId id = ObjectId.createNoClone(key);
                    matches.add(id);
                    it.next();
                }
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

        Map<ObjectId, Integer> insertedIds = new HashMap<ObjectId, Integer>();
        try (RocksDBReference dbRef = dbhandle.getReference();
                WriteOptions wo = new WriteOptions()) {
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
                                insertedIds.put(object.getId(), Integer.valueOf(value.length));
                            }

                        }
                        // Stopwatch sw = Stopwatch.createStarted();
                        dbRef.db().write(wo, batch);
                        for (Entry<ObjectId, Integer> entry : insertedIds.entrySet()) {
                            listener.inserted(entry.getKey(), entry.getValue());
                        }
                        insertedIds.clear();
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
