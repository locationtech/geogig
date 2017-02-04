/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.performance;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.model.impl.RevTreeBuilder;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.AbstractIterator;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Reports the performance of building large {@link RevTree}
 * <p>
 * The test is only run if the System property {@code geogig.runPerformanceTests} is set to
 * {@code true}
 * <p>
 * It also needs to be run with a rather high Heap size (4GB recommended)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RevTreeBuilderPerformanceTest {

    private ObjectStore odb;

    private static final ObjectId FAKE_ID = RevObjectTestSupport.hashString("fake");

    @Rule
    public TestName testName = new TestName();

    /**
     * Enables this test only if the geogig.runPerformanceTests=true system property was provided
     */
    @ClassRule
    public static EnablePerformanceTestRule performanceRule = new EnablePerformanceTestRule();

    @Before
    public void before() throws Exception {
        odb = createObjectStore();
        odb.open();
    }

    protected ObjectStore createObjectStore() throws Exception{
        return new HeapObjectStore();
    }

    @After
    public void after() throws Exception {
        odb.close();
        System.gc();
        Thread.sleep(3000);
        System.gc();
        Thread.sleep(1000);
        tearDown();
    }

    protected void tearDown() throws Exception {
        // override
    }

    public static void main(String[] args) {
        RevTreeBuilderPerformanceTest test = new RevTreeBuilderPerformanceTest();
        try {
            test.before();
            test.testBuilUnordered_04_10M();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                test.after();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        }
    }

    private Iterable<Node> nodes(final int numNodes) {
        return new Iterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return new AbstractIterator<Node>() {
                    int count = 0;

                    @Override
                    protected Node computeNext() {
                        count++;
                        if (count > numNodes) {
                            return endOfData();
                        }
                        return createNode(count);
                    }
                };
            }
        };
    }

    @Test
    public void testBuilUnordered_01_100K() {
        testBuildUnordered(100_000);
    }

    @Test
    public void testBuilUnordered_02_1M() {
        testBuildUnordered(1000_000);
    }

    @Test
    public void testBuilUnordered_03_5M() {
        testBuildUnordered(5000_000);
    }

    //@Ignore
    @Test
    public void testBuilUnordered_04_10M() {
        testBuildUnordered(10_000_000);
    }

    //@Ignore
    @Test
    public void testBuilUnordered_05_50M() {
        testBuildUnordered(50_000_000);
    }

    @Ignore
    @Test
    public void testBuilUnordered_06_100M() {
        testBuildUnordered(100_000_000);
    }

    private void testBuildUnordered(final int size) {
        System.err.println(testName.getMethodName() + ":\n----------------------");
        Stopwatch totalTime = Stopwatch.createUnstarted();
        Iterable<Node> nodes = nodes(size);

        RevTreeBuilder builder = CanonicalTreeBuilder.create(odb);

        createTree(size, nodes, builder, totalTime);

        totalTime.start();

        System.err.println("\tbuilding...");
        Stopwatch sw = Stopwatch.createStarted();

        RevTree tree = builder.build();

        sw.stop();
        totalTime.stop();

        System.err.printf("%,d features tree built in %s (%s)\n", tree.size(), sw, tree.getId());
        System.err.printf("\tTotal time: %s\n", totalTime);
        if (odb instanceof HeapObjectStore) {
            HeapObjectStore hos = (HeapObjectStore) odb;
            System.err.printf("\tTotal trees created: %,d, Stored size: %,d bytes\n", hos.size(),
                    hos.storageSize());
        }
        assertEquals(size, tree.size());
    }

    private static Node createNode(int i) {
        byte[] rawID = FAKE_ID.getRawValue();
        String key = "Feature." + i;
        ObjectId id = new ObjectId(rawID);
        Envelope env = new Envelope(0, 0, i, i);
        Node ref = Node.create(key, id, FAKE_ID, TYPE.FEATURE, env);
        return ref;
    }

    private RevTreeBuilder createTree(final int size, final Iterable<Node> nodes,
            final RevTreeBuilder b, Stopwatch totalTime) {

        Preconditions.checkArgument(!totalTime.isRunning());
        System.err.print("\tInserting nodes...");
        int count = 0, s = 0;
        final int step = size / 100;
        Stopwatch sw = Stopwatch.createUnstarted();
//        Iterable<List<Node>> partitions = Iterables.partition(nodes, 100_000);
//        for (List<Node> partition : partitions) {
            sw.start();
            totalTime.start();
            for (Node n : nodes) {
                count++;
                s++;
                b.put(n);
                if (s == step) {
                    s = 0;
                    System.err.print('#');
                }
            }
            sw.stop();
            totalTime.stop();
//        }
        System.err.printf("\n%,d nodes inserted in %s\n", count, sw);
        System.err.flush();
        return b;
    }

}
