/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterators.partition;
import static com.sleepycat.je.OperationStatus.NOTFOUND;
import static com.sleepycat.je.OperationStatus.SUCCESS;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.AbstractObjectDatabase;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.fs.FileBlobStore;
import org.locationtech.geogig.storage.fs.FileConflictsDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentLockedException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

/**
 * 
 */
abstract class JEObjectDatabase extends AbstractObjectDatabase implements ObjectDatabase {

    /** Name of the BDB JE Environment inside the .geogig folder used for the objects database */
    static final String ENVIRONMENT_NAME = "objects";

    private static final Logger LOGGER = LoggerFactory.getLogger(JEObjectDatabase.class);

    private static final int SYNC_BYTES_LIMIT = 512 * 1024 * 1024;

    @Nullable
    private ExecutorService dbSyncService;

    private ExecutorService writerService;

    /**
     * The default number of objects bulk operations are partitioned into
     * 
     * @see #getAll(Iterable, BulkOpListener)
     * @see #putAll(Iterator, BulkOpListener)
     * @see #deleteAll(Iterator, BulkOpListener)
     */
    private static final Integer DEFAULT_BULK_PARTITIONING = 10 * 1000;

    private static final String BULK_PARTITIONING_CONFIG_KEY = "bdbje.bulkpartition";

    private static final String OBJECT_DURABILITY_CONFIG_KEY = "bdbje.object_durability";

    private EnvironmentBuilder envProvider;

    /**
     * Lazily loaded, do not access it directly but through {@link #createEnvironment()}
     */
    protected Environment env;

    protected Database objectDb;

    protected final ConfigDatabase configDB;

    private final boolean readOnly;

    private final String envName;

    private final FileConflictsDatabase conflicts;

    private final FileBlobStore blobStore;

    public JEObjectDatabase(final ObjectSerializingFactory serialization,
            final ConfigDatabase configDB, final EnvironmentBuilder envProvider,
            final boolean readOnly, final String envName) {
        super(serialization);
        this.configDB = configDB;
        this.envProvider = envProvider;
        this.readOnly = readOnly;
        this.envName = envName;
        File geoGigDirectory = envProvider.getGeoGigDirectory();
        this.conflicts = new FileConflictsDatabase(geoGigDirectory);
        this.blobStore = new FileBlobStore(geoGigDirectory);
    }

