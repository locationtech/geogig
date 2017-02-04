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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.datastream.SerializationFactoryProxy;
import org.locationtech.geogig.storage.postgresql.Environment.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
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

    protected final Environment config;

    protected final ConfigDatabase configdb;

    private static final SerializationFactoryProxy encoder = new SerializationFactoryProxy();

    protected DataSource dataSource;

    private ExecutorService executor = null;

    private Cache<ObjectId, byte[]> byteCache = null;

    private int threadPoolSize = 2;

    private ThreadGroup threadGroup = null;

    private int getAllBatchSize = DEFAULT_GET_ALL_PARTITION_SIZE;

    private int putAllBatchSize = DEFAULT_PUT_ALL_PARTITION_SIZE;

    @Inject
    public PGObjectStore(final ConfigDatabase configdb, final Hints hints)
            throws URISyntaxException {
        this(configdb, Environment.get(hints));
    }

    public PGObjectStore(final ConfigDatabase configdb, final Environment config) {
        Preconditions.checkNotNull(configdb);
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(config.getRepositoryName(), "Repository id not provided");
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
        dataSource = connect();

        Optional<Integer> getAllFetchSize = configdb.get(KEY_GETALL_BATCH_SIZE, Integer.class);
        Optional<Integer> putAllBatchSize = configdb.get(KEY_PUTALL_BATCH_SIZE, Integer.class);
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

        final String prefix = config.getTables().getPrefix();
        final ConnectionConfig connectionConfig = config.connectionConfig;
        ObjectStoreSharedResources.retain(configdb, connectionConfig, prefix);
        executor = ObjectStoreSharedResources.getExecutor(connectionConfig, prefix);
        byteCache = ObjectStoreSharedResources.getByteCache(connectionConfig, prefix);
        threadPoolSize = ObjectStoreSharedResources.getThreadPoolSize(connectionConfig, prefix);
        threadGroup = ObjectStoreSharedResources.getThreadGroup(connectionConfig, prefix);
    }

    @Override
    public boolean isOpen() {
        return dataSource != null;
    }

    @Override
    public void close() {
        if (dataSource != null) {
            try {
                close(dataSource);
            } finally {
                dataSource = null;
            }
        }
        final String prefix = config.getTables().getPrefix();
        final ConnectionConfig connectionConfig = config.connectionConfig;
        ObjectStoreSharedResources.release(connectionConfig, prefix);
        executor = null;
        byteCache = null;
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
        if (byteCache.getIfPresent(id) != null) {
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

        final Set<ObjectId> queryIds = ids instanceof Set ? (Set<ObjectId>) ids
                : Sets.newHashSet(ids);

        ImmutableMap<ObjectId, byte[]> cached = byteCache.getAllPresent(queryIds);

        Iterator<T> hits = Collections.emptyIterator();
        Iterator<T> stream = Collections.emptyIterator();

        if (!cached.isEmpty()) {

            Map<ObjectId, T> cachedObjects = Maps.transformEntries(cached, (id, bytes) -> {
                RevObject o = encoder.decode(id, bytes);
                if (type.isAssignableFrom(o.getClass())) {
                    listener.found(id, Integer.valueOf(bytes.length));
                    return type.cast(o);
                }
                listener.notFound(id);
                return null;
            });

            hits = Iterators.filter(cachedObjects.values().iterator(), Predicates.notNull());
        }
        if (queryIds.size() > cached.size()) {
            Set<ObjectId> misses = Sets.difference(queryIds, cached.keySet());
            stream = new GetAllIterator(dataSource, misses.iterator(), type, listener, this);
        }

        return Iterators.concat(hits, stream);
    }

    private static class GetAllIterator<T extends RevObject> extends AbstractIterator<T> {

        private Iterator<ObjectId> ids;

        private DataSource dataSource;

        private BulkOpListener listener;

        private PGObjectStore db;

        private Iterator<RevObject> delegate = Collections.emptyIterator();

        @Nullable
        private TYPE type;

        private final Class<T> classFilter;

        GetAllIterator(DataSource ds, Iterator<ObjectId> ids, Class<T> type,
                BulkOpListener listener, PGObjectStore db) {
            this.dataSource = ds;
            this.ids = ids;
            this.listener = listener;
            this.db = db;
            this.classFilter = type;
            this.type = type == RevObject.class ? null : TYPE.valueOf(type);
        }

        @Override
        protected T computeNext() {
            if (delegate.hasNext()) {
                return classFilter.cast(delegate.next());
            }
            if (ids.hasNext()) {
                delegate = nextPartition();
                return computeNext();
            }
            ids = null;
            dataSource = null;
            delegate = null;
            listener = null;
            db = null;
            return endOfData();
        }

        private Iterator<RevObject> nextPartition() {
            checkState(db.isOpen(), "Database is closed");

            List<Future<List<RevObject>>> list = new ArrayList<>();

            Iterator<ObjectId> ids = this.ids;
            final int getAllPartitionSize = db.getAllBatchSize;

            int concurrency = db.threadPoolSize;
            for (int j = 0; j < concurrency && ids.hasNext(); j++) {
                Set<ObjectId> idList = new HashSet<>();
                for (int i = 0; i < getAllPartitionSize && ids.hasNext(); i++) {
                    idList.add(ids.next());
                }
                checkState(db.isOpen(), "Database is closed");
                Future<List<RevObject>> objects = db.getAll(idList, dataSource, listener, type);
                list.add(objects);
            }
            final Function<Future<List<RevObject>>, List<RevObject>> function = (objs) -> {
                try {
                    return objs.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    throw propagate(e);
                }
            };

            Iterable<List<RevObject>> lists = Iterables.transform(list, function);
            Iterable<RevObject> concat = Iterables.concat(lists);
            return concat.iterator();
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

    /**
     * Opens a database connection, returning the object representing connection state.
     */
    protected DataSource connect() {
        return PGStorage.newDataSource(config);
    }

    /**
     * Closes a database connection.
     * 
     * @param dataSource The connection object.
     */

    protected void close(DataSource ds) {
        PGStorage.closeDataSource(ds);
    }

    protected String tableNameForType(RevObject.TYPE type, PGId pgid) {
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
        byte[] cached = byteCache.getIfPresent(id);
        if (cached != null) {
            return encoder.decode(id, cached);
        }

        final PGId pgid = PGId.valueOf(id);
        final String tableName = tableNameForType(type, pgid);

        if (tableName == null) {
            return null;
        }

        // NOTE: the AND clause is for the ((id).h1) = ? comparison to use the hash index
        // and enable constraint exclusion
        final String sql = format(
                "SELECT object FROM %s WHERE ((id).h1) = ? AND id = CAST(ROW(?,?,?) AS OBJECTID)",
                tableName);

        byte[] bytes = null;

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, id))) {
                ps.setInt(1, pgid.hash1());
                pgid.setArgs(ps, 2);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        bytes = rs.getBytes(1);
                    }
                }
            }
        } catch (SQLException e) {
            throw propagate(e);
        }

        if (bytes == null) {
            return null;
        }

        RevObject obj = encoder.decode(id, bytes);
        // Only cache tree objects
        if (obj.getType().equals(TYPE.TREE)) {
            byteCache.put(id, bytes);
        }
        return obj;
    }

    private Future<List<RevObject>> getAll(final Set<ObjectId> ids, final DataSource ds,
            final BulkOpListener listener, final @Nullable TYPE type) {
        checkState(isOpen(), "Database is closed");

        GetAllOp getAllOp = new GetAllOp(ids, listener, this, type);
        // Avoid deadlocking by running the task synchronously if we are already in one of the
        // threads on the executor.
        if (Thread.currentThread().getThreadGroup().equals(threadGroup)) {
            try {
                List<RevObject> objects = getAllOp.call();
                return Futures.immediateFuture(objects);
            } catch (Exception e) {
                propagate(e);
            }
        }
        Future<List<RevObject>> future = executor.submit(getAllOp);
        return future;
    }

    private static class GetAllOp implements Callable<List<RevObject>> {

        private final Set<ObjectId> queryIds;

        private final BulkOpListener callback;

        private final PGObjectStore db;

        private final Cache<ObjectId, byte[]> byteCache;

        @Nullable
        private final TYPE type;

        public GetAllOp(Set<ObjectId> ids, BulkOpListener listener, PGObjectStore db,
                @Nullable TYPE type) {
            this.queryIds = ids;
            this.callback = listener;
            this.db = db;
            this.type = type;
            this.byteCache = db.byteCache;
        }

        @Override
        public List<RevObject> call() throws Exception {
            checkState(db.isOpen(), "Database is closed");
            final String tableName = db.tableNameForType(type, null);

            final int queryCount = queryIds.size();
            List<RevObject> found = new ArrayList<>(queryCount);

            if (tableName != null) {
                byte[] bytes;
                ObjectId id;

                final String sql = format(
                        "SELECT ((id).h1), ((id).h2),((id).h3), object FROM %s WHERE ((id).h1) = ANY(?)",
                        tableName);

                try (Connection cx = PGStorage.newConnection(db.dataSource)) {
                    try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, queryIds))) {

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
                                    bytes = rs.getBytes(4);

                                    RevObject obj = encoder.decode(id, bytes);
                                    if (type == null || type.equals(obj.getType())) {
                                        queryIds.remove(id);
                                        callback.found(id, Integer.valueOf(bytes.length));
                                        found.add(obj);
                                        byteCache.put(id, bytes);
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
            for (ObjectId oid : queryIds) {
                callback.notFound(oid);
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
                byteCache.invalidate(id);
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

        private final BlockingQueue<List<EncodedObject>> objects;

        private final AtomicBoolean eofFlag;

        public InsertDbOp(DataSource ds, AtomicBoolean abortFlag, AtomicBoolean eofFlag,
                BlockingQueue<List<EncodedObject>> queue, BulkOpListener listener,
                PGObjectStore objectStore) {
            this.ds = ds;
            this.abortFlag = abortFlag;
            this.eofFlag = eofFlag;
            this.objects = queue;
            this.listener = listener;
            this.objectStore = objectStore;
        }

        @Override
        public Void call() {
            try (Connection cx = PGStorage.newConnection(ds)) {
                cx.setAutoCommit(false);
                try {
                    doInsert(cx);
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

        private void doInsert(Connection cx) throws Exception {
            Map<String, PreparedStatement> perTableStatements = new HashMap<>();
            ArrayListMultimap<String, ObjectId> perTableIds = ArrayListMultimap.create();

            while (true) {
                List<EncodedObject> partition = null;
                while (null == (partition = objects.poll(50, TimeUnit.MILLISECONDS))) {
                    if (abortFlag.get() || eofFlag.get()) {
                        break;
                    }
                    Thread.yield();
                }
                if (abortFlag.get() || partition == null) {
                    break;
                }

                // partition the objects into chunks for batch processing
                for (EncodedObject obj : partition) {
                    if (abortFlag.get()) {
                        break;
                    }
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
                        break;
                    }
                    PreparedStatement tableStatement;
                    tableStatement = perTableStatements.get(tableName);
                    List<ObjectId> ids = perTableIds.removeAll(tableName);
                    int[] batchResults = tableStatement.executeBatch();
                    tableStatement.clearParameters();
                    tableStatement.clearBatch();

                    notifyInserted(batchResults, ids, listener);
                }

            } // while

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

    /**
     * Override to optimize batch insert.
     */
    @Override
    public void putAll(final Iterator<? extends RevObject> objects, final BulkOpListener listener) {
        checkNotNull(objects, "objects is null");
        checkNotNull(listener, "listener is null");
        checkWritable();
        config.checkRepositoryExists();

        final int maxTasks = Math.max(1,
                Math.min(Runtime.getRuntime().availableProcessors(), threadPoolSize) / 2);

        final Iterator<List<EncodedObject>> encoded = Iterators.partition(Iterators.transform(
                objects,
                (obj) -> new EncodedObject(obj.getId(), obj.getType(), encoder.encode(obj))),
                putAllBatchSize);

        final BlockingQueue<List<EncodedObject>> queue = new ArrayBlockingQueue<>(2 + maxTasks);

        int numTasks = 0;
        for (int i = 0; i < maxTasks; i++) {
            if (encoded.hasNext()) {
                queue.add(encoded.next());
                numTasks++;
            }
        }
        if (numTasks == 0) {
            return;
        }
        LOG.debug("putAll(): inserting using {} threads", numTasks);

        AtomicBoolean abortFlag = new AtomicBoolean();
        AtomicBoolean eofFlag = new AtomicBoolean();

        List<Future<Void>> tasks = new ArrayList<>(numTasks);
        for (int i = 0; i < numTasks; i++) {
            InsertDbOp task = new InsertDbOp(dataSource, abortFlag, eofFlag, queue, listener,
                    this);
            tasks.add(executor.submit(task));
        }

        while (!abortFlag.get() && encoded.hasNext()) {
            List<EncodedObject> encodedPartition = encoded.next();
            try {
                while (!queue.offer(encodedPartition, 50, TimeUnit.MILLISECONDS)) {
                    if (abortFlag.get()) {
                        return;
                    }
                }
            } catch (InterruptedException e) {
                abortFlag.set(true);
                e.printStackTrace();
                return;
            }
        }

        eofFlag.set(true);

        try {
            for (Future<Void> f : tasks) {
                f.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw propagate(e);
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

        long count = 0;
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
                    count += notifyDeleted(stmt.executeBatch(), list, listener);
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
            byteCache.invalidate(id);
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

    /**
     * Class for managing the shared resources for each database.
     */
    private static class ObjectStoreSharedResources {

        /**
         * A Key to the cache hash table, composed of connection info plus table names prefix.
         * <p>
         * A single database generally contains all its repos in tables with the default
         * {@code geogig_} prefix, but it's also possible that it contains separate sets of
         * repositories when the table names prefix differ. This is the case for most test cases
         * where a single database is used to exercise "disconnected" repositories (i.e. where they
         * don't share the {@code ObjectDatabase} by using different table names prefixes like
         * "geogig_XXX" and "geogig_YYY" where XXX and YYY are random numbers.
         *
         */
        private static final class Key {
            final ConnectionConfig config;

            final String tableNamesPrefix;

            public Key(ConnectionConfig config, String tableNamsPrefix) {
                this.config = config;
                this.tableNamesPrefix = tableNamsPrefix;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof Key && config.equals(((Key) o).config)
                        && tableNamesPrefix.equals(((Key) o).tableNamesPrefix);
            }

            @Override
            public int hashCode() {
                return Objects.hash(config, tableNamesPrefix);
            }
        }

        private static final ConcurrentMap<Key, SharedResourceReference> sharedResources = new ConcurrentHashMap<>();

        /**
         * Keeps track of the number of object databases using the database resources so they can be
         * removed when no longer needed.
         */
        private static class SharedResourceReference {
            final ExecutorService executor;

            final Cache<ObjectId, byte[]> byteCache;

            final int threadPoolSize;

            final ThreadGroup threadGroup;

            int refCount = 1;

            SharedResourceReference(Cache<ObjectId, byte[]> byteCache, ExecutorService executor,
                    int threadPoolSize, ThreadGroup threadGroup) {
                this.executor = executor;
                this.byteCache = byteCache;
                this.threadPoolSize = threadPoolSize;
                this.threadGroup = threadGroup;
            }
        }

        public synchronized static void retain(ConfigDatabase configdb,
                Environment.ConnectionConfig config, String tableNamesPrefix) {
            final Key key = new Key(config, tableNamesPrefix);
            SharedResourceReference ref = sharedResources.get(key);
            if (ref == null) {
                int threadPoolSize;
                Optional<Integer> tpoolSize = configdb.get(KEY_THREADPOOL_SIZE, Integer.class)
                        .or(configdb.getGlobal(KEY_THREADPOOL_SIZE, Integer.class));
                if (tpoolSize.isPresent()) {
                    Integer poolSize = tpoolSize.get();
                    Preconditions.checkState(poolSize.intValue() > 0,
                            "postgres.threadPoolSize must be a positive integer: %s. Check your config.",
                            poolSize);
                    threadPoolSize = poolSize;
                } else {
                    threadPoolSize = Math.max(Runtime.getRuntime().availableProcessors(), 2);
                }
                final String threadGroupName = String.format("PG ODB threads for %s:%d/%s",
                        config.getServer(), config.getPortNumber(), config.getDatabaseName());
                final ThreadGroup threadGroup = new ThreadGroup(threadGroupName);
                final ThreadFactory threadFactory = new ThreadFactory() {
                    public Thread newThread(Runnable r) {
                        return new Thread(threadGroup, r);
                    }
                };
                ExecutorService databaseExecutor = createExecutorService(threadFactory, config,
                        threadPoolSize);
                Cache<ObjectId, byte[]> byteCache = createCache(configdb);
                ref = new SharedResourceReference(byteCache, databaseExecutor, threadPoolSize,
                        threadGroup);
                sharedResources.put(key, ref);
            } else {
                ref.refCount++;
            }
        }

        public synchronized static void release(Environment.ConnectionConfig config,
                String tableNamesPrefix) {
            final Key key = new Key(config, tableNamesPrefix);
            SharedResourceReference ref = sharedResources.get(key);
            if (ref != null) {
                ref.refCount--;
                if (ref.refCount == 0) {
                    ref.executor.shutdownNow();
                    ref.byteCache.cleanUp();
                    sharedResources.remove(key);
                }
            }
        }

        public static ExecutorService getExecutor(Environment.ConnectionConfig config,
                String tableNamesPrefix) {
            final Key key = new Key(config, tableNamesPrefix);
            return sharedResources.get(key).executor;
        }

        public static ThreadGroup getThreadGroup(Environment.ConnectionConfig config,
                String tableNamesPrefix) {
            final Key key = new Key(config, tableNamesPrefix);
            return sharedResources.get(key).threadGroup;
        }

        public static Cache<ObjectId, byte[]> getByteCache(
                final Environment.ConnectionConfig config, final String tableNamesPrefix) {
            final Key key = new Key(config, tableNamesPrefix);
            return sharedResources.get(key).byteCache;
        }

        public static int getThreadPoolSize(Environment.ConnectionConfig config,
                String tableNamesPrefix) {
            final Key key = new Key(config, tableNamesPrefix);
            return sharedResources.get(key).threadPoolSize;
        }

        private static Cache<ObjectId, byte[]> createCache(ConfigDatabase configdb) {
            Optional<Long> maxSize = configdb.get(Environment.KEY_ODB_BYTE_CACHE_MAX_SIZE,
                    Long.class);
            Optional<Integer> concurrencyLevel = configdb
                    .get(Environment.KEY_ODB_BYTE_CACHE_CONCURRENCY_LEVEL, Integer.class);
            Optional<Integer> expireSeconds = configdb
                    .get(Environment.KEY_ODB_BYTE_CACHE_EXPIRE_SECONDS, Integer.class);
            Optional<Integer> initialCapacity = configdb
                    .get(Environment.KEY_ODB_BYTE_CACHE_INITIAL_CAPACITY, Integer.class);

            CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
            Long maxWeightBytes = maxSize.or(defaultCacheSize());
            cacheBuilder = cacheBuilder.maximumWeight(maxWeightBytes);
            cacheBuilder.weigher(new Weigher<ObjectId, byte[]>() {

                private final int ESTIMATED_OBJECTID_SIZE = 8 + ObjectId.NUM_BYTES;

                @Override
                public int weigh(ObjectId key, byte[] value) {
                    return ESTIMATED_OBJECTID_SIZE + value.length;
                }

            });
            cacheBuilder.expireAfterAccess(expireSeconds.or(300), TimeUnit.SECONDS);
            cacheBuilder.initialCapacity(initialCapacity.or(100_000));
            cacheBuilder.concurrencyLevel(concurrencyLevel.or(4));
            cacheBuilder.softValues();
            return cacheBuilder.build();

        }

        private static ExecutorService createExecutorService(ThreadFactory threadFactory,
                Environment.ConnectionConfig config, int threadPoolSize) {
            String poolName = String.format("GeoGig PG ODB pool for %s:%d/%s", config.getServer(),
                    config.getPortNumber(), config.getDatabaseName()) + "-%d";
            return Executors.newFixedThreadPool(threadPoolSize,
                    new ThreadFactoryBuilder().setThreadFactory(threadFactory)
                            .setNameFormat(poolName).setDaemon(true).build());
        }

        private static long defaultCacheSize() {
            final long maxMemory = Runtime.getRuntime().maxMemory();
            // Use 20% of the heap by default
            return (long) (maxMemory * 0.2);
        }

    }

}