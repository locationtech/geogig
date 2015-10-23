/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.mapdb;

import static com.google.common.base.Preconditions.checkState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectInserter;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2;
import org.locationtech.geogig.storage.fs.FileBlobStore;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;

/**
 * An Object database that uses a MapDB file database for persistence.
 * 
 * @see http://mapdb.org/
 */
public class MapdbObjectDatabase implements ObjectDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapdbObjectDatabase.class);

    private static final Function<byte[], ObjectId> TO_ID = new Function<byte[], ObjectId>() {
        @Override
        public ObjectId apply(byte[] rawId) {
            return ObjectId.createNoClone(rawId);
        }
    };

    private static final BTreeKeySerializer<byte[], byte[][]> KEY_SERIALIZER = BTreeKeySerializer.BYTE_ARRAY2;

    private static final Serializer<byte[]> VALUE_SERIALIZER = Serializer.BYTE_ARRAY;

    private final ConfigDatabase config;

    private DB db = null;

    // TODO could be changed to Map <ObjectId, byte[]> as ObjectId implements serializable
    // what about prefix finding then?
    private ConcurrentNavigableMap<byte[], byte[]> collection = null;

    private static final ObjectSerializingFactory serializers = DataStreamSerializationFactoryV2.INSTANCE;

    private final String collectionName = "objects";

    private MapdbConflictsDatabase conflicts;

    private FileBlobStore blobStore;

    private Platform platform;

    private final AtomicBoolean open = new AtomicBoolean();

    @Inject
    public MapdbObjectDatabase(ConfigDatabase config, Platform platform) {
        this.config = config;
        this.platform = platform;
    }

    /*
     * Things to know about MapDB
     * 
     * Transactions (write-ahead-log) can be disabled with DBMaker.transactionDisable(), this will
     * MapDB much faster. However, without WAL the store gets corrupted when not closed correctly.
     * 
     * Keys and values must be immutable. MapDB may serialize them on background thread, put them
     * into instance cache... Modifying an object after it was stored is a bad idea.
     * 
     * MapDB is much faster with memory mapped files. But those cause problems on 32bit JVMs and are
     * disabled by default. Use DBMaker.fileMmapEnableIfSupported() to enable them on 32bit systems.
     * 
     * 
     * There is instance cache which uses more memory, but makes MapDB faster. Use
     * DBMaker.cacheHashTableEnable()
     * 
     * MapDB does not run compaction on the background. You need to call DB.compact() from time to
     * time.
     * 
     * MapDB file storage can be only opened by one user at time. File lock should prevent file
     * being used multiple times. But if file lock fails to prevent it, the file will become
     * corrupted when opened (and written into) by multiple users.
     */

    @Override
    public synchronized void open() {
        if (isOpen()) {
            return;
        }
        Optional<URI> repoPath = new ResolveGeogigURI(platform, null).call();
        Preconditions.checkState(repoPath.isPresent(), "Not inside a geogig directory");
        URI uri = repoPath.get();
        Preconditions.checkState("file".equals(uri.getScheme()),
                "Repository URL is not file system based: %s", uri);
        File repoLocation = new File(uri);
        File storeDirectory = new File(repoLocation, "objects");

        if (!storeDirectory.exists() && !storeDirectory.mkdirs()) {
            throw new IllegalStateException("Unable to create Environment directory: '"
                    + storeDirectory.getAbsolutePath() + "'");
        }

        db = DBMaker.fileDB(new File(storeDirectory, "objectdb.mapdb"))//
                .asyncWriteEnable()//
                // .asyncWriteFlushDelay(10_000)//
                // .fileMmapEnableIfSupported()//
                // .fileMmapCleanerHackEnable()//
                // .cacheHashTableEnable()//
                // .cacheSize(??)
                // .closeOnJvmShutdown()//
                .make();

        collection = db.treeMap(collectionName, KEY_SERIALIZER, VALUE_SERIALIZER);

        conflicts = new MapdbConflictsDatabase(db);
        blobStore = new FileBlobStore(platform);
        this.open.set(true);
    }

    @Override
    public final boolean isOpen() {
        return open.get();
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.configure(config, "mapdb", "0.1");
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.verify(config, "mapdb", "0.1");
    }

    private AtomicInteger pendingCommits = new AtomicInteger();

    private void commit() {
        final int pendingCount = pendingCommits.getAndSet(0);
        if (pendingCount > 0) {
            Stopwatch sw = Stopwatch.createStarted();
            db.commit();
            sw.stop();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("commit (%,d objects): %s", pendingCount, sw));
            }
        }
    }

    @Override
    public synchronized void close() {
        final boolean wasOpen = open.getAndSet(false);
        if (!wasOpen) {
            return;
        }
        commit();
        db.close();
        db = null;
        collection = null;
        conflicts.close();
        conflicts = null;
        blobStore.close();
        blobStore = null;
    }

    @Override
    public ConflictsDatabase getConflictsDatabase() {
        return conflicts;
    }

    @Override
    public BlobStore getBlobStore() {
        return blobStore;
    }

    @Override
    public boolean exists(ObjectId id) {
        checkState(isOpen(), "db is closed");
        return collection.containsKey(id.getRawValue());
    }

    @Override
    public List<ObjectId> lookUp(final String partialId) {
        checkState(isOpen(), "db is closed");
        Preconditions.checkNotNull(partialId);
        Preconditions.checkArgument(partialId.length() > 7,
                "partial id must be at least 8 characters long: ", partialId);
        Preconditions.checkArgument(partialId.matches("[a-fA-F0-9]+"),
                "Prefix query must be done with hexadecimal values only");

        byte[] fromKey = ObjectId.toRaw(partialId);
        byte[] toKey = new byte[ObjectId.NUM_BYTES];
        {
            int prefixLen = fromKey.length;
            System.arraycopy(fromKey, 0, toKey, 0, prefixLen);
            Arrays.fill(toKey, prefixLen, ObjectId.NUM_BYTES - 1, (byte) 0xFF);
        }

        final Collection<ObjectId> baseResults;
        baseResults = Collections2.transform(collection.subMap(fromKey, toKey).keySet(), TO_ID);

        List<ObjectId> results = new ArrayList<ObjectId>();

        // If the length of the partial string is odd, then the last character wasn't considered in
        // the lookup, we need to filter the list further.
        if (partialId.length() % 2 != 0) {
            Iterator<ObjectId> listIterator = baseResults.iterator();
            while (listIterator.hasNext()) {
                ObjectId partialMatch = listIterator.next();
                if (partialMatch.toString().startsWith(partialId)) {
                    results.add(partialMatch);
                }
            }
        } else {
            results.addAll(baseResults);
        }

        return results;
    }

    @Override
    public RevObject get(ObjectId id) {
        checkState(isOpen(), "db is closed");
        RevObject result = getIfPresent(id);
        if (result != null) {
            return result;
        } else {
            throw new NoSuchElementException("No object with id: " + id);
        }
    }

    @Override
    public <T extends RevObject> T get(ObjectId id, Class<T> clazz) {
        return clazz.cast(get(id));
    }

    @Override
    public RevObject getIfPresent(ObjectId id) {
        checkState(isOpen(), "db is closed");
        byte[] key = id.getRawValue();
        byte[] rawValue = collection.get(key);
        return rawValue == null ? null : fromBytes(id, rawValue);
    }

    @Override
    public <T extends RevObject> T getIfPresent(ObjectId id, Class<T> clazz) {
        return clazz.cast(getIfPresent(id));
    }

    @Override
    public RevTree getTree(ObjectId id) {
        return get(id, RevTree.class);
    }

    @Override
    public RevFeature getFeature(ObjectId id) {
        return get(id, RevFeature.class);
    }

    @Override
    public RevFeatureType getFeatureType(ObjectId id) {
        return get(id, RevFeatureType.class);
    }

    @Override
    public RevCommit getCommit(ObjectId id) {
        return get(id, RevCommit.class);
    }

    @Override
    public RevTag getTag(ObjectId id) {
        return get(id, RevTag.class);
    }

    private long deleteChunk(List<ObjectId> ids) {
        long deleteCounter = 0;
        for (ObjectId id : ids) {
            byte[] key = id.getRawValue();
            boolean deleted = delete(key);
            if (deleted) {
                deleteCounter++;
            }
        }
        return deleteCounter;
    }

    @Override
    public boolean delete(ObjectId id) {
        checkState(isOpen(), "db is closed");
        byte[] key = id.getRawValue();
        boolean deleted;
        if ((deleted = delete(key))) {
            pendingCommits.incrementAndGet();
            commit();
        }
        return deleted;
    }

    private boolean delete(byte[] rawId) {
        byte[] value = collection.remove(rawId);
        boolean deleted = value != null;
        if (deleted) {
            pendingCommits.incrementAndGet();
        }
        return deleted;
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids) {
        return deleteAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
        checkState(isOpen(), "db is closed");
        Iterator<List<ObjectId>> chunks = Iterators.partition(ids, 500);
        long count = 0;
        while (chunks.hasNext()) {
            count += deleteChunk(chunks.next());
        }
        commit();
        return count;
    }

    @Override
    public boolean put(final RevObject object) {
        checkState(isOpen(), "db is closed");
        byte[] key = object.getId().getRawValue();
        byte[] record = toBytes(object);
        byte[] oldVal = collection.putIfAbsent(key, record);
        boolean existed = oldVal != null;
        if (!existed) {
            pendingCommits.incrementAndGet();
            commit();
        }
        return !existed;
    }

    @Override
    public void putAll(final Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener) {
        checkState(isOpen(), "db is closed");

        if (!objects.hasNext()) {
            return;
        }

        RevObject next;
        ObjectId id;
        byte[] value;
        byte[] key;

        while (isOpen() && objects.hasNext()) {
            next = objects.next();
            id = next.getId();
            key = id.getRawValue();
            value = toBytes(next);
            byte[] oldVal = collection.putIfAbsent(key, value);
            boolean existed = oldVal != null;
            if (existed) {
                listener.found(id, Integer.valueOf(oldVal.length));
            } else {
                pendingCommits.incrementAndGet();
                listener.inserted(id, Integer.valueOf(value.length));
            }
        }
        if (isOpen()) {
            commit();
        }
    }

    @Override
    public ObjectInserter newObjectInserter() {
        return new ObjectInserter(this);
    }

    @Override
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids) {
        return getAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, final BulkOpListener listener) {
        checkState(isOpen(), "db is closed");

        return new AbstractIterator<RevObject>() {
            final Iterator<ObjectId> queryIds = ids.iterator();

            @Override
            protected RevObject computeNext() {
                RevObject obj = null;
                while (obj == null) {
                    if (!queryIds.hasNext()) {
                        return endOfData();
                    }
                    ObjectId id = queryIds.next();
                    obj = getIfPresent(id);
                    if (obj == null) {
                        listener.notFound(id);
                    } else {
                        listener.found(obj.getId(), null);
                    }
                }
                return obj == null ? endOfData() : obj;
            }
        };
    }

    private RevObject fromBytes(ObjectId id, byte[] buffer) {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(buffer);
        RevObject result;
        try {
            result = serializers.read(id, new LZFInputStream(byteStream));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return result;
    }

    private byte[] toBytes(RevObject object) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        LZFOutputStream cOut = new LZFOutputStream(byteStream);
        try {
            serializers.write(object, cOut);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            cOut.close();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return byteStream.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("%s[db: %s, collection: %s]", getClass().getSimpleName(),
                db == null ? "<unset>" : db, collectionName);
    }
}