    /**
     * @return creates and returns the environment
     */
    private synchronized Environment createEnvironment(boolean readOnly)
            throws com.sleepycat.je.EnvironmentLockedException {

        Environment env = envProvider.setRelativePath(this.envName).setReadOnly(readOnly).get();

        return env;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public synchronized void close() {
        if (env == null) {
            LOGGER.trace("Database already closed.");
            return;
        }

        final File envHome = env.getHome();
        try {
            LOGGER.debug("Closing object database at {}", envHome);
            if (writerService != null) {
                writerService.shutdown();
                waitForServiceShutDown(writerService);
            }
            if (objectDb != null) {
                objectDb.close();
                objectDb = null;
            }
            if (dbSyncService != null) {
                dbSyncService.shutdown();
                waitForServiceShutDown(dbSyncService);
            }
            LOGGER.trace("ObjectDatabase closed. Closing environment...");
            if (!readOnly) {
                env.sync();
                env.cleanLog();
            }
        } finally {
            conflicts.close();
            blobStore.close();
            env.close();
            env = null;
        }
        LOGGER.debug("Database {} closed.", envHome);
    }

    private void waitForServiceShutDown(ExecutorService service) {
        try {
            while (!service.isTerminated()) {
                service.awaitTermination(100, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Error waiting for service to finish", e);
        }
    }

    @Override
    public boolean isOpen() {
        return objectDb != null;
    }

    @Override
    public synchronized void open() {
        if (isOpen()) {
            LOGGER.trace("Environment {} already open", env.getHome());
            return;
        }
        this.objectDb = createDatabase();

        int nWriterThreads = 1;
        writerService = Executors.newFixedThreadPool(nWriterThreads, new ThreadFactoryBuilder()
                .setNameFormat("BDBJE-" + env.getHome().getName() + "-WRITE-THREAD-%d").build());
        if (!objectDb.getConfig().getTransactional()) {
            dbSyncService = Executors.newFixedThreadPool(nWriterThreads, new ThreadFactoryBuilder()
                    .setNameFormat("BDBJE-" + env.getHome().getName() + "-SYNC-THREAD-%d").build());
        }
        this.conflicts.open();
        this.blobStore.open();
        LOGGER.debug("Object database opened at {}. Transactional: {}", env.getHome(),
                objectDb.getConfig().getTransactional());

    }

    @Override
    public FileConflictsDatabase getConflictsDatabase() {
        return conflicts;
    }

    @Override
    public BlobStore getBlobStore() {
        return blobStore;
    }

    protected Database createDatabase() {

        final String databaseName = "ObjectDatabase";
        Environment environment;
        try {
            environment = createEnvironment(readOnly);
        } catch (EnvironmentLockedException e) {
            throw new IllegalStateException(
                    "The repository is already open by another process for writing", e);
        }

        if (!environment.getDatabaseNames().contains(databaseName)) {
            if (readOnly) {
                environment.close();
                try {
                    environment = createEnvironment(false);
                } catch (EnvironmentLockedException e) {
                    throw new IllegalStateException(String.format(
                            "Environment open readonly but database %s does not exist.",
                            databaseName));
                }
            }
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            Database openDatabase = environment.openDatabase(null, databaseName, dbConfig);
            openDatabase.close();
            environment.flushLog(true);
            environment.close();
            environment = createEnvironment(readOnly);
        }

        Database database;
        try {
            LOGGER.debug("Opening ObjectDatabase at {}", environment.getHome());

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setCacheMode(CacheMode.MAKE_COLD);
            dbConfig.setKeyPrefixing(false);// can result in a slightly smaller db size

            dbConfig.setReadOnly(readOnly);
            boolean transactional = environment.getConfig().getTransactional();
            dbConfig.setTransactional(transactional);
            dbConfig.setDeferredWrite(!transactional);

            database = environment.openDatabase(null, databaseName, dbConfig);
        } catch (RuntimeException e) {
            if (environment != null) {
                environment.close();
            }
            throw e;
        }
        this.env = environment;
        return database;

    }

    @Override
    protected List<ObjectId> lookUpInternal(final byte[] partialId) {
        checkOpen();

        DatabaseEntry key;
        {
            byte[] keyData = partialId.clone();
            key = new DatabaseEntry(keyData);
        }

        DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0, 0, true);// do not retrieve data

        List<ObjectId> matches;

        CursorConfig cursorConfig = new CursorConfig();
        cursorConfig.setReadUncommitted(true);

        Transaction transaction = null;
        Cursor cursor = objectDb.openCursor(transaction, cursorConfig);
        try {
            // position cursor at the first closest key to the one looked up
            OperationStatus status = cursor.getSearchKeyRange(key, data, LockMode.READ_UNCOMMITTED);
            if (SUCCESS.equals(status)) {
                matches = new ArrayList<ObjectId>(2);
                final byte[] compKey = new byte[partialId.length];
                while (SUCCESS.equals(status)) {
                    byte[] keyData = key.getData();
                    System.arraycopy(keyData, 0, compKey, 0, compKey.length);
                    if (Arrays.equals(partialId, compKey)) {
                        matches.add(new ObjectId(keyData));
                    } else {
                        break;
                    }
                    status = cursor.getNext(key, data, LockMode.READ_UNCOMMITTED);
                }
            } else {
                matches = Collections.emptyList();
            }
            return matches;
        } finally {
            cursor.close();
        }
    }

    /**
     * @see org.locationtech.geogig.storage.ObjectDatabase#exists(org.locationtech.geogig.model.ObjectId)
     */
    @Override
    public boolean exists(final ObjectId id) {
        checkOpen();

        Preconditions.checkNotNull(id, "argument id is null");

        DatabaseEntry key = new DatabaseEntry(id.getRawValue());
        DatabaseEntry data = new DatabaseEntry();
        // tell db not to retrieve data
        data.setPartial(0, 0, true);

        final LockMode lockMode = LockMode.READ_UNCOMMITTED;
        Transaction transaction = null;
        OperationStatus status = objectDb.get(transaction, key, data, lockMode);
        return SUCCESS == status;
    }

    @Override
    protected InputStream getRawInternal(final ObjectId id, final boolean failIfNotFound) {
        checkOpen();

        Preconditions.checkNotNull(id, "id is null");
        DatabaseEntry key = new DatabaseEntry(id.getRawValue());
        DatabaseEntry data = new DatabaseEntry();

        final LockMode lockMode = LockMode.READ_UNCOMMITTED;
        Transaction transaction = null;
        OperationStatus operationStatus = objectDb.get(transaction, key, data, lockMode);
        if (NOTFOUND.equals(operationStatus)) {
            if (failIfNotFound) {
                throw new IllegalArgumentException("Object does not exist: " + id.toString()
                        + " at " + env.getHome().getAbsolutePath());
            }
            return null;
        }
        final byte[] cData = data.getData();

        return new ByteArrayInputStream(cData);
    }

    @Override
    public void putAll(final Iterator<? extends RevObject> objects, final BulkOpListener listener) {
        checkNotNull(objects, "objects is null");
        checkNotNull(listener, "listener is null");
        checkWritable();

        if (!objects.hasNext()) {
            return;
        }

        final int buffSize = 256 * 1024;
        BulkInsert task = new BulkInsert(objects, listener, buffSize);

        try {
            task.run();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private class BulkInsert {

        private BulkOpListener listener;

        private int buffSize;

        private Iterator<? extends RevObject> objects;

        public BulkInsert(final Iterator<? extends RevObject> objects,
                final BulkOpListener listener, final int buffSize) {
            this.objects = objects;
            this.listener = listener;
            this.buffSize = buffSize;
        }

        public Integer run() throws Exception {
            int count = 0;
            List<Future<Void>> pendingWrites = new ArrayList<Future<Void>>();
            try {
                InternalByteArrayOutputStream out = new InternalByteArrayOutputStream(
                        this.buffSize);
                TreeMap<ObjectId, int[]> offsets = Maps.newTreeMap(ObjectId.NATURAL_ORDER);

                int objectsInBuffer = 0;
                while (true) {
                    if (!serializeNextObject(offsets, out)) {
                        break;
                    }
                    count++;
                    objectsInBuffer++;
                    if (out.size() >= buffSize) {
                        Future<Void> future = insertSortedObjects(offsets, out);
                        // future.get();
                        // out.reset();
                        // offsets.clear();
                        out = new InternalByteArrayOutputStream(this.buffSize);
                        offsets = Maps.newTreeMap(ObjectId.NATURAL_ORDER);
                        pendingWrites.add(future);
                        if (pendingWrites.size() == 10) {
                            waitForWrites(pendingWrites);
                        }

                        LOGGER.debug("Inserted {} objects with a byte buffer of {} KB",
                                objectsInBuffer, (out.size() / 1024));
                        objectsInBuffer = 0;
                        out.reset();
                    }
                }
                if (!offsets.isEmpty()) {
                    Future<Void> future = insertSortedObjects(offsets, out);
                    pendingWrites.add(future);
                    LOGGER.debug("Inserted {} objects with a byte buffer of {} KB", objectsInBuffer,
                            (out.size() / 1024));
                }
                waitForWrites(pendingWrites);
            } catch (Exception e) {
                LOGGER.error("Error inserting objects: " + e.getMessage(), e);
                throw e;
            } finally {
                pendingWrites.clear();
                pendingWrites = null;
            }
            return count;
        }

        private void waitForWrites(List<Future<Void>> pendingWrites)
                throws InterruptedException, ExecutionException {
            if (pendingWrites.isEmpty()) {
                return;
            }

            for (Future<Void> pendingWrite : pendingWrites) {
                if (!pendingWrite.isDone()) {
                    pendingWrite.get();
                }
            }

            pendingWrites.clear();
        }

        private Future<Void> insertSortedObjects(TreeMap<ObjectId, int[]> offsets,
                InternalByteArrayOutputStream buffer) throws Exception {

            return writerService.submit(new InsertTask(offsets, buffer, listener));
        }

        private boolean serializeNextObject(TreeMap<ObjectId, int[]> offsets,
                InternalByteArrayOutputStream out) {
            if (!objects.hasNext()) {
                return false;
            }
            RevObject o = objects.next();
            int offset = out.size();
            writeObject(o, out);
            int size = out.size() - offset;
            offsets.put(o.getId(), new int[] { offset, size });

            return true;
        }

    }

    private AtomicInteger bytesWritten = new AtomicInteger();

    private class InsertTask implements Callable<Void> {

        private TreeMap<ObjectId, int[]> offsets;

        private InternalByteArrayOutputStream buffer;

        private BulkOpListener listener;

        public InsertTask(TreeMap<ObjectId, int[]> offsets, InternalByteArrayOutputStream buffer,
                BulkOpListener listener) {
            this.offsets = offsets;
            this.buffer = buffer;
            this.listener = listener;
        }

        @Override
        public Void call() throws Exception {

            Transaction transaction = newTransaction();

            final int numObjects = offsets.size();
            try {
                final int bufferBytes = buffer.size();
                DatabaseEntry key = new DatabaseEntry(new byte[ObjectId.NUM_BYTES]);
                final byte[] rawData = buffer.bytes();

                for (Iterator<Map.Entry<ObjectId, int[]>> it = offsets.entrySet().iterator(); it
                        .hasNext();) {
                    Entry<ObjectId, int[]> e = it.next();
                    it.remove();
                    final ObjectId objectId = e.getKey();
                    int offset = e.getValue()[0];
                    int size = e.getValue()[1];

                    objectId.getRawValue(key.getData());
                    DatabaseEntry data = new DatabaseEntry(rawData, offset, size);

                    OperationStatus status = objectDb.putNoOverwrite(transaction, key, data);
                    if (OperationStatus.SUCCESS.equals(status)) {
                        listener.inserted(objectId, size);
                    } else if (OperationStatus.KEYEXIST.equals(status)) {
                        listener.found(objectId, null);
                    }

                }
                final boolean transactional = objectDb.getConfig().getTransactional();
                if (transactional) {
                    commit(transaction);
                    LOGGER.trace("Committed {} inserts to {}", numObjects,
                            objectDb.getEnvironment().getHome());
                } else {
                    int totalWritten;
                    synchronized (bytesWritten) {
                        totalWritten = bytesWritten.addAndGet(bufferBytes);
                    }
                    if (totalWritten >= SYNC_BYTES_LIMIT) {
                        writerService.execute(new FlushLogTask(bytesWritten, objectDb));
                    }
                }
            } catch (Exception e) {
                abort(transaction);
                throw e;
            } finally {
                offsets = null;
                buffer = null;
            }
            return null;
        }

    }

    private class FlushLogTask implements Runnable {

        private Environment env;

        private volatile AtomicInteger bytesWritten;

        private Database objectDb;

        public FlushLogTask(AtomicInteger bytesWritten, Database objectDb) {
            this.bytesWritten = bytesWritten;
            this.objectDb = objectDb;
            this.env = objectDb.getEnvironment();
        }

        @Override
        public void run() {
            boolean doSync = false;
            final int buffSize;
            synchronized (bytesWritten) {
                buffSize = bytesWritten.get();
                if (buffSize >= SYNC_BYTES_LIMIT) {
                    doSync = true;
                    bytesWritten.set(0);
                }
            }
            if (doSync) {
                Preconditions.checkState(dbSyncService != null,
                        "DB Sync executor service is null, but the database is non transactional.");
                dbSyncService.execute(new Runnable() {

                    @Override
                    public void run() {
                        Stopwatch sw = Stopwatch.createStarted();
                        if (objectDb.getConfig().getDeferredWrite()) {
                            objectDb.sync();
                            env.evictMemory();
                            env.cleanLog();
                            // env.sync();
                        } else {
                            env.flushLog(false);
                        }
                        LOGGER.debug("flushed db log after {} bytes in {}", buffSize, sw.stop());
                    }
                });
            }

        }
    }

    @Override
    protected boolean putInternal(final ObjectId id, final byte[] rawData) {
        checkWritable();

        final Transaction transaction = newTransaction();

        final OperationStatus status;
        try {
            status = putInternal(id, rawData, transaction);
            commit(transaction);
        } catch (RuntimeException e) {
            abort(transaction);
            throw e;
        }
        final boolean didntExist = SUCCESS.equals(status);

        return didntExist;
    }

    private OperationStatus putInternal(final ObjectId id, final byte[] rawData,
            Transaction transaction) {
        OperationStatus status;
        final byte[] rawKey = id.getRawValue();
        DatabaseEntry key = new DatabaseEntry(rawKey);
        DatabaseEntry data = new DatabaseEntry(rawData);

        status = objectDb.putNoOverwrite(transaction, key, data);
        return status;
    }

    @Override
    public void delete(final ObjectId id) {
        Preconditions.checkNotNull(id, "argument id is null");
        checkWritable();
        final byte[] rawKey = id.getRawValue();
        final DatabaseEntry key = new DatabaseEntry(rawKey);

        final Transaction transaction = newTransaction();

        try {
            objectDb.delete(transaction, key);
            commit(transaction);
        } catch (RuntimeException e) {
            abort(transaction);
            throw e;
        }
    }

    private void abort(@Nullable Transaction transaction) {
        if (transaction != null) {
            try {
                transaction.abort();
            } catch (Exception e) {
                LOGGER.error("Error aborting transaction", e);
            }
        }
    }

    private void commit(@Nullable Transaction transaction) {
        if (transaction != null) {
            try {
                transaction.commit();
            } catch (Exception e) {
                LOGGER.error("Error committing transaction", e);
            }
        }
    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids, final BulkOpListener listener) {
        Preconditions.checkNotNull(ids, "argument ids is null");
        Preconditions.checkNotNull(listener, "argument listener is null");
        checkWritable();

        UnmodifiableIterator<List<ObjectId>> partition = partition(ids, getBulkPartitionSize());

        final DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0, 0, true);// do not retrieve data

        while (partition.hasNext()) {
            List<ObjectId> nextIds = Lists.newArrayList(partition.next());
            Collections.sort(nextIds);

            final Transaction transaction = newTransaction();

            CursorConfig cconfig = new CursorConfig();
            final Cursor cursor = objectDb.openCursor(transaction, cconfig);

            try {
                DatabaseEntry key = new DatabaseEntry(new byte[ObjectId.NUM_BYTES]);
                for (ObjectId id : nextIds) {
                    // copy id to key object without allocating new byte[]
                    id.getRawValue(key.getData());

                    OperationStatus status = cursor.getSearchKey(key, data, LockMode.DEFAULT);
                    if (OperationStatus.SUCCESS.equals(status)) {
                        OperationStatus delete = cursor.delete();
                        if (OperationStatus.SUCCESS.equals(delete)) {
                            listener.deleted(id);
                        } else {
                            listener.notFound(id);
                        }
                    } else {
                        listener.notFound(id);
                    }
                }
                cursor.close();
            } catch (Exception e) {
                cursor.close();
                abort(transaction);
                Throwables.propagate(e);
            }
            commit(transaction);
        }
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, final BulkOpListener listener) {
        return getAll(ids, listener, RevObject.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends RevObject> Iterator<T> getAll(final Iterable<ObjectId> ids,
            final BulkOpListener listener, final Class<T> type) {

        Preconditions.checkNotNull(ids, "ids is null");
        Preconditions.checkNotNull(listener, "listener is null");
        Preconditions.checkNotNull(type, "type is null");
        checkOpen();

        return (Iterator<T>) new CursorRevObjectIterator(ids.iterator(), listener, type);

    }

    private class CursorRevObjectIterator extends AbstractIterator<RevObject> implements Closeable {

        private final ObjectSerializingFactory reader = JEObjectDatabase.this.serializer;

        @Nullable
        private Transaction transaction;

        private Cursor cursor;

        private BulkOpListener listener;

        private UnmodifiableIterator<List<ObjectId>> unsortedIds;

        private Iterator<ObjectId> sortedIds;

        private final Class<?> filter;

        /**
         * Uses a transaction to open a read only cursor for it to work when called from a different
         * threads than the one it was created at. The transaction is aborted at {@link #close()}
         */
        public CursorRevObjectIterator(final Iterator<ObjectId> objectIds,
                final BulkOpListener listener, final Class<?> filter) {

            this.filter = filter;
            this.unsortedIds = Iterators.partition(objectIds, getBulkPartitionSize());
            this.sortedIds = Collections.emptyIterator();

            this.listener = listener;
            CursorConfig cursorConfig = new CursorConfig();
            cursorConfig.setReadUncommitted(true);
            transaction = getOrCreateTransaction();
            this.cursor = objectDb.openCursor(transaction, cursorConfig);
        }

        private Transaction getOrCreateTransaction() {
            final boolean transactional = objectDb.getConfig().getTransactional();
            if (!transactional) {
                return null;
            }
            TransactionConfig config = new TransactionConfig();
            config.setReadUncommitted(true);
            Transaction t = env.beginTransaction(null, config);
            return t;
        }

        @Override
        protected RevObject computeNext() {
            if (!sortedIds.hasNext()) {
                if (unsortedIds.hasNext()) {
                    List<ObjectId> unsorted = unsortedIds.next();
                    List<ObjectId> sorted = ObjectId.NATURAL_ORDER.sortedCopy(unsorted);
                    this.sortedIds = sorted.iterator();
                } else {
                    close();
                    return endOfData();
                }
            }
            try {

                byte[] keyBuff = new byte[ObjectId.NUM_BYTES];
                DatabaseEntry key = new DatabaseEntry(keyBuff);

                RevObject found = null;
                while (sortedIds.hasNext() && found == null) {
                    ObjectId id = sortedIds.next();
                    id.getRawValue(keyBuff);
                    key.setData(keyBuff);

                    DatabaseEntry data = new DatabaseEntry();
                    // lookup data for the next key
                    OperationStatus status;
                    status = cursor.getSearchKey(key, data, LockMode.READ_UNCOMMITTED);
                    if (SUCCESS.equals(status)) {
                        InputStream rawData = new ByteArrayInputStream(data.getData());
                        found = reader.read(id, rawData);
                        if (filter.isAssignableFrom(found.getClass())) {
                            listener.found(found.getId(), data.getSize());
                        } else {
                            found = null;
                            listener.notFound(id);
                        }
                    } else {
                        listener.notFound(id);
                    }
                }
                if (found == null) {
                    return computeNext();
                }
                return found;
            } catch (Exception e) {
                try {
                    throw Throwables.propagate(e);
                } finally {
                    close();
                }
            }
        }

        @Override
        public void close() {
            sortedIds = null;
            Cursor cursor = this.cursor;
            this.cursor = null;
            if (cursor != null) {
                cursor.close();
            }
            if (transaction != null) {
                transaction.abort();
                transaction = null;
            }
        }
    }

    private int getBulkPartitionSize() {
        Optional<Integer> configuredSize = configDB.get(BULK_PARTITIONING_CONFIG_KEY,
                Integer.class);
        return configuredSize.or(DEFAULT_BULK_PARTITIONING).intValue();
    }

    @Nullable
    private Transaction newTransaction() {
        final boolean transactional = objectDb.getConfig().getTransactional();
        if (transactional) {
            TransactionConfig txConfig = new TransactionConfig();
            txConfig.setReadUncommitted(true);
            Optional<String> durability = configDB.get(OBJECT_DURABILITY_CONFIG_KEY);
            if (!durability.isPresent()) {
                durability = configDB.getGlobal(OBJECT_DURABILITY_CONFIG_KEY);
            }
            if ("safe".equals(durability.orNull())) {
                txConfig.setDurability(Durability.COMMIT_SYNC);
            } else {
                txConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);
            }
            Transaction transaction = env.beginTransaction(null, txConfig);
            return transaction;
        }
        return null;
    }

    @Override
    protected void finalize() {
        if (isOpen()) {
            LOGGER.warn("JEObjectDatabase {} was not closed. Forcing close at finalize()",
                    env.getHome());
            close();
        }
    }

    public void checkWritable() {
        checkOpen();
        if (readOnly) {
            throw new IllegalStateException(envName + " is read only.");
        }
    }

    private void checkOpen() {
        Preconditions.checkState(isOpen(), "Database is closed");
    }

    @Override
    public String toString() {
        return String.format("%s[env=%s]", getClass().getSimpleName(), envName);
    }
}
