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
import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterators.spliteratorUnknownSize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.rocksdb.DBHandle.RocksDBReference;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV2;
import org.locationtech.geogig.storage.datastream.RevObjectSerializerLZF;
import org.locationtech.geogig.storage.datastream.RevObjectSerializerProxy;
import org.locationtech.geogig.storage.impl.AbstractObjectStore;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;

public class RocksdbObjectStore extends AbstractObjectStore implements ObjectStore {

    private static final Logger LOG = LoggerFactory.getLogger(RocksdbObjectStore.class);

    private volatile boolean open;

    protected final String path;

    protected final boolean readOnly;

    protected DBHandle dbhandle;

    private ReadOptions bulkReadOptions;

    protected final Platform platform;

    protected final @Nullable Hints hints;

    @Inject
    public RocksdbObjectStore(Platform platform, @Nullable Hints hints) {
        this(platform, hints, "objects.rocksdb");
    }

    public RocksdbObjectStore(Platform platform, @Nullable Hints hints, String databaseName) {
        checkNotNull(platform);
        this.platform = platform;
        this.hints = hints;
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

        RevObjectSerializer defaultSerializer = new RevObjectSerializerProxy();
        RevObjectSerializer serializer = defaultSerializer;
        final Optional<String> serializerValue = dbhandle.getMetadata("serializer");
        if (serializerValue.isPresent()) {
            String sval = serializerValue.get();
            Preconditions.checkState("proxy".equals(sval),
                    "serialization factory metadata error: expected 'proxy', got '%s'", sval);
        } else {
            // pre 1.0 serializer, for backwards compatibility with repos created before initial
            // release
            serializer = new RevObjectSerializerLZF(DataStreamRevObjectSerializerV2.INSTANCE);
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
        boolean exists;
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            byte[] key = id.getRawValue();
            exists = exists(dbRef, bulkReadOptions, key);
            if (!exists) {
                dbRef.db().put(key, rawData);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
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
            throw new IllegalArgumentException("Object does not exist: " + id);
        }
        return null;
    }

    @Nullable
    private byte[] getRawInternal(byte[] key) throws IllegalArgumentException {
        checkOpen();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            return dbRef.db().get(key);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean exists(ObjectId id) {
        checkOpen();
        checkNotNull(id, "argument id is null");

        try (RocksDBReference dbRef = dbhandle.getReference()) {
            return exists(dbRef, bulkReadOptions, id.getRawValue());
        }
    }

    private static final byte[] NO_DATA = new byte[0];

    private boolean exists(RocksDBReference dbRef, ReadOptions readOptions, byte[] key) {
        int size = RocksDB.NOT_FOUND;
        if (dbRef.db().keyMayExist(key, new StringBuilder(0))) {
            try {
                size = dbRef.db().get(key, NO_DATA);
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
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
            dbRef.db().delete(key);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
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
                        RevObject object;
                        object = serializer().read(id, valueBuff, 0, size);
                        if (type.isInstance(object)) {
                            listener.found(id, Integer.valueOf(size));
                            return type.cast(object);
                        } else {
                            listener.notFound(id);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
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
            try (WriteOptions writeOps = new WriteOptions(); //
                    WriteBatch batch = new WriteBatch()) {
                writeOps.setSync(true);
                while (ids.hasNext()) {
                    ObjectId id = ids.next();
                    id.getRawValue(keybuff);
                    if (!checkExists || exists(dbRef, ro, keybuff)) {
                        batch.delete(keybuff);
                        listener.deleted(id);
                    } else {
                        listener.notFound(id);
                    }
                }
                dbRef.db().write(writeOps, batch);
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
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
                    ObjectId id = ObjectId.create(key);
                    matches.add(id);
                    it.next();
                }
            }
        }
        return matches;
    }

    protected static class EncodedObject {
        final ObjectId id;

        final TYPE type;

        final byte[] serialform;

        public EncodedObject(ObjectId id, TYPE type, byte[] serialform) {
            this.id = id;
            this.type = type;
            this.serialform = serialform;
        }
    }

    /**
     * Creates a parallel stream out of {@code objects}, filtering out before encoding through
     * {@link #exists} if {@code checkExists == true}
     */
    protected Stream<RevObject> toStream(Iterator<? extends RevObject> objects, boolean checkExists,
            BulkOpListener listener) {

        final int characteristics = IMMUTABLE | NONNULL | DISTINCT;
        Stream<RevObject> stream;
        stream = StreamSupport.stream(spliteratorUnknownSize(objects, characteristics), true);
        if (checkExists) {
            stream = stream.filter((o) -> {
                if (exists(o.getId())) {
                    listener.found(o.getId(), null);
                    return false;
                }
                return true;
            });
        }
        return stream;
    }

    private EncodedObject encode(RevObject o) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            serializer().write(o, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new EncodedObject(o.getId(), o.getType(), out.toByteArray());
    }

    @Override
    public final void putAll(Iterator<? extends RevObject> objects, final BulkOpListener listener) {
        checkNotNull(objects, "objects is null");
        checkNotNull(listener, "listener is null");
        checkWritable();

        final boolean checkExists = !BulkOpListener.NOOP_LISTENER.equals(listener);
        Stream<RevObject> stream = toStream(objects, checkExists, listener);
        putAll(stream, listener);
    }

    protected void putAll(Stream<RevObject> stream, BulkOpListener listener) {
        final Stopwatch sw = LOG.isTraceEnabled() ? Stopwatch.createStarted() : null;

        final int batchsize = 1000;
        // encodes on several threads
        Stream<EncodedObject> encoded = stream.parallel().map(o -> encode(o));

        int insertCount = 0;
        final Iterator<EncodedObject> iterator = encoded.iterator();
        while (iterator.hasNext()) {
            Iterator<EncodedObject> batch = Iterators.limit(iterator, batchsize);
            insertCount += insertBatch(batch, listener);
        }
        if (LOG.isTraceEnabled())

        {
            LOG.trace(String.format("Inserted %,d objects in %s", insertCount, sw.stop()));
        }
    }

    private int insertBatch(Iterator<EncodedObject> objects, BulkOpListener listener) {

        final byte[] keybuff = new byte[ObjectId.NUM_BYTES];

        Set<ObjectId> insertedIds = new HashSet<>();

        try (RocksDBReference dbRef = dbhandle.getReference();
                WriteOptions wo = new WriteOptions(); //
                WriteBatch batch = new WriteBatch()) {
            wo.setSync(true);
            while (objects.hasNext()) {
                EncodedObject object = objects.next();
                final ObjectId id = object.id;
                id.getRawValue(keybuff);
                final byte[] value = object.serialform;
                batch.put(keybuff, value);
                insertedIds.add(id);
            }

            dbRef.db().write(wo, batch);
            // need to notify listener once the objects are actually on the db
            insertedIds.forEach((id) -> listener.inserted(id, null));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return insertedIds.size();
    }

    @Override
    public <T extends RevObject> AutoCloseableIterator<ObjectInfo<T>> getObjects(
            Iterator<NodeRef> refs, BulkOpListener listener, Class<T> type) {

        checkNotNull(refs, "refs is null");
        checkNotNull(listener, "listener is null");
        checkNotNull(type, "type is null");
        checkOpen();

        return new AutoCloseableIterator<ObjectInfo<T>>() {

            private Iterator<NodeRef> noderefs = refs;

            private byte[] keybuff = new byte[ObjectId.NUM_BYTES];

            private byte[] valueBuff = new byte[64 * 1024];

            private ReadOptions readOps = bulkReadOptions;

            private boolean closed;

            private ObjectInfo<T> next;

            @Override
            public void close() {
                closed = true;
                noderefs = null;
                valueBuff = null;
            }

            @Override
            public boolean hasNext() {
                if (closed) {
                    return false;
                }
                if (next == null) {
                    next = computeNext();
                }
                return next != null;
            }

            @Override
            public ObjectInfo<T> next() {
                if (closed) {
                    throw new NoSuchElementException("Iterator is closed");
                }
                final ObjectInfo<T> curr;
                if (next == null) {
                    curr = computeNext();
                } else {
                    curr = next;
                    next = null;
                }
                if (curr == null) {
                    throw new NoSuchElementException();
                }
                return curr;
            }

            private @Nullable ObjectInfo<T> computeNext() {
                checkOpen();
                try (RocksDBReference dbRef = dbhandle.getReference()) {
                    while (noderefs.hasNext()) {
                        final NodeRef ref = noderefs.next();
                        final ObjectId id = ref.getObjectId();
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
                            return ObjectInfo.of(ref, type.cast(object));
                        } else {
                            listener.notFound(id);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
        };
    }
}
