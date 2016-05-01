/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.Environment.KEY_GETALL_BATCH_SIZE;
import static org.locationtech.geogig.storage.postgresql.Environment.KEY_PUTALL_BATCH_SIZE;
import static org.locationtech.geogig.storage.postgresql.Environment.KEY_THREADPOOL_SIZE;
import static org.locationtech.geogig.storage.postgresql.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.PGStorage.rollbackAndRethrow;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.VERSION;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectInserter;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;

/**
 * PostgreSQL implementation for {@link ObjectDatabase}.
 * <p>
 * TODO: document/force use of {@code SET constraint_exclusion=ON}
 */
public class PGObjectDatabase implements ObjectDatabase {

    static final Logger LOG = LoggerFactory.getLogger(PGObjectDatabase.class);

    private static final int DEFAULT_PUT_ALL_PARTITION_SIZE = 10_000;

    private static final int DEFAULT_GET_ALL_PARTITION_SIZE = 50;

    private final Environment config;

    private final ConfigDatabase configdb;

    private ObjectSerializingFactory serializer;

    private DataSource dataSource;

    private PGConflictsDatabase conflicts;

    private PGBlobStore blobStore;

    private ExecutorService executor = null;

    private Cache<ObjectId, byte[]> byteCache = null;

    private int threadPoolSize = 2;

    private int getAllBatchSize = DEFAULT_GET_ALL_PARTITION_SIZE;

    private int putAllBatchSize = DEFAULT_PUT_ALL_PARTITION_SIZE;

    private final boolean readOnly;

    /**
     * The serialized object is added a header that's one unsigned byte with the index of the
     * corresponding factory in this array
     */
    private static final ObjectSerializingFactory[] SUPPORTED_FORMATS = { //
            DataStreamSerializationFactoryV1.INSTANCE, //
            DataStreamSerializationFactoryV2.INSTANCE //
    };

    @Inject
    public PGObjectDatabase(final ConfigDatabase configdb, final Hints hints)
            throws URISyntaxException {
        this(configdb, Environment.get(hints), readOnly(hints));
    }

    private static boolean readOnly(Hints hints) {
        return hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
    }

