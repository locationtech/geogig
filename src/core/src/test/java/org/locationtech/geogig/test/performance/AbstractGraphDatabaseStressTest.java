/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.performance;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.test.TestPlatform;

import com.google.common.collect.ImmutableList;

/**
 * Abstract test suite for {@link GraphDatabase} implementations.
 * <p>
 * Create a concrete subclass of this test suite and implement {@link #createInjector()} so that
 * {@code GraphDtabase.class} is bound to your implementation instance as a singleton.
 */
public abstract class AbstractGraphDatabaseStressTest {
    protected GraphDatabase database;

    protected TestPlatform platform;

    /**
     * Enables this test only if the geogig.runPerformanceTests=true system property was provided
     */
    @ClassRule
    public static EnablePerformanceTestRule performanceRule = new EnablePerformanceTestRule();

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        File root = tmpFolder.getRoot();
        tmpFolder.newFolder(".geogig");
        platform = new TestPlatform(root);
        platform.setUserHome(tmpFolder.newFolder("fake_home"));
        database = createDatabase(platform);
        database.open();
    }

    @After
    public void after() {
        if (database != null) {
            database.close();
        }
    }

    protected abstract GraphDatabase createDatabase(TestPlatform platform);

    @Test
    public void testConcurrentUses() throws Exception {
        ConcurrentLinkedQueue<String> errorLog = new ConcurrentLinkedQueue<String>();
        // ExecutorService executor = Executors.newCachedThreadPool();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (String s : new String[] { "a", "b", "c", "d" }) {
            Runnable task = new InsertMany(s, errorLog);
            futures.add(executor.submit(task));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        assertEquals(errorLog.toString(), 0, errorLog.size());
        assertEquals(100, database.getDepth(RevObjectTestSupport.hashString("a_commit_100")));
    }

    private class InsertMany implements Runnable {
        private final String key;

        private final ConcurrentLinkedQueue<String> errorLog;

        public InsertMany(String key, ConcurrentLinkedQueue<String> errorLog) {
            this.key = key;
            this.errorLog = errorLog;
        }

        public void run() {
            try {
                for (int i = 0; i < 100; i++) {
                    ObjectId root = RevObjectTestSupport.hashString(key + "_commit_" + i);
                    ObjectId commit = RevObjectTestSupport.hashString(key + "_commit_" + (i + 1));
                    database.put(commit, ImmutableList.of(root));
                }
            } catch (Exception e) {
                errorLog.offer(e.toString());
            }
        }
    }
}
