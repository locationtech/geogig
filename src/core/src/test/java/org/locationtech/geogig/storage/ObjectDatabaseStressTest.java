/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevFeatureImpl;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.storage.BulkOpListener.CountingListener;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

public abstract class ObjectDatabaseStressTest {
    private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    ObjectDatabase db;

    @Before
    public void setUp() throws IOException {
        File workingDirectory = tmp.getRoot();
        tmp.newFolder(".geogig");
        Platform platform = new TestPlatform(workingDirectory);
        ConfigDatabase config = new IniFileConfigDatabase(platform);
        db = createDb(platform, config);
        db.open();
    }

    protected abstract ObjectDatabase createDb(Platform platform, ConfigDatabase config);

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Ignore
    @Test
    public void test() {
        Stopwatch sw = Stopwatch.createStarted();
        int count = 1_000;
        for (int i = 0; i < count; i++) {
            RevObject o = fakeObject(i);
            Assert.assertTrue(db.put(o));
            // Assert.assertFalse(db.put(o));
        }
        sw.stop();
        System.err.printf("inserted %,d in %s\n", count, sw);
        // Assert.assertFalse(db.put(o));
    }

    @Ignore
    @Test
    public void testPutAll_1K() throws Exception {
        testPutAll(1000);
    }

    @Test
    public void testPutAll_10K() throws Exception {
        testPutAll(10_000);
    }

    @Test
    public void testPutAll_100K() throws Exception {
        testPutAll(100_000);
    }

    // @Ignore
    @Test
    public void testPutAll_1M() throws Exception {
        testPutAll(1000_000);
    }

    @Ignore
    @Test
    public void testPutAll_5M() throws Exception {
        testPutAll(5000_000);
    }

    @Ignore
    @Test
    public void testPutAll_10M() throws Exception {
        testPutAll(10_000_000);
    }

    @Ignore
    @Test
    public void testPutAll_50M() throws Exception {
        testPutAll(50_000_000);
    }

    private void testPutAll(final int count) throws Exception {
        MemoryUsage initialMem = MEMORY_MX_BEAN.getHeapMemoryUsage();

        Stopwatch sw = Stopwatch.createStarted();
        final int threadCount = 4;

        final CountingListener listener = BulkOpListener.newCountingListener();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            int jobSize = count / threadCount;
            final int from = t * jobSize;
            if (t == threadCount - 1) {
                jobSize = jobSize + (count % threadCount);
            }
            final Iterator<RevObject> objects = asObjects(sequentialIds(from, jobSize).iterator());
            Future<?> future = executor.submit(new Runnable() {
                @Override
                public void run() {
                    // Stopwatch sw = Stopwatch.createStarted();
                    db.putAll(objects, listener);
                    // sw.stop();
                    // System.err.printf("\t%s inserted %,d in %s\n",
                    // Thread.currentThread().getName(), myCount, sw);
                }
            });
            futures.add(future);
        }

        for (Future<?> f : futures) {
            f.get();
        }
        sw.stop();
        System.err.printf("--- %,d inserted in %s\n", listener.inserted(), sw);
        Assert.assertEquals(count, listener.inserted());

        final MemoryUsage indexCreateMem = MEMORY_MX_BEAN.getHeapMemoryUsage();

        executor.shutdownNow();

        Assert.assertEquals(count, listener.inserted());

        db.close();
        db.open();

        final int queryCount = count / 10;

        testGettIfPresent(count, queryCount);

        MemoryUsage getIfPresentTraversedMem = MEMORY_MX_BEAN.getHeapMemoryUsage();

        Iterable<ObjectId> ids = randomIds(queryCount, count);

        CountingListener getAllListener = BulkOpListener.newCountingListener();
        sw.reset().start();
        final int returnedObjectCount = Iterators.size(db.getAll(ids, getAllListener));
        System.err.printf("----- %,d random objects queried (%,d not found) with getAll() in %s\n",
                getAllListener.found(), getAllListener.notFound(), sw.stop());
        System.err.printf("%,d objects returned on iterator\n", returnedObjectCount);

        MemoryUsage getAllTraversedMem = MEMORY_MX_BEAN.getHeapMemoryUsage();

        MemoryUsage afterGCMem = null;
        if (count >= 1_000_000) {
            System.gc();
            Thread.sleep(1000);
            System.gc();
            Thread.sleep(1000);
            afterGCMem = MEMORY_MX_BEAN.getHeapMemoryUsage();
        }
        reportMem(initialMem, indexCreateMem, getIfPresentTraversedMem, getAllTraversedMem,
                afterGCMem);
        reportRepoSize();
    }

    private void testGettIfPresent(final int count, final int queryCount) {
        Stopwatch s = Stopwatch.createStarted();
        int found = 0;
        for (Iterator<ObjectId> it = randomIds(queryCount, count).iterator(); it.hasNext();) {
            ObjectId id = it.next();
            RevObject o = db.getIfPresent(id);
            if (o != null) {
                found++;
            }
            // Assert.assertNotNull(o);
        }
        System.err.printf(
                "----- %,d out of %,d random objects queried with getIfPresent() in %s\n", found,
                queryCount, s.stop());

        Assert.assertEquals(queryCount, found);
    }

    private void reportRepoSize() throws IOException {
        String cmd = "du -sh " + tmp.getRoot().getAbsolutePath();
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader stdError = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String s;
        while ((s = stdError.readLine()) != null) {
            System.err.println(s.trim());
        }
    }

    private void reportMem(MemoryUsage initialMem, MemoryUsage indexCreateMem,
            MemoryUsage getIfPresentTraversedMem, MemoryUsage getAllTraversedMem,
            @Nullable MemoryUsage afterGCMem) {

        final double mbFactor = 1024 * 1024;

        System.err.printf(
                "Initial memory usage: %.2fMB, after creating: %.2fMB, after random traversal: %.2fMB"
                        + ", after random getAll() traversal: %.2fMB",
                (initialMem.getUsed() / mbFactor), (indexCreateMem.getUsed() / mbFactor),
                (getIfPresentTraversedMem.getUsed() / mbFactor),
                (getAllTraversedMem.getUsed() / mbFactor));

        if (afterGCMem != null) {
            System.err.printf(", after GC: %.2fMB\n", (afterGCMem.getUsed() / mbFactor));
        }
        System.err.println();
    }

    private Iterable<ObjectId> sequentialIds(final int from, final int count) {
        Preconditions.checkArgument(from >= 0 && count > 0);

        return new Iterable<ObjectId>() {
            @Override
            public Iterator<ObjectId> iterator() {
                return new AbstractIterator<ObjectId>() {
                    int c = from;

                    final int to = from + count;

                    @Override
                    protected ObjectId computeNext() {
                        if (c == to) {
                            return endOfData();
                        }
                        return fakeId(c++);
                    }
                };
            }
        };
    }

    private Iterable<ObjectId> randomIds(final int count, final int total) {
        return new Iterable<ObjectId>() {

            @Override
            public Iterator<ObjectId> iterator() {
                return new AbstractIterator<ObjectId>() {
                    final Random random = new Random();

                    int c = 0;

                    @Override
                    protected ObjectId computeNext() {
                        if (c == count) {
                            return endOfData();
                        }
                        c++;
                        int q = random.nextInt(total);
                        return fakeId(q);
                    }
                };
            }
        };

    }

    public Iterator<RevObject> asObjects(Iterator<ObjectId> ids) {
        return Iterators.transform(ids, new Function<ObjectId, RevObject>() {
            @Override
            public RevObject apply(ObjectId input) {
                return fakeObject(input);
            }
        });
    }

    private RevObject fakeObject(int i) {
        ObjectId objectId = fakeId(i);
        return fakeObject(objectId);
    }

    private RevObject fakeObject(ObjectId objectId) {
        // String oidString = objectId.toString();
        // ObjectId treeId = ObjectId.forString("tree" + oidString);
        // ImmutableList<ObjectId> parentIds = ImmutableList.of();
        // RevPerson author = new RevPersonImpl("Gabriel", "groldan@boundlessgeo.com", 1000, -3);
        // RevPerson committer = new RevPersonImpl("Gabriel", "groldan@boundlessgeo.com", 1000, -3);
        // String message = "message " + oidString;
        // return new RevCommitImpl(objectId, treeId, parentIds, author, committer, message);

        ImmutableList.Builder<Optional<Object>> builder = new ImmutableList.Builder();

        builder.add(Optional.absent());
        builder.add(Optional.of("Some string value " + objectId));

        ImmutableList<Optional<Object>> values = builder.build();
        RevFeatureImpl feature = new RevFeatureImpl(objectId, values);
        return feature;
    }

    private ObjectId fakeId(int i) {
        return ObjectId.forString("fakeID" + i);
    }

}