    public PGObjectDatabase(final ConfigDatabase configdb, final Environment config,
            final boolean readOnly) {
        Preconditions.checkNotNull(configdb);
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(config.getRepositoryId(), "Repository id not provided");
        this.configdb = configdb;
        this.config = config;
        this.serializer = SUPPORTED_FORMATS[SUPPORTED_FORMATS.length - 1];
        this.readOnly = readOnly;
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.configure(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.verify(configdb, FORMAT_NAME, VERSION);
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

        final String repositoryId = config.getRepositoryId();
        final String conflictsTable = config.getTables().conflicts();
        final String blobsTable = config.getTables().blobs();

        conflicts = new PGConflictsDatabase(dataSource, conflictsTable, repositoryId);
        blobStore = new PGBlobStore(dataSource, blobsTable, repositoryId);

        ObjectDatabaseSharedResources.retain(configdb, config.connectionConfig);
        executor = ObjectDatabaseSharedResources.getExecutor(config.connectionConfig);
        byteCache = ObjectDatabaseSharedResources.getByteCache(config.connectionConfig);
        threadPoolSize = ObjectDatabaseSharedResources.getThreadPoolSize(config.connectionConfig);
    }

    @Override
    public boolean isOpen() {
        return dataSource != null;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void close() {
        if (dataSource != null) {
            conflicts = null;
            try {
                close(dataSource);
            } finally {
                dataSource = null;
            }
        }
        printStats("get()", getCount, getTimeNanos, getObjectCount);
        printStats("getAll()", getAllCount, getAllTimeNanos, getAllObjectCount);
        ObjectDatabaseSharedResources.release(config.connectionConfig);
        executor = null;
        byteCache = null;
    }

    @Override
    public ConflictsDatabase getConflictsDatabase() {
        Preconditions.checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();
        return conflicts;
    }

    @Override
    public PGBlobStore getBlobStore() {
        Preconditions.checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();
        return blobStore;
    }

    @VisibleForTesting
    void setPutAllBatchSize(int size) {
        this.putAllBatchSize = size;
    }

    private void printStats(String methodName, AtomicLong callCount, AtomicLong totalTimeNanos,
            AtomicLong objectCount) {
        long callTimes = callCount.get();
        if (callTimes == 0) {
            return;
        }
        long totalMillis = TimeUnit.MILLISECONDS.convert(totalTimeNanos.get(),
                TimeUnit.NANOSECONDS);
        double avgMillis = (double) totalMillis / callTimes;
        LOG.debug(String.format(
                "%s call count: %,d, objects found: %,d, total time: %,dms, avg call time: %fms\n",
                methodName, callTimes, objectCount.get(), totalMillis, avgMillis));
    }

    @Override
    public boolean exists(final ObjectId id) {
        checkNotNull(id, "argument id is null");
        checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();
        if (byteCache.getIfPresent(id) != null) {
            return true;
        }
        return new DbOp<Boolean>() {
            @Override
            protected Boolean doRun(Connection cx) throws SQLException {
                String sql = format(
                        "SELECT TRUE WHERE EXISTS ( SELECT 1 FROM %s WHERE ((id).h1) = ? AND id = CAST(ROW(?,?,?) AS OBJECTID) )",
                        config.getTables().objects());
                final PGId pgid = PGId.valueOf(id);
                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, id))) {
                    ps.setInt(1, pgid.hash1());
                    pgid.setArgs(ps, 2);
                    try (ResultSet rs = ps.executeQuery()) {
                        boolean exists = rs.next();
                        return exists;
                    }
                }

            }
        }.run(dataSource);
    }

    @Override
    public List<ObjectId> lookUp(final String partialId) {
        checkNotNull(partialId, "argument partialId is null");
        checkArgument(partialId.length() > 7, "partial id must be at least 8 characters long: ",
                partialId);
        checkState(isOpen(), "db is closed");
        config.checkRepositoryExists();

        final int hash1 = PGId.intHash(ObjectId.toRaw(partialId));

        return new DbOp<List<ObjectId>>() {
            @Override
            protected List<ObjectId> doRun(Connection cx) throws IOException, SQLException {
                final String objects = config.getTables().objects();
                final String sql = format(
                        "SELECT ((id).h2), ((id).h3) FROM %s WHERE ((id).h1) = ? LIMIT 1000",
                        objects);

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
            }
        }.run(dataSource);
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

    private AtomicLong getCount = new AtomicLong();

    private AtomicLong getObjectCount = new AtomicLong();

    private AtomicLong getTimeNanos = new AtomicLong();

    private AtomicLong getAllCount = new AtomicLong();

    private AtomicLong getAllObjectCount = new AtomicLong();

    private AtomicLong getAllTimeNanos = new AtomicLong();

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
            obj = RevTree.EMPTY;
        } else {
            getCount.incrementAndGet();
            TYPE objectType = null;
            if (type != null && !RevObject.class.equals(type)) {
                objectType = RevObject.TYPE.valueOf(type);
            }

            obj = getIfPresent(id, objectType, dataSource);
            if (obj == null) {
                return null;
            }
            getObjectCount.incrementAndGet();

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

        Iterator<ObjectId> iterator = ids.iterator();

        return new GetAllIterator(dataSource, iterator, type, listener, this);
    }

    private static class GetAllIterator<T extends RevObject> extends AbstractIterator<T> {

        private Iterator<ObjectId> ids;

        private DataSource dataSource;

        private BulkOpListener listener;

        private PGObjectDatabase db;

        private Iterator<RevObject> delegate = Collections.emptyIterator();

        @Nullable
        private TYPE type;

        private final Class<T> classFilter;

        GetAllIterator(DataSource ds, Iterator<ObjectId> ids, Class<T> type,
                BulkOpListener listener, PGObjectDatabase db) {
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
            List<Future<List<RevObject>>> list = new ArrayList<>();

            Iterator<ObjectId> ids = this.ids;
            final int getAllPartitionSize = db.getAllBatchSize;

            for (int j = 0; j < db.threadPoolSize && ids.hasNext(); j++) {
                List<ObjectId> idList = new ArrayList<>(getAllPartitionSize);
                for (int i = 0; i < getAllPartitionSize && ids.hasNext(); i++) {
                    idList.add(ids.next());
                }
                Future<List<RevObject>> objects = db.getAll(idList, dataSource, listener, type);
                list.add(objects);
            }
            final Function<Future<List<RevObject>>, List<RevObject>> function = (objs) -> {
                try {
                    return objs.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    throw Throwables.propagate(e);
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
        // CountingListener l = BulkOpListener.newCountingListener();
        // putAll(Iterators.singletonIterator(object));
        // return l.inserted() > 0;

        final ObjectId id = object.getId();
        boolean inserted = new DbOp<Boolean>() {
            @Override
            protected Boolean doRun(Connection cx) throws SQLException, IOException {
                final PGId pgid = PGId.valueOf(id);
                final String tableName = tableName(config.getTables(), object.getType(),
                        pgid.hash1());

                String sql = format("INSERT INTO %s (id, object) VALUES (ROW(?,?,?),?)", tableName);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, id, object))) {
                    pgid.setArgs(ps, 1);
                    byte[] blob = writeObject(object);
                    ps.setBytes(4, blob);

                    final int updateCount = ps.executeUpdate();

                    return Boolean.valueOf(updateCount == 1);
                }
            }
        }.run(dataSource).booleanValue();

        return inserted;
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public boolean delete(ObjectId objectId) {
        checkNotNull(objectId, "argument objectId is null");
        checkWritable();
        config.checkRepositoryExists();
        return delete(objectId, dataSource);
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids) {
        return deleteAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public ObjectInserter newObjectInserter() {
        return new ObjectInserter(this);
    }

    /**
     * Reads object from its binary representation as stored in the database.
     */
    protected RevObject readObject(InputStream bytes, ObjectId id) {
        try {
            final int serialVersionHeader = bytes.read();
            assert serialVersionHeader >= 0 && serialVersionHeader < SUPPORTED_FORMATS.length;
            final ObjectSerializingFactory serializer = SUPPORTED_FORMATS[serialVersionHeader];
            return serializer.read(id, bytes);
        } catch (IOException e) {
            throw new RuntimeException("Error reading object " + id, e);
        }
    }

    /**
     * Writes object to its binary representation as stored in the database.
     */
    protected byte[] writeObject(RevObject object) {
        return writeObject(object, serializer);
    }

    private static final byte[] writeObject(RevObject object, ObjectSerializingFactory serializer) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        LZFOutputStream cout = new LZFOutputStream(bout);
        final int storageVersionHeader = SUPPORTED_FORMATS.length - 1;
        try {
            cout.write(storageVersionHeader);
            serializer.write(object, cout);
            cout.close();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        byte[] bytes = bout.toByteArray();
        return bytes;
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
            try {
                InputStream inputStream = new LZFInputStream(new ByteArrayInputStream(cached));
                return readObject(inputStream, id);
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
        return new DbOp<RevObject>() {
            @Override
            protected RevObject doRun(Connection cx) throws SQLException {

                final PGId pgid = PGId.valueOf(id);
                final String tableName;
                final TableNames tables = config.getTables();
                if (type == null) {
                    tableName = tables.objects();
                } else {
                    switch (type) {
                    case COMMIT:
                        tableName = tables.commits();
                        break;
                    case FEATURE:
                        tableName = tables.features(pgid.hash1());
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

                // NOTE: the AND clause is for the ((id).h1) = ? comparison to use the hash index
                // and enable constraint exclusion
                String sql = format(
                        "SELECT object FROM %s WHERE ((id).h1) = ? AND id = CAST(ROW(?,?,?) AS OBJECTID)",
                        tableName);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, id))) {
                    ps.setInt(1, pgid.hash1());
                    pgid.setArgs(ps, 2);

                    RevObject obj = null;

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            byte[] bytes = rs.getBytes(1);

                            try {
                                InputStream in = new LZFInputStream(
                                        new ByteArrayInputStream(bytes));
                                obj = readObject(in, id);
                                // Only cache tree objects
                                if (obj.getType().equals(TYPE.TREE)) {
                                    byteCache.put(id, bytes);
                                }
                            } catch (IOException e) {
                                throw Throwables.propagate(e);
                            }
                        }
                        // Preconditions.checkState(!rs.next());
                        return obj;
                    }
                }

            }
        }.run(ds);
    }

    private Future<List<RevObject>> getAll(final List<ObjectId> ids, final DataSource ds,
            final BulkOpListener listener, final @Nullable TYPE type) {

        GetAllOp getAllOp = new GetAllOp(ids, listener, this, type, byteCache);
        Future<List<RevObject>> future = executor.submit(getAllOp);
        return future;
    }

    private static class GetAllOp extends DbOp<List<RevObject>>
            implements Callable<List<RevObject>> {

        private final List<ObjectId> queryIds;

        private final BulkOpListener callback;

        private final PGObjectDatabase db;

        private final Cache<ObjectId, byte[]> byteCache;

        @Nullable
        private final TYPE type;

        public GetAllOp(List<ObjectId> ids, BulkOpListener listener, PGObjectDatabase db,
                @Nullable TYPE type, Cache<ObjectId, byte[]> byteCache) {
            this.queryIds = ids;
            this.callback = listener;
            this.db = db;
            this.type = type;
            this.byteCache = byteCache;
        }

        @Override
        protected List<RevObject> doRun(Connection cx) throws IOException, SQLException {

            final TableNames tables = db.config.getTables();
            final String tableName;
            if (type == null) {
                tableName = tables.objects();
            } else {
                switch (type) {
                case COMMIT:
                    tableName = tables.commits();
                    break;
                case FEATURE:
                    tableName = tables.features();
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
            final int queryCount = queryIds.size();
            List<RevObject> found = new ArrayList<>(queryCount);
            byte[] bytes;
            ObjectId id;

            Iterator<ObjectId> iter = queryIds.iterator();
            while (iter.hasNext()) {
                id = iter.next();
                bytes = byteCache.getIfPresent(id);
                if (bytes != null) {
                    foundBytes(id, bytes, found, false);
                    iter.remove();
                }
            }

            final String sql = format(
                    "SELECT ((id).h1), ((id).h2),((id).h3), object FROM %s WHERE ((id).h1) = ANY(?)",
                    tableName);

            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, queryIds))) {

                final Array array = toJDBCArray(cx, queryIds);
                ps.setFetchSize(queryCount);
                ps.setArray(1, array);

                Stopwatch sw = Stopwatch.createStarted();
                try (ResultSet rs = ps.executeQuery()) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace(String.format("Executed getAll for %,d ids in %,dms\n",
                                queryCount, sw.elapsed(TimeUnit.MILLISECONDS)));
                    }
                    while (rs.next()) {
                        id = PGId.valueOf(rs, 1).toObjectId();
                        // only add those that are in the query set. The resultset may contain
                        // more due to hash1 clashes
                        if (queryIds.remove(id)) {
                            bytes = rs.getBytes(4);
                            foundBytes(id, bytes, found, true);
                        }
                    }
                }
                sw.stop();
                if (LOG.isTraceEnabled()) {
                    LOG.trace(String.format("Finished getAll for %,d out of %,d ids in %,dms\n",
                            found.size(), queryCount, sw.elapsed(TimeUnit.MILLISECONDS)));
                }
            }
            for (ObjectId oid : queryIds) {
                callback.notFound(oid);
            }
            return found;
        }

        private void foundBytes(ObjectId oid, byte[] bytes, List<RevObject> found, boolean cache) {
            try {
                InputStream in = new LZFInputStream(new ByteArrayInputStream(bytes));
                RevObject obj = db.readObject(in, oid);
                found.add(obj);
                callback.found(oid, Integer.valueOf(bytes.length));
                // Only cache tree objects.
                if (cache && obj.getType().equals(TYPE.TREE)) {
                    byteCache.put(oid, bytes);
                }
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }

        private Array toJDBCArray(Connection cx, final List<ObjectId> queryIds)
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

        @Override
        public List<RevObject> call() throws Exception {

            db.getAllCount.incrementAndGet();
            Stopwatch sw = Stopwatch.createStarted();
            List<RevObject> found = run(db.dataSource);
            db.getAllTimeNanos.addAndGet(sw.stop().elapsed(TimeUnit.NANOSECONDS));
            db.getAllObjectCount.addAndGet(found.size());
            return found;

        }
    }

    /**
     * Deletes the object with the specified id.
     * 
     * @return Flag indicating if object was actually removed.
     */

    private boolean delete(final ObjectId id, DataSource ds) {
        return new DbOp<Boolean>() {
            @Override
            protected Boolean doRun(Connection cx) throws SQLException {
                String sql = format("DELETE FROM %s WHERE id = CAST(ROW(?,?,?) AS OBJECTID)",
                        config.getTables().objects());

                try (PreparedStatement stmt = cx.prepareStatement(log(sql, LOG, id))) {
                    PGId.valueOf(id).setArgs(stmt, 1);
                    int updateCount = stmt.executeUpdate();
                    byteCache.invalidate(id);
                    return Boolean.valueOf(updateCount > 0);
                }
            }
        }.run(ds).booleanValue();
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

    private static class InsertDbOp extends DbOp<Void> implements Callable<Void> {

        private final DataSource ds;

        private final AtomicBoolean abortFlag;

        private final BulkOpListener listener;

        private final TableNames tables;

        private final BlockingQueue<List<EncodedObject>> objects;

        private final AtomicBoolean eofFlag;

        public InsertDbOp(DataSource ds, AtomicBoolean abortFlag, AtomicBoolean eofFlag,
                BlockingQueue<List<EncodedObject>> queue, BulkOpListener listener,
                TableNames tables) {
            this.ds = ds;
            this.abortFlag = abortFlag;
            this.eofFlag = eofFlag;
            this.objects = queue;
            this.listener = listener;
            this.tables = tables;
        }

        @Override
        public Void call() throws Exception {
            return super.run(ds);
        }

        @Override
        protected boolean isAutoCommit() {
            return false;
        }

        @Override
        protected Void doRun(Connection cx) throws IOException, SQLException {

            Map<String, PreparedStatement> perTableStatements = new HashMap<>();
            ArrayListMultimap<String, ObjectId> perTableIds = ArrayListMultimap.create();

            long insertedCount = 0;
            long processedCount = 0;
            try {
                // Stopwatch sw = Stopwatch.createStarted();
                while (true) {
                    List<EncodedObject> partition = null;
                    while (null == (partition = objects.poll(50, TimeUnit.MILLISECONDS))) {
                        if (abortFlag.get() || eofFlag.get()) {
                            break;
                        }
                    }
                    if (abortFlag.get() || partition == null) {
                        break;
                    }

                    // partition the objects into chunks for batch processing
                    for (EncodedObject obj : partition) {
                        if (abortFlag.get()) {
                            break;
                        }
                        processedCount++;
                        final TYPE type = obj.type;
                        final ObjectId id = obj.id;
                        final PGId pgid = PGId.valueOf(id);
                        final byte[] bytes = obj.serialized;
                        {
                            final String tableName = tableName(tables, type, pgid.hash1());
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
                        // Stopwatch sw = Stopwatch.createStarted();
                        int[] batchResults = tableStatement.executeBatch();
                        // System.err.printf("%,d batch inserts to %s in %s\n", ids.size(),
                        // tableName, sw.stop());
                        tableStatement.clearParameters();
                        tableStatement.clearBatch();

                        int newObjects = notifyInserted(batchResults, ids, listener);
                        insertedCount += newObjects;
                    }

                } // while

                for (PreparedStatement tableStatement : perTableStatements.values()) {
                    tableStatement.close();
                }
                if (abortFlag.get()) {
                    cx.rollback();
                } else {
                    cx.commit();
                    // System.err.printf("Inserted %,d of %,d objects in %s\n", insertedCount,
                    // processedCount, sw.stop());
                }

            } catch (SQLException e) {
                abortFlag.set(true);
                e.printStackTrace();
                Throwables.getRootCause(e).printStackTrace();
                rollbackAndRethrow(cx, e);
            } catch (Exception e) {
                abortFlag.set(true);
                e.printStackTrace();
                rollbackAndRethrow(cx, e);
            }
            return null;
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

    private static class Encoder implements Function<RevObject, EncodedObject> {

        private final ObjectSerializingFactory serializer;

        Encoder(ObjectSerializingFactory serializer) {
            this.serializer = serializer;
        }

        @Override
        public EncodedObject apply(RevObject obj) {
            byte[] bytes = writeObject(obj, serializer);
            return new EncodedObject(obj.getId(), obj.getType(), bytes);
        }
    };

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

        final Iterator<List<EncodedObject>> encoded = Iterators
                .partition(Iterators.transform(objects, new Encoder(serializer)), putAllBatchSize);

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
        TableNames tables = config.getTables();

        List<Future<Void>> tasks = new ArrayList<>(numTasks);
        for (int i = 0; i < numTasks; i++) {
            InsertDbOp task = new InsertDbOp(dataSource, abortFlag, eofFlag, queue, listener,
                    tables);
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
            throw Throwables.propagate(e);
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
    public long deleteAll(final Iterator<ObjectId> ids, final BulkOpListener listener) {
        checkNotNull(ids, "argument objectId is null");
        checkNotNull(listener, "argument listener is null");
        checkWritable();
        config.checkRepositoryExists();
        return new DbOp<Long>() {
            @Override
            protected boolean isAutoCommit() {
                return false;
            }

            @Override
            protected Long doRun(Connection cx) throws SQLException, IOException {
                String sql = format("DELETE FROM %s WHERE id = CAST(ROW(?,?,?) AS OBJECTID)",
                        config.getTables().objects());

                long count = 0;

                try (PreparedStatement stmt = cx.prepareStatement(log(sql, LOG))) {
                    // partition the objects into chunks for batch processing
                    Iterator<List<ObjectId>> it = Iterators.partition(ids, putAllBatchSize);

                    while (it.hasNext()) {
                        List<ObjectId> l = it.next();
                        for (ObjectId id : l) {
                            PGId.valueOf(id).setArgs(stmt, 1);
                            stmt.addBatch();
                        }

                        count += notifyDeleted(stmt.executeBatch(), l, listener);
                        stmt.clearParameters();
                        stmt.clearBatch();
                    }
                    cx.commit();
                } catch (SQLException e) {
                    rollbackAndRethrow(cx, e);
                }
                return count;
            }
        }.run(dataSource);
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

    private void checkOpen() {
        Preconditions.checkState(isOpen(), "Database is closed");
    }

    public void checkWritable() {
        checkOpen();
        if (readOnly) {
            throw new IllegalStateException("db is read only.");
        }
    }

    /**
     * Class for managing the shared resources for each database.
     */
    private static class ObjectDatabaseSharedResources {
        private static final ConcurrentMap<Environment.ConnectionConfig, SharedResourceReference> sharedResources = new ConcurrentHashMap<Environment.ConnectionConfig, SharedResourceReference>();

        /**
         * Keeps track of the number of object databases using the database resources so they can be
         * removed when no longer needed.
         */
        private static class SharedResourceReference {
            final ExecutorService executor;

            final Cache<ObjectId, byte[]> byteCache;

            final int threadPoolSize;

            int refCount = 1;

            SharedResourceReference(Cache<ObjectId, byte[]> byteCache, ExecutorService executor,
                    int threadPoolSize) {
                this.executor = executor;
                this.byteCache = byteCache;
                this.threadPoolSize = threadPoolSize;
            }
        }

        public synchronized static void retain(ConfigDatabase configdb,
                Environment.ConnectionConfig config) {
            SharedResourceReference ref = sharedResources.get(config);
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

                ExecutorService databaseExecutor = createExecutorService(config, threadPoolSize);
                Cache<ObjectId, byte[]> byteCache = createCache(configdb);
                ref = new SharedResourceReference(byteCache, databaseExecutor, threadPoolSize);
                sharedResources.put(config, ref);
            } else {
                ref.refCount++;
            }
        }

        public synchronized static void release(Environment.ConnectionConfig config) {
            SharedResourceReference ref = sharedResources.get(config);
            if (ref != null) {
                ref.refCount--;
                if (ref.refCount == 0) {
                    ref.executor.shutdownNow();
                    ref.byteCache.cleanUp();
                    sharedResources.remove(config);
                }
            }
        }

        public static ExecutorService getExecutor(Environment.ConnectionConfig config) {
            return sharedResources.get(config).executor;
        }

        public static Cache<ObjectId, byte[]> getByteCache(Environment.ConnectionConfig config) {
            return sharedResources.get(config).byteCache;
        }

        public static int getThreadPoolSize(Environment.ConnectionConfig config) {
            return sharedResources.get(config).threadPoolSize;
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
            cacheBuilder = cacheBuilder.maximumWeight(maxSize.or(defaultCacheSize()));
            cacheBuilder.weigher(new Weigher<ObjectId, byte[]>() {
                @Override
                public int weigh(ObjectId arg0, byte[] arg1) {
                    return arg1.length;
                }

            });
            cacheBuilder.expireAfterAccess(expireSeconds.or(300), TimeUnit.SECONDS);
            cacheBuilder.initialCapacity(initialCapacity.or(10_000));
            cacheBuilder.concurrencyLevel(concurrencyLevel.or(4));
            cacheBuilder.softValues();
            return cacheBuilder.build();

        }

        private static ExecutorService createExecutorService(Environment.ConnectionConfig config,
                int threadPoolSize) {
            String poolName = String.format("GeoGig PG ODB pool for %s:%d/%s", config.getServer(),
                    config.getPortNumber(), config.getDatabaseName()) + "-%d";
            return Executors.newFixedThreadPool(threadPoolSize,
                    new ThreadFactoryBuilder().setNameFormat(poolName).setDaemon(true).build());
        }

        private static long defaultCacheSize() {
            final long maxMemory = Runtime.getRuntime().maxMemory();
            // Use 20% of the heap by default
            return (long) (maxMemory * 0.2);
        }

    }

}