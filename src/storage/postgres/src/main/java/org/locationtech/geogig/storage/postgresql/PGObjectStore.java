/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.propagate;
import static java.lang.String.format;
import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static org.locationtech.geogig.storage.postgresql.Environment.KEY_GETALL_BATCH_SIZE;
import static org.locationtech.geogig.storage.postgresql.Environment.KEY_PUTALL_BATCH_SIZE;
import static org.locationtech.geogig.storage.postgresql.Environment.KEY_THREADPOOL_SIZE;
import static org.locationtech.geogig.storage.postgresql.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.PGStorage.rollbackAndRethrow;

import java.net.URISyntaxException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.cache.CacheManager;
import org.locationtech.geogig.storage.cache.ObjectCache;
import org.locationtech.geogig.storage.datastream.SerializationFactoryProxy;
import org.locationtech.geogig.storage.impl.ConnectionManager;
import org.locationtech.geogig.storage.postgresql.Environment.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;

/**
 * PostgreSQL implementation for {@link ObjectStore}.
 * <p>
 * TODO: document/force use of {@code SET constraint_exclusion=ON}
 */
public class PGObjectStore implements ObjectStore {

    static final Logger LOG = LoggerFactory.getLogger(PGObjectStore.class);

    private static final int DEFAULT_PUT_ALL_PARTITION_SIZE = 10_000;

    private static final int DEFAULT_GET_ALL_PARTITION_SIZE = 100;

    private static final ObjectStoreSharedResources SHARED_RESOURCES = new ObjectStoreSharedResources();

    protected final Environment config;

    protected final ConfigDatabase configdb;

    private static final SerializationFactoryProxy encoder = new SerializationFactoryProxy();

    protected DataSource dataSource;

    private ObjectCache sharedCache = null;

    private int getAllBatchSize = DEFAULT_GET_ALL_PARTITION_SIZE;

    private int putAllBatchSize = DEFAULT_PUT_ALL_PARTITION_SIZE;

    private SharedResourceReference resources;

    @Inject
    public PGObjectStore(final ConfigDatabase configdb, final Hints hints)
            throws URISyntaxException {
        this(configdb, Environment.get(hints));
    }

    public PGObjectStore(final ConfigDatabase configdb, final Environment config) {
        Preconditions.checkNotNull(configdb);
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(config.getRepositoryName(), "Repository id not provided");
        // REVISIT: the following check should be done at open() instead?
        Preconditions.checkArgument(PGStorage.repoExists(config), "Repository %s does not exist",
                config.getRepositoryName());
        this.configdb = configdb;
        this.config = config;
    }

    @Override
    public void open() {
        if (dataSource != null) {
            return;
        }
        dataSource = PGStorage.newDataSource(config);

        Optional<Integer> getAllFetchSize = configdb.get(KEY_GETALL_BATCH_SIZE, Integer.class);
        Optional<Integer> putAllBatchSize = configdb.get(KEY_PUTALL_BATCH_SIZE, Integer.class);
        Optional<Integer> tpoolSize = configdb.getGlobal(KEY_THREADPOOL_SIZE, Integer.class);
        if (getAllFetchSize.isPresent()) {
            Integer fetchSize = getAllFetchSize.get();
            Preconditions.checkState(fetchSize.intValue() > 0,
                    "postgres.getAllBatchSize must be a positive integer: %s. Check your config.",
                    fetchSize);
            this.getAllBatchSize = fetchSize;
        }
        if (putAllBatchSize.isPresent()) {
            Integer batchSize = putAllBatchSize.get();
            Preconditions.checkState(batchSize.intValue() > 0,
                    "postgres.putAllBatchSize must be a positive integer: %s. Check your config.",
                    batchSize);
            this.putAllBatchSize = batchSize;
        }
        int threadPoolSize;
        if (tpoolSize.isPresent()) {
            Integer poolSize = tpoolSize.get();
            Preconditions.checkState(poolSize.intValue() > 0,
                    "postgres.threadPoolSize must be a positive integer: %s. Check your config.",
                    poolSize);
            threadPoolSize = poolSize;
        } else {
            threadPoolSize = Math.max(Runtime.getRuntime().availableProcessors(), 2);
        }

        final ConnectionConfig connectionConfig = config.connectionConfig;
        this.resources = SHARED_RESOURCES.acquire(connectionConfig);
        resources.trySetThreadPoolSize(threadPoolSize);

        this.sharedCache = CacheManager.INSTANCE.acquire(getCacheIdentifier(connectionConfig));
    }

    /**
     * The cache identifier to give to {@link CacheManager#acquire(String)}, defaults to
     * {@code connectionConfig.toURI().toString()}, subclasses should override to reflect which
     * object store they operate upon (e.g. {@code connectionConfig.toURI().toString() + "index"} or
     * {@code + "objects"}
     */
    protected String getCacheIdentifier(ConnectionConfig connectionConfig) {
        final String cacheIdentifier = connectionConfig.toURI().toString();
        return cacheIdentifier;
    }

