/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.storage.NodeStorageOrder;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterators;
import com.vividsolutions.jts.geom.Envelope;

public abstract class AbstractNodeIndexTest extends Assert {
    private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ExecutorService executorService;

    private NodeIndex index;

    @Before
    public void before() {
        tempFolder.newFolder(".geogig");
        File workingDirectory = tempFolder.getRoot();
        Platform platform = new TestPlatform(workingDirectory);
        executorService = Executors.newFixedThreadPool(4);
        index = createIndex(platform, executorService);
    }

    protected abstract NodeIndex createIndex(Platform platform, ExecutorService executorService);

    @After
    public void after() {
        index.close();
        executorService.shutdownNow();
    }

    @Test
    public void testEmpty() {
        Iterator<Node> nodes = index.nodes();
        assertNotNull(nodes);
        assertFalse(nodes.hasNext());
    }

    @Test
    public void test1k() throws Exception {
        testNodes(1000);
    }

    @Test
    public void testOrder1k() throws Exception {
        testOrder(1000);
    }

    @Test
    public void test10k() throws Exception {
        testNodes(1000 * 10);
    }

    @Test
    public void testOrder10k() throws Exception {
        testOrder(1000 * 10);
    }

    @Test
    public void test100k() throws Exception {
        int count = 1000 * 100;
        testNodes(count);
    }

    @Test
    public void testOrder100k() throws Exception {
        int count = 1000 * 100;
        testOrder(count);
    }

    @Test
    public void test1M() throws Exception {
        int count = 1000 * 1000;
        testNodes(count);
    }

    @Test
    public void testOrder1M() throws Exception {
        int count = 1000 * 1000;
        testOrder(count);
    }

    @Ignore
    @Test
    public void test5M() throws Exception {
        testNodes(1000 * 1000 * 5);
    }

    @Ignore
    @Test
    public void test10M() throws Exception {
        testNodes(1000 * 1000 * 10);
    }

    @Ignore
    @Test
    public void test25M() throws Exception {
        testNodes(1000 * 1000 * 25);
    }

    @Ignore
    @Test
    public void test50M() throws Exception {
        testNodes(1000 * 1000 * 50);
    }

    private double getMemUsage() {
        MemoryUsage heapUsage = MEMORY_MX_BEAN.getHeapMemoryUsage();
        final double mbFactor = 1024 * 1024;
        return heapUsage.getUsed() / mbFactor;
    }

    private void testNodes(final int count) throws Exception {
        final double initialMem = getMemUsage();

        Stopwatch sw = Stopwatch.createStarted();
        for (int i = 0; i < count; i++) {
            index.add(node(i));
        }
        Iterator<Node> nodes = index.nodes();
        assertNotNull(nodes);
        System.err.printf("Added %,d nodes to %s index in %s. Traversing...\n", count, index
                .getClass().getSimpleName(), sw.stop());
        final double indexCreateMem = getMemUsage();

        sw.reset().start();
        int size = Iterators.size(nodes);
        System.err.printf("Traversed %,d nodes in %s\n", size, sw.stop());
        final double indexTraversedMem = getMemUsage();

        System.err
                .printf("Initial memory usage: %.2fMB, after creating index: %.2fMB, after traversing: %.2fMB\n",
                        initialMem, indexCreateMem, indexTraversedMem);
        if (count >= 1_000_000) {
            System.gc();
            Thread.sleep(1000);
            System.gc();
            Thread.sleep(1000);
            final double afterGCMem = getMemUsage();
            System.err.printf("Mem usage after GC: %.2fMB\n", afterGCMem);
        }
        System.err.println();
        assertEquals(count, size);
    }

    private void testOrder(final int count) throws Exception {

        for (int i = 0; i < count; i++) {
            Node node = node(i);
            index.add(node);
        }

        final Iterator<Node> nodes = index.nodes();
        Iterable<Node> iterable = new Iterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return nodes;
            }
        };
        NodeStorageOrder ordering = new NodeStorageOrder();
        assertTrue(ordering.isStrictlyOrdered(iterable));
    }

    private Node node(int i) {
        String name = String.valueOf(i);
        return Node.create(name, ObjectId.forString(name), ObjectId.NULL, TYPE.FEATURE,
                new Envelope(i, i + 1, i, i + 1));
    }
}
