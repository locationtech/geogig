/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.stresstest;

import static com.google.common.collect.ImmutableList.copyOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.geotools.data.GeoGigDataStore;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.geogig.test.integration.TestContextBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class DataStoreConcurrencyTest {

    private Context context;

    private GeoGigDataStore store;

    private static final SimpleFeatureType pointType;
    static {
        final String pointsTypeSpec = "sp:String,ip:Integer,pp:Point:srid=4326";
        try {
            pointType = DataUtilities.createType("point", pointsTypeSpec);
        } catch (SchemaException e) {
            throw new RuntimeException(e);
        }
    }

    private ExecutorService editThreads;

    private ExecutorService readThreads;

    private final int writeThreadCount = 4, readThreadCount = 4;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private int initialCommitCount;

    // Feature insert counter
    private static final AtomicInteger CONCURRENT_INSERT_COUNT = new AtomicInteger(0);
    // List of Feature counts read by ReadTask instances
    private static final ArrayList<Integer> READ_COUNT_LIST = new ArrayList<>(4);

    @Before
    public void beforeTest() throws Exception {

        File workingDirectory = tmp.newFolder("repo");
        File userHomeDirectory = tmp.newFolder("home");
        TestPlatform platform = new TestPlatform(workingDirectory);
        platform.setUserHome(userHomeDirectory);

        GlobalContextBuilder.builder(new TestContextBuilder(platform));
        context = GlobalContextBuilder.builder().build(new Hints().platform(platform));

        GeoGIG repo = new GeoGIG(context);
        repo.command(InitOp.class).call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("gabriel").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("gabriel@roldan.example.com").call();

        store = new GeoGigDataStore(repo.getRepository());

        store.createSchema(pointType);

        editThreads = Executors.newFixedThreadPool(writeThreadCount,
                new ThreadFactoryBuilder().setNameFormat("edit-thread-%d").build());

        readThreads = Executors.newFixedThreadPool(readThreadCount,
                new ThreadFactoryBuilder().setNameFormat("read-thread-%d").build());
        initialCommitCount = copyOf(context.command(LogOp.class).call()).size();

        // reset Insert counter each scenario
        CONCURRENT_INSERT_COUNT.set(0);
        // clear ReadTask counts each scenario
        READ_COUNT_LIST.clear();
    }

    @After
    public void afterTest() throws Exception {
        if (store != null) {
            store.dispose();
        }
        if (editThreads != null) {
            editThreads.shutdownNow();
        }
        if (readThreads != null) {
            readThreads.shutdownNow();
        }
    }

    @Test
    public void testConcurrentEdits() throws Exception {
        final int insertsPerTask = 20;
        List<Future<Integer>> insertResults = runInserts(writeThreadCount, insertsPerTask);
        for (Future<Integer> f : insertResults) {
            assertEquals(insertsPerTask, f.get().intValue());
        }

        List<RevCommit> commits = copyOf(context.command(LogOp.class).call());
        final int expectedCommitCount = initialCommitCount + insertsPerTask * writeThreadCount;
        assertEquals(expectedCommitCount, commits.size());
    }

    @Test
    public void testConcurrentReads() throws Exception {

        final int insertsPerTask = 20;
        assertEquals(insertsPerTask, runInserts(1, insertsPerTask).get(0).get().intValue());

        final int readsPerTask = 20;
        List<Future<Integer>> readResults = runReads(readThreadCount, readsPerTask);

        for (Future<Integer> f : readResults) {
            assertEquals(insertsPerTask * readsPerTask, f.get().intValue());
        }
    }

    @Test
    public void testConcurrentReadsWithIndex() throws Exception {

        final int insertsPerTask = 20;
        List<Future<Integer>> runInserts = runInserts(1, insertsPerTask);
        assertEquals(1, runInserts.size());
        Integer inserts = runInserts.get(0).get();
        assertEquals(insertsPerTask, inserts.intValue());

        // create an index
        Optional<ObjectId> createOrUpdateIndex = store.createOrUpdateIndex(pointType.getTypeName());
        assertTrue(createOrUpdateIndex.isPresent());
        ObjectId indexId = createOrUpdateIndex.get();
        assertNotNull(indexId);

        final int readsPerTask = 20;
        List<Future<Integer>> readResults = runReads(1, readsPerTask);
        for (Future<Integer> f : readResults) {
            assertEquals(insertsPerTask * readsPerTask, f.get().intValue());
        }
    }

    @Test
    public void testConcurrentEditsAndReads() throws Exception {

        final int insertsPerTask = 20;
        final int readsPerTask = 40;

        // have something to read
        runInserts(1, insertsPerTask).get(0).get();

        List<Future<Integer>> insertResults = runInserts(writeThreadCount, insertsPerTask);
        // a small sleep here allows the InsertTasks to get a head-start on the ReadTasks,
        // making it much more likely that the ReadTasks read more than the initial set
        // of inserts above. The faster the machine, the quicker the ReadTasks will cmplete,
        // meaning they will read fewer Features as the InserTasks have less time to insert
        // new features.
        Thread.sleep(300);
        List<Future<Integer>> readResults = runReads(readThreadCount, readsPerTask);

        for (Future<Integer> f : insertResults) {
            assertEquals(insertsPerTask, f.get().intValue());
        }
        for (Future<Integer> f : readResults) {
            // ensure the READ_COUNT_LIST has a matching count for each ReadTask Future
            final Integer readCount = f.get();
            assertTrue(String.format("Unexpected read count: %s", readCount),
                    READ_COUNT_LIST.contains(readCount));
        }

        List<RevCommit> commits = copyOf(context.command(LogOp.class).call());
        final int expectedCommitCount = insertsPerTask + initialCommitCount
                + insertsPerTask * writeThreadCount;
        assertEquals(expectedCommitCount, commits.size());
    }

    private List<Future<Integer>> runInserts(final int writeThreadCount, final int insertsPerTask) {
        List<Future<Integer>> insertResults = Lists.newArrayList();
        for (int i = 0; i < writeThreadCount; i++) {
            insertResults.add(editThreads.submit(new InsertTask(store, insertsPerTask)));
        }
        return insertResults;
    }

    private List<Future<Integer>> runReads(final int readThreadCount, final int readsPerTask) {
        List<Future<Integer>> readResults = Lists.newArrayList();
        for (int i = 0; i < readThreadCount; i++) {
            readResults.add(readThreads.submit(new ReadTask(store, readsPerTask)));
        }
        return readResults;
    }

    private static GeometryFactory gf = new GeometryFactory();

    private static Point createRandomPoint() {
        return gf.createPoint(new Coordinate(Math.random() * 358 - 179, Math.random() * 178 - 89));
    }

    public static class InsertTask implements Callable<Integer> {

        private static final Random rnd = new Random(1000);

        private final GeoGigDataStore dataStore;

        private final SimpleFeatureBuilder builder;

        private int numInserts;

        public InsertTask(GeoGigDataStore store, int numInserts) {
            this.dataStore = store;
            this.numInserts = numInserts;
            this.builder = new SimpleFeatureBuilder(pointType);
        }

        @Override
        public Integer call() {
            int random;
            synchronized (rnd) {
                random = rnd.nextInt();
            }
            final String typeName = pointType.getTypeName();
            SimpleFeatureStore featureSource;
            int insertCount = 0;
            try {
                for (int i = 0; i < numInserts; i++) {
                    builder.reset();
                    builder.set("pp", createRandomPoint());
                    builder.set("sp", String.valueOf(random));
                    builder.set("ip", Integer.valueOf(random));
                    SimpleFeature feature = builder.buildFeature(String.valueOf(random));

                    featureSource = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);
                    Transaction tx = new DefaultTransaction();
                    featureSource.setTransaction(tx);
                    try {
                        featureSource.addFeatures(DataUtilities.collection(feature));
                        // ensure the insert and count increment are atomic
                        synchronized (CONCURRENT_INSERT_COUNT) {
                            tx.commit();
                            insertCount++;
                            CONCURRENT_INSERT_COUNT.getAndIncrement();
                        }
                    } finally {
                        tx.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return insertCount;
        }
    }

    public static class ReadTask implements Callable<Integer> {

        private final GeoGigDataStore dataStore;

        private final int numReads;

        public ReadTask(GeoGigDataStore store, final int numReads) {
            this.dataStore = store;
            this.numReads = numReads;
        }

        @Override
        public Integer call() {
            int readCount = 0;
            try {
                for (int i = 0; i < numReads; i++) {
                    // ensure the read and readCount increment are atomic
                    synchronized( CONCURRENT_INSERT_COUNT) {
                        assertEquals(CONCURRENT_INSERT_COUNT.get(), doRead());
                        readCount += CONCURRENT_INSERT_COUNT.get();
                    }
                }
                // set the total number of features read for this Task on the READ_COUNT_LIST
                READ_COUNT_LIST.add(readCount);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return readCount;
        }

        private int doRead() throws IOException {
            final String typeName = pointType.getTypeName();
            SimpleFeatureSource featureSource;
            featureSource = dataStore.getFeatureSource(typeName);
            SimpleFeatureCollection fc = featureSource.getFeatures();
            SimpleFeatureIterator features = fc.features();
            int count = 0;
            while (features.hasNext()) {
                SimpleFeature next = features.next();
                count++;
            }
            features.close();
            return count;
        }
    }

    public static void main(String args[]) {
        DataStoreConcurrencyTest test = new DataStoreConcurrencyTest();
        try {
            test.tmp.create();
            test.beforeTest();
            test.testConcurrentEditsAndReads();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                test.afterTest();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