    @Override
    public boolean isOpen() {
        return dataSource != null;
    }

    @Override
    public void close() {
        if (this.dataSource == null) {
            return;
        }
        DataSource ds = this.dataSource;
        SharedResourceReference res = this.resources;
        ObjectCache sharedCache = this.sharedCache;
        this.dataSource = null;
        this.resources = null;
        this.sharedCache = null;
        try {
            PGStorage.closeDataSource(ds);
        } finally {
            if (res != null) {
                SHARED_RESOURCES.release(res);
            }
            CacheManager.INSTANCE.release(sharedCache);
        }
    }

    @VisibleForTesting
    public ObjectCache getCache() {
        return sharedCache;
    }

    @VisibleForTesting
    void setPutAllBatchSize(int size) {
        this.putAllBatchSize = size;
    }

    protected String objectsTable() {
        return config.getTables().objects();
    }

    @Override
    public boolean exists(final ObjectId id) {
        checkNotNull(id, "argument id is null");
        checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();
        if (sharedCache.contains(id)) {
            return true;
        }
        final String sql = format(
                "SELECT TRUE WHERE EXISTS ( SELECT 1 FROM %s WHERE ((id).h1) = ? AND id = CAST(ROW(?,?,?) AS OBJECTID) )",
                objectsTable());
        final PGId pgid = PGId.valueOf(id);
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, id))) {
                ps.setInt(1, pgid.hash1());
                pgid.setArgs(ps, 2);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean exists = rs.next();
                    return exists;
                }
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    @Override
    public List<ObjectId> lookUp(final String partialId) {
        checkNotNull(partialId, "argument partialId is null");
        checkArgument(partialId.length() > 7, "partial id must be at least 8 characters long: ",
                partialId);
        checkState(isOpen(), "db is closed");
        config.checkRepositoryExists();

        final int hash1 = PGId.intHash(ObjectId.toRaw(partialId));
        final String sql = format(
                "SELECT ((id).h2), ((id).h3) FROM %s WHERE ((id).h1) = ? LIMIT 1000",
                objectsTable());

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setInt(1, hash1);

                List<ObjectId> matchList = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ObjectId id = PGId.valueOf(hash1, rs.getLong(1), rs.getLong(2))
                                .toObjectId();
                        final String idStr = id.toString();
                        if (idStr.startsWith(partialId)) {
                            matchList.add(id);
                        }
                    }
                }
                return matchList;
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    @Override
    public RevObject get(ObjectId id) throws IllegalArgumentException {
        checkNotNull(id, "argument id is null");
        checkState(isOpen(), "db is closed");

        config.checkRepositoryExists();
        RevObject obj = getIfPresent(id);
        if (obj == null) {
            throw new IllegalArgumentException("Object does not exist: " + id);
        }

        return obj;
    }

    @Override
    public <T extends RevObject> T get(ObjectId id, Class<T> type) throws IllegalArgumentException {
        checkNotNull(id, "argument id is null");
        checkNotNull(type, "argument class is null");
        checkState(isOpen(), "db is closed");

        config.checkRepositoryExists();
        RevObject obj = getIfPresent(id, type);
        if (obj == null) {
            throw new IllegalArgumentException("Object does not exist: " + id);
        }
        return type.cast(obj);
    }

    @Override
    public RevObject getIfPresent(ObjectId id) {
        checkNotNull(id, "argument id is null");
        checkState(isOpen(), "db is closed");
        config.checkRepositoryExists();
        return getIfPresent(id, RevObject.class);
    }

    @Override
    public <T extends RevObject> T getIfPresent(final ObjectId id, final Class<T> type)
            throws IllegalArgumentException {

        checkNotNull(id, "argument id is null");
        checkNotNull(type, "argument class is null");
        checkState(isOpen(), "db is closed");
        config.checkRepositoryExists();

        final RevObject obj;
        if (RevTree.EMPTY_TREE_ID.equals(id)) {
            return type.isAssignableFrom(RevTree.class) ? type.cast(RevTree.EMPTY) : null;
        }

        @Nullable
        final TYPE objectType;
        if (RevObject.class.equals(type)) {
            objectType = null;
        } else {
            objectType = RevObject.TYPE.valueOf(type);
        }

        obj = getIfPresent(id, objectType, dataSource);
        if (obj == null) {
            return null;
        }

        if (!type.isAssignableFrom(obj.getClass())) {
            return null;
        }
        return type.cast(obj);
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

    @Override
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids) {
        return getAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, final BulkOpListener listener) {
        return getAll(ids, listener, RevObject.class);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public <T extends RevObject> Iterator<T> getAll(Iterable<ObjectId> ids, BulkOpListener listener,
            Class<T> type) {

        checkNotNull(ids, "ids is null");
        checkNotNull(listener, "listener is null");
        checkNotNull(type, "type is null");
        checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();

        Iterator<T> stream = new GetAllIterator(ids.iterator(), type, listener, this);

        return stream;
    }

    @Override
    public <T extends RevObject> AutoCloseableIterator<ObjectInfo<T>> getObjects(
            Iterator<NodeRef> refs, BulkOpListener listener, Class<T> type) {
        checkNotNull(refs, "refs is null");
        checkNotNull(listener, "listener is null");
        checkNotNull(type, "type is null");
        checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();

        AutoCloseableIterator<ObjectInfo<T>> stream = new ObjectIterator<T>(refs, type, listener,
                this);

        return stream;
    }

    private static class ObjectIterator<T extends RevObject>
            implements AutoCloseableIterator<ObjectInfo<T>> {

        private PeekingIterator<NodeRef> nodes;

        private final Class<T> type;

        private final BulkOpListener listener;

        private final PGObjectStore store;

        private Iterator<ObjectInfo<T>> nextBatch;

        final ObjectCache cache;

        private boolean closed;

        private ObjectInfo<T> next;

        public ObjectIterator(Iterator<NodeRef> refs, Class<T> type, BulkOpListener listener,
                PGObjectStore store) {
            this.nodes = Iterators.peekingIterator(refs);
            this.type = type;
            this.listener = listener;
            this.store = store;
            cache = store.sharedCache;
        }

        @Override
        public void close() {
            closed = true;
            nodes = null;
            nextBatch = null;
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
            if (nextBatch != null && nextBatch.hasNext()) {
                return nextBatch.next();
            }
            if (!nodes.hasNext()) {
                return null;
            }
            {
                ObjectInfo<T> obj = tryNextCached();
                if (obj != null) {
                    return obj;
                }
            }

            final int queryBatchSize = store.getAllBatchSize;
            final int superPartitionBatchSize = 10 * queryBatchSize;

            List<ObjectInfo<T>> hits = new LinkedList<>();
            List<NodeRef> cacheMisses = new ArrayList<>(superPartitionBatchSize);
            for (int i = 0; i < superPartitionBatchSize && nodes.hasNext(); i++) {
                NodeRef node = nodes.next();
                ObjectId id = node.getObjectId();
                RevObject cached = cache.getIfPresent(id);
                if (cached == null) {
                    cacheMisses.add(node);
                } else {
                    T obj = cacheHit(id, cached);
                    if (obj != null) {
                        hits.add(new ObjectInfo<T>(node, obj));
                    }
                }
            }
            List<List<NodeRef>> partitions = Lists.partition(cacheMisses, queryBatchSize);
            List<Future<List<ObjectInfo<T>>>> futures = new ArrayList<>(partitions.size());
            for (List<NodeRef> partition : partitions) {
                Future<List<ObjectInfo<T>>> dbBatch;
                dbBatch = store.getObjects(partition, listener, type);
                futures.add(dbBatch);
            }

            final Function<Future<List<ObjectInfo<T>>>, List<ObjectInfo<T>>> futureGetter = (
                    objs) -> {
                try {
                    return objs.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    throw propagate(e);
                }
            };

            Iterable<List<ObjectInfo<T>>> lists = Iterables.transform(futures, futureGetter);
            Iterable<ObjectInfo<T>> concat = Iterables.concat(lists);
            Iterator<ObjectInfo<T>> iterator = concat.iterator();

            nextBatch = Iterators.concat(hits.iterator(), iterator);
            return computeNext();
        }

        private ObjectInfo<T> tryNextCached() {
            while (nodes.hasNext()) {
                NodeRef node = nodes.peek();
                ObjectId id = node.getObjectId();
                RevObject cached = cache.getIfPresent(id);
                if (cached == null) {
                    return null;
                } else {
                    nodes.next();
                    T obj = cacheHit(id, cached);
                    if (obj != null) {
                        return new ObjectInfo<T>(node, obj);
                    }
                }
            }
            return null;
        }

        @Nullable
        private T cacheHit(ObjectId id, RevObject object) {
            if (type.isInstance(object)) {
                listener.found(id, null);
                return type.cast(object);
            } else {
                listener.notFound(id);
            }
            return null;
        }
    }

    private static class GetAllIterator<T extends RevObject> extends AbstractIterator<T> {

        private final PeekingIterator<ObjectId> ids;

        private final Class<T> type;

        private final BulkOpListener listener;

        private final PGObjectStore store;

        private Iterator<T> nextBatch;

        final ObjectCache cache;

        public GetAllIterator(Iterator<ObjectId> ids, Class<T> type, BulkOpListener listener,
                PGObjectStore store) {
            this.ids = Iterators.peekingIterator(ids);
            this.type = type;
            this.listener = listener;
            this.store = store;
            cache = store.sharedCache;
        }

        @Override
        protected T computeNext() {
            if (nextBatch != null && nextBatch.hasNext()) {
                return nextBatch.next();
            }
            if (!ids.hasNext()) {
                return endOfData();
            }
            {
                T obj = tryNextCached();
                if (obj != null) {
                    return obj;
                }
            }

            final int queryBatchSize = store.getAllBatchSize;
            final int superPartitionBatchSize = 10 * queryBatchSize;

            List<T> hits = new LinkedList<>();
            List<ObjectId> cacheMisses = new ArrayList<>(superPartitionBatchSize);
            for (int i = 0; i < superPartitionBatchSize && ids.hasNext(); i++) {
                ObjectId id = ids.next();
                RevObject cached = cache.getIfPresent(id);
                if (cached == null) {
                    cacheMisses.add(id);
                } else {
                    T obj = cacheHit(id, cached);
                    if (obj != null) {
                        hits.add(obj);
                    }
                }
            }
            List<List<ObjectId>> partitions = Lists.partition(cacheMisses, queryBatchSize);
            List<Future<List<T>>> futures = new ArrayList<>(partitions.size());
            for (List<ObjectId> partition : partitions) {
                Future<List<T>> dbBatch;
                dbBatch = store.getAll(partition, listener, type);
                futures.add(dbBatch);
            }

            final Function<Future<List<T>>, List<T>> futureGetter = (objs) -> {
                try {
                    return objs.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    throw propagate(e);
                }
            };

            Iterable<List<T>> lists = Iterables.transform(futures, futureGetter);
            Iterable<T> concat = Iterables.concat(lists);
            Iterator<T> iterator = concat.iterator();

            nextBatch = Iterators.concat(hits.iterator(), iterator);
            return computeNext();
        }

        private T tryNextCached() {
            while (ids.hasNext()) {
                ObjectId id = ids.peek();
                RevObject cached = cache.getIfPresent(id);
                if (cached == null) {
                    return null;
                } else {
                    ids.next();
                    T obj = cacheHit(id, cached);
                    if (obj != null) {
                        return obj;
                    }
                }
            }
            return null;
        }

        @Nullable
        private T cacheHit(ObjectId id, RevObject object) {
            if (type.isInstance(object)) {
                listener.found(id, null);
                return type.cast(object);
            } else {
                listener.notFound(id);
            }
            return null;
        }

    }

    @Override
    public boolean put(final RevObject object) {
        checkNotNull(object, "argument object is null");
        checkArgument(!object.getId().isNull(), "ObjectId is NULL %s", object);
        checkWritable();
        config.checkRepositoryExists();

        final ObjectId id = object.getId();
        final PGId pgid = PGId.valueOf(id);
        final String tableName = tableNameForType(object.getType(), pgid);
        final String sql = format("INSERT INTO %s (id, object) VALUES (ROW(?,?,?),?)", tableName);

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(true);
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, id, object))) {
                pgid.setArgs(ps, 1);
                byte[] blob = encoder.encode(object);
                ps.setBytes(4, blob);

                final int updateCount = ps.executeUpdate();
                boolean inserted = updateCount == 1;
                return inserted;
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public void delete(ObjectId objectId) {
        checkNotNull(objectId, "argument objectId is null");
        checkWritable();
        config.checkRepositoryExists();
        delete(objectId, dataSource);
    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids) {
        deleteAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    protected String tableNameForType(@Nullable RevObject.TYPE type, PGId pgid) {
        final String tableName;
        final TableNames tables = config.getTables();
        if (type == null) {
            tableName = objectsTable();
        } else {
            switch (type) {
            case COMMIT:
                tableName = tables.commits();
                break;
            case FEATURE:
                tableName = pgid != null ? tables.features(pgid.hash1()) : tables.features();
                break;
            case FEATURETYPE:
                tableName = tables.featureTypes();
                break;
            case TAG:
                tableName = tables.tags();
                break;
            case TREE:
                tableName = tables.trees();
                break;
            default:
                throw new IllegalArgumentException();
            }
        }
        return tableName;
    }

    /**
     * Retrieves the object with the specified id.
     * <p>
     * Must return <code>null</code> if no such object exists.
     * </p>
     */
    @Nullable
    private RevObject getIfPresent(final ObjectId id, final @Nullable RevObject.TYPE type,
            DataSource ds) {
        RevObject cached = sharedCache.getIfPresent(id);
        if (cached != null) {
            return cached;
        }

        final PGId pgid = PGId.valueOf(id);
        final String tableName = tableNameForType(type, pgid);

        if (tableName == null) {
            return null;
        }

        // NOTE: the AND clause is for the ((id).h1) = ? comparison to use the hash index
        // and enable constraint exclusion
        final String sql = format(
                "SELECT encode(object,'base64') FROM %s WHERE ((id).h1) = ? AND id = CAST(ROW(?,?,?) AS OBJECTID)",
                tableName);

        byte[] bytes = null;

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, id))) {
                ps.setInt(1, pgid.hash1());
                pgid.setArgs(ps, 2);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        byte[] bytesBase64 = rs.getBytes(1);
                        bytes = Base64.getMimeDecoder().decode(bytesBase64);
                    }
                }
            }
        } catch (SQLException e) {
            throw propagate(e);
        }

        if (bytes == null) {
            return null;
        }

        RevObject obj = encoder.read(id, bytes, 0, bytes.length);
        sharedCache.put(obj);
        return obj;
    }

    private <T extends RevObject> Future<List<T>> getAll(final Collection<ObjectId> ids,
            final BulkOpListener listener, final Class<T> type) {
        checkState(isOpen(), "Database is closed");

        GetAllOp<T> getAllOp = new GetAllOp<T>(ids, listener, this, type);
        // Avoid deadlocking by running the task synchronously if we are already in one of the
        // threads on the executor.
        if (Thread.currentThread().getThreadGroup().equals(resources.threadGroup)) {
            try {
                List<T> objects = getAllOp.call();
                return Futures.immediateFuture(objects);
            } catch (Exception e) {
                propagate(e);
            }
        }
        Future<List<T>> future = resources.executor().submit(getAllOp);
        return future;
    }

    private <T extends RevObject> Future<List<ObjectInfo<T>>> getObjects(
            final Collection<NodeRef> nodes, final BulkOpListener listener, final Class<T> type) {
        checkState(isOpen(), "Database is closed");

        GetObjectOp<T> getAllOp = new GetObjectOp<T>(nodes, listener, this, type);
        // Avoid deadlocking by running the task synchronously if we are already in one of the
        // threads on the executor.
        if (Thread.currentThread().getThreadGroup().equals(resources.threadGroup)) {
            try {
                List<ObjectInfo<T>> objects = getAllOp.call();
                return Futures.immediateFuture(objects);
            } catch (Exception e) {
                propagate(e);
            }
        }
        Future<List<ObjectInfo<T>>> future = resources.executor().submit(getAllOp);
        return future;
    }

    private static class GetAllOp<T extends RevObject> implements Callable<List<T>> {

        private final Set<ObjectId> queryIds;

        private final BulkOpListener callback;

        private final PGObjectStore db;

        private final ObjectCache sharedCache;

        private final Class<T> type;

        private final boolean notify;

        public GetAllOp(Collection<ObjectId> ids, BulkOpListener listener, PGObjectStore db,
                Class<T> type) {
            this.queryIds = Sets.newHashSet(ids);
            this.callback = listener;
            this.notify = !BulkOpListener.NOOP_LISTENER.equals(listener);
            this.db = db;
            this.type = type;
            this.sharedCache = db.sharedCache;
        }

        @Override
        public List<T> call() throws Exception {
            checkState(db.isOpen(), "Database is closed");
            final TYPE objType = RevObject.class.equals(type) ? null : RevObject.TYPE.valueOf(type);
            final String tableName = db.tableNameForType(objType, null);

            final int queryCount = queryIds.size();
            List<T> found = new ArrayList<>(queryCount);

            if (tableName != null) {
                byte[] bytes;
                ObjectId id;

                final String sql = format(
                        "SELECT ((id).h1), ((id).h2),((id).h3), encode(object,'base64') FROM %s WHERE ((id).h1) = ANY(?)",
                        tableName);

                try (Connection cx = PGStorage.newConnection(db.dataSource)) {
                    try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, queryIds))) {
                        Base64.Decoder base64Decoder = Base64.getMimeDecoder();
                        final Array array = toJDBCArray(cx, queryIds);
                        ps.setFetchSize(queryCount);
                        ps.setArray(1, array);

                        final Stopwatch sw = LOG.isTraceEnabled() ? Stopwatch.createStarted()
                                : null;

                        try (ResultSet rs = ps.executeQuery()) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace(String.format("Executed getAll for %,d ids in %,dms",
                                        queryCount, sw.elapsed(TimeUnit.MILLISECONDS)));
                            }
                            while (rs.next()) {
                                id = PGId.valueOf(rs, 1).toObjectId();
                                // only add those that are in the query set. The resultset may
                                // contain
                                // more due to hash1 clashes
                                if (queryIds.contains(id)) {
                                    byte[] bytesBase64 = rs.getBytes(4);
                                    bytes = base64Decoder.decode(bytesBase64);

                                    RevObject obj = encoder.decode(id, bytes);
                                    if (objType == null || objType.equals(obj.getType())) {
                                        if (notify) {
                                            queryIds.remove(id);
                                            callback.found(id, Integer.valueOf(bytes.length));
                                        }
                                        found.add(type.cast(obj));
                                        sharedCache.put(obj);
                                    }
                                }
                            }
                        }
                        if (LOG.isTraceEnabled()) {
                            sw.stop();
                            LOG.trace(String.format(
                                    "Finished getAll for %,d out of %,d ids in %,dms", found.size(),
                                    queryCount, sw.elapsed(TimeUnit.MILLISECONDS)));
                        }
                    }
                }
            }
            if (notify) {
                for (ObjectId oid : queryIds) {
                    callback.notFound(oid);
                }
            }
            return found;
        }

        private Array toJDBCArray(Connection cx, final Collection<ObjectId> queryIds)
                throws SQLException {
            Array array;
            Object[] arr = new Object[queryIds.size()];
            Iterator<ObjectId> it = queryIds.iterator();
            for (int i = 0; it.hasNext(); i++) {
                ObjectId id = it.next();
                arr[i] = Integer.valueOf(PGId.valueOf(id).hash1());
            }
            array = cx.createArrayOf("integer", arr);
            return array;
        }
    }

    private static class GetObjectOp<T extends RevObject> implements Callable<List<ObjectInfo<T>>> {

        private final Set<NodeRef> queryNodes;

        private final BulkOpListener callback;

        private final PGObjectStore db;

        private final ObjectCache sharedCache;

        private final Class<T> type;

        public GetObjectOp(Collection<NodeRef> ids, BulkOpListener listener, PGObjectStore db,
                Class<T> type) {
            this.queryNodes = Sets.newHashSet(ids);
            this.callback = listener;
            this.db = db;
            this.type = type;
            this.sharedCache = db.sharedCache;
        }

        @Override
        public List<ObjectInfo<T>> call() throws Exception {

            checkState(db.isOpen(), "Database is closed");
            final TYPE objType = RevObject.class.equals(type) ? null : RevObject.TYPE.valueOf(type);
            final String tableName = db.tableNameForType(objType, null);

            final int queryCount = queryNodes.size();

            final String sql = format(
                    "SELECT ((id).h1), ((id).h2),((id).h3), encode(object,'base64') FROM %s WHERE ((id).h1) = ANY(?)",
                    tableName);

            Map<ObjectId, byte[]> queryMatches = new HashMap<>();

            try (Connection cx = PGStorage.newConnection(db.dataSource)) {

                final Array array = toJDBCArray(cx, queryNodes);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, queryNodes))) {
                    ps.setFetchSize(queryCount);
                    ps.setArray(1, array);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ObjectId id = PGId.valueOf(rs, 1).toObjectId();
                            byte[] bytesBase64 = rs.getBytes(4);
                            byte[] bytes = Base64.getMimeDecoder().decode(bytesBase64);
                            queryMatches.put(id, bytes);
                        }
                    }
                }
            }

            List<ObjectInfo<T>> result = new ArrayList<>(queryCount);
            for (NodeRef n : queryNodes) {
                ObjectId id = n.getObjectId();
                byte[] bytes = queryMatches.get(id);
                if (bytes == null) {
                    callback.notFound(n.getObjectId());
                } else {
                    RevObject obj = encoder.decode(id, bytes);
                    if (objType == null || objType.equals(obj.getType())) {
                        callback.found(id, null/* this arg should be deprecated */);
                        ObjectInfo<T> info = ObjectInfo.of(n, type.cast(obj));
                        result.add(info);
                        sharedCache.put(obj);
                    } else {
                        callback.notFound(n.getObjectId());
                    }
                }
            }

            return result;
        }

        private Array toJDBCArray(Connection cx, final Collection<NodeRef> queryIds)
                throws SQLException {
            Array array;
            Object[] arr = new Object[queryIds.size()];
            Iterator<NodeRef> it = queryIds.iterator();
            for (int i = 0; it.hasNext(); i++) {
                ObjectId id = it.next().getObjectId();
                arr[i] = Integer.valueOf(PGId.valueOf(id).hash1());
            }
            array = cx.createArrayOf("integer", arr);
            return array;
        }
    }

    /**
     * Deletes the object with the specified id.
     * 
     * @return Flag indicating if object was actually removed.
     */

    private void delete(final ObjectId id, DataSource ds) {
        String sql = format("DELETE FROM %s WHERE id = CAST(ROW(?,?,?) AS OBJECTID)",
                objectsTable());

        try (Connection cx = PGStorage.newConnection(ds)) {
            cx.setAutoCommit(true);
            try (PreparedStatement stmt = cx.prepareStatement(log(sql, LOG, id))) {
                PGId.valueOf(id).setArgs(stmt, 1);
                stmt.executeUpdate();
                sharedCache.invalidate(id);
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    private static class EncodedObject {
        final ObjectId id;

        final byte[] serialized;

        final TYPE type;

        EncodedObject(final ObjectId id, final TYPE type, final byte[] serialized) {
            this.id = id;
            this.type = type;
            this.serialized = serialized;
        }
    }

    private static class InsertDbOp implements Callable<Void> {

        private final DataSource ds;

        private final AtomicBoolean abortFlag;

        private final BulkOpListener listener;

        private final PGObjectStore objectStore;

        private List<EncodedObject> batch;

        public InsertDbOp(DataSource ds, AtomicBoolean abortFlag, List<EncodedObject> batch,
                BulkOpListener listener, PGObjectStore objectStore) {
            this.ds = ds;
            this.abortFlag = abortFlag;
            this.batch = batch;
            this.listener = listener;
            this.objectStore = objectStore;
        }

        @Override
        public Void call() {
            if (abortFlag.get()) {
                return null;
            }
            try (Connection cx = PGStorage.newConnection(ds)) {
                cx.setAutoCommit(false);
                try {
                    doInsert(cx, batch);
                    if (abortFlag.get()) {
                        cx.rollback();
                    } else {
                        cx.commit();
                    }
                } catch (Exception executionEx) {
                    rollbackAndRethrow(cx, executionEx);
                } finally {
                    cx.setAutoCommit(true);
                }
            } catch (Exception connectEx) {
                abortFlag.set(true);
            }
            return null;
        }

        private void doInsert(Connection cx, List<EncodedObject> partition) throws Exception {
            Map<String, PreparedStatement> perTableStatements = new HashMap<>();
            ArrayListMultimap<String, ObjectId> perTableIds = ArrayListMultimap.create();

            // partition the objects into chunks for batch processing
            for (Iterator<EncodedObject> it = partition.iterator(); it.hasNext()
                    && !abortFlag.get();) {
                EncodedObject obj = it.next();
                final TYPE type = obj.type;
                final ObjectId id = obj.id;
                final PGId pgid = PGId.valueOf(id);
                final byte[] bytes = obj.serialized;
                {
                    final String tableName = objectStore.tableNameForType(type, pgid);
                    perTableIds.put(tableName, id);

                    PreparedStatement stmt = prepare(cx, tableName, perTableStatements);
                    pgid.setArgs(stmt, 1);
                    stmt.setBytes(4, bytes);
                    stmt.addBatch();
                }
            }

            for (String tableName : new HashSet<String>(perTableIds.keySet())) {
                if (abortFlag.get()) {
                    return;
                }
                PreparedStatement tableStatement;
                tableStatement = perTableStatements.get(tableName);
                List<ObjectId> ids = perTableIds.removeAll(tableName);
                int[] batchResults = tableStatement.executeBatch();
                tableStatement.clearParameters();
                tableStatement.clearBatch();

                notifyInserted(batchResults, ids, listener);
            }

            for (PreparedStatement tableStatement : perTableStatements.values()) {
                tableStatement.close();
            }
        }

        private PreparedStatement prepare(final Connection cx, final String tableName,
                Map<String, PreparedStatement> perTableStatements) throws SQLException {

            PreparedStatement stmt = perTableStatements.get(tableName);
            if (stmt == null) {
                String sql = format("INSERT INTO %s (id, object) VALUES(ROW(?,?,?),?)", tableName);
                stmt = cx.prepareStatement(sql);
                perTableStatements.put(tableName, stmt);
            }
            return stmt;
        }

    }

    private EncodedObject encode(RevObject o) {
        ObjectId id = o.getId();
        TYPE type = o.getType();
        byte[] serialized = encoder.encode(o);
        return new EncodedObject(id, type, serialized);
    }

    /**
     * Override to optimize batch insert.
     */
    @Override
    public void putAll(final Iterator<? extends RevObject> objects, final BulkOpListener listener) {
        checkNotNull(objects, "objects is null");
        checkNotNull(listener, "listener is null");
        checkWritable();
        config.checkRepositoryExists();

        Iterator<EncodedObject> encoded;
        {
            // let the RevObjects be encoded in several threads and then joint on a single iterator
            // of EncodedObject
            final int characteristics = IMMUTABLE | NONNULL | DISTINCT;

            Spliterator<? extends RevObject> spliterator = Spliterators
                    .spliteratorUnknownSize(objects, characteristics);

            final boolean parallel = true;
            encoded = StreamSupport.stream(spliterator, parallel).map((obj) -> encode(obj))
                    .iterator();
        }

        final int maxTasks = Math.min(Runtime.getRuntime().availableProcessors(),
                resources.threadPoolSize());

        // Insert in batches of putAllBatchSize.
        // Using several connections for the insert really boosts performance, yet we need to share
        // the connection pool with other calling threads and make sure a really large insert
        // doesn't preclude other threads from inserting (by holding the connections for too long or
        // saturating the I/O thread pool), so each batch is inserted by a single
        // InsertDbOp
        final Iterator<List<EncodedObject>> partitions;
        partitions = Iterators.partition(encoded, putAllBatchSize);

        final AtomicBoolean abortFlag = new AtomicBoolean();
        while (partitions.hasNext() && !abortFlag.get()) {
            List<InsertDbOp> tasks = new ArrayList<>(maxTasks);
            for (int i = 0; i < maxTasks && partitions.hasNext() && !abortFlag.get(); i++) {
                List<EncodedObject> batch = partitions.next();
                InsertDbOp task = new InsertDbOp(dataSource, abortFlag, batch, listener, this);
                tasks.add(task);
            }
            try {
                List<Future<Void>> results = resources.executor().invokeAll(tasks);
                if (abortFlag.get()) {
                    try {
                        for (Future<Void> r : results) {
                            r.get();
                        }
                    } catch (ExecutionException e) {
                        throw Throwables.propagate(e);
                    }
                }
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    static final String tableName(TableNames tables, TYPE type, int hash) {
        switch (type) {
        case COMMIT:
            return tables.commits();
        case FEATURE:
            return tables.features(hash);
        case FEATURETYPE:
            return tables.featureTypes();
        case TAG:
            return tables.tags();
        case TREE:
            return tables.trees();
        default:
            throw new IllegalArgumentException("Invalid type: " + type);
        }
    }

    static int notifyInserted(int[] inserted, List<ObjectId> objects, BulkOpListener listener) {
        int newObjects = 0;
        for (int i = 0; i < inserted.length; i++) {
            ObjectId id = objects.get(i);
            if (inserted[i] > 0) {
                listener.inserted(id, null);
                newObjects++;
            } else {
                listener.found(id, null);
            }
        }
        return newObjects;
    }

    /**
     * Override to optimize batch delete.
     */
    @Override
    public void deleteAll(final Iterator<ObjectId> ids, final BulkOpListener listener) {
        checkNotNull(ids, "argument objectId is null");
        checkNotNull(listener, "argument listener is null");
        checkWritable();
        config.checkRepositoryExists();

        final String sql = format("DELETE FROM %s WHERE id = CAST(ROW(?,?,?) AS OBJECTID)",
                objectsTable());

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try (PreparedStatement stmt = cx.prepareStatement(log(sql, LOG))) {
                // partition the objects into chunks for batch processing
                Iterator<List<ObjectId>> it = Iterators.partition(ids, putAllBatchSize);
                while (it.hasNext()) {
                    List<ObjectId> list = it.next();
                    for (ObjectId id : list) {
                        PGId.valueOf(id).setArgs(stmt, 1);
                        stmt.addBatch();
                    }
                    notifyDeleted(stmt.executeBatch(), list, listener);
                    stmt.clearParameters();
                    stmt.clearBatch();
                }
                cx.commit();
            } catch (SQLException e) {
                rollbackAndRethrow(cx, e);
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException connectEx) {
            throw propagate(connectEx);
        }
    }

    long notifyDeleted(int[] deleted, List<ObjectId> ids, BulkOpListener listener) {
        long count = 0;
        for (int i = 0; i < deleted.length; i++) {
            ObjectId id = ids.get(i);
            sharedCache.invalidate(id);
            if (deleted[i] > 0) {
                count++;
                listener.deleted(id);
            } else {
                listener.notFound(id);
            }
        }
        return count;
    }

    protected void checkOpen() {
        Preconditions.checkState(isOpen(), "Database is closed");
    }

    public void checkWritable() {
        checkOpen();
    }

    private static class SharedResourceReference {

        private ExecutorService executor;

        private int threadPoolSize;

        private final ThreadGroup threadGroup;

        private final ConnectionConfig config;

        SharedResourceReference(ThreadGroup threadGroup, ConnectionConfig config) {
            this.threadGroup = threadGroup;
            this.config = config;
        }

        public int threadPoolSize() {
            checkState(threadPoolSize > 0, "threadPoolSize was not set");
            return threadPoolSize;
        }

        public void trySetThreadPoolSize(int poolSize) {
            Preconditions.checkArgument(poolSize > 0);
            if (threadPoolSize == 0) {
                this.threadPoolSize = poolSize;
            }
        }

        public ExecutorService executor() {
            if (executor == null) {
                createExecutorService();
            }
            return executor;
        }

        private synchronized void createExecutorService() {
            if (executor != null) {
                return;
            }
            final ThreadFactory threadFactory = new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    return new Thread(threadGroup, r);
                }
            };
            String poolName = String.format("GeoGig PG ODB pool for %s:%d/%s", config.getServer(),
                    config.getPortNumber(), config.getDatabaseName()) + "-%d";
            this.executor = Executors.newFixedThreadPool(threadPoolSize,
                    new ThreadFactoryBuilder().setThreadFactory(threadFactory)
                            .setNameFormat(poolName).setDaemon(true).build());
        }

    }

    /**
     * Class for managing the shared resources for each database.
     */
    private static class ObjectStoreSharedResources
            extends ConnectionManager<ConnectionConfig, SharedResourceReference> {

        @Override
        protected SharedResourceReference connect(ConnectionConfig config) {
            final String threadGroupName = String.format("PG ODB threads for %s:%d/%s",
                    config.getServer(), config.getPortNumber(), config.getDatabaseName());
            final ThreadGroup threadGroup = new ThreadGroup(threadGroupName);
            SharedResourceReference ref = new SharedResourceReference(threadGroup, config);
            return ref;
        }

        @Override
        protected void disconnect(SharedResourceReference ref) {
            if (ref.executor != null) {
                ref.executor.shutdownNow();
            }
        }
    }

}