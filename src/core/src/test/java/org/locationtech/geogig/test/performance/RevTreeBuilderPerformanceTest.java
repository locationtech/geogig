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
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.model.internal.ClusteringStrategyBuilder;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Stopwatch;

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

    private static final ObjectId UPDATE_ID = RevObjectTestSupport.hashString("fake2");

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

    protected ObjectStore createObjectStore() throws Exception {
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

    private Stream<Node> nodes(final int numNodes, ObjectId fakeId) {
        return IntStream.range(0, numNodes).parallel().mapToObj(i -> createNode(i, fakeId));
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

    // @Ignore
    @Test
    public void testBuilUnordered_04_10M() {
        testBuildUnordered(10_000_000);
    }

    // @Ignore
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
        System.err.printf("\n----------------------\n%s\nObjectStore: %s\t DAG Store: %s\n",
                testName.getMethodName(), //
                odb.getClass().getSimpleName(), //
                ClusteringStrategyBuilder.getDAGStoreName());

        Stopwatch totalTime = Stopwatch.createStarted();
        Stream<Node> nodes = nodes(size, FAKE_ID);

        RevTreeBuilder builder = CanonicalTreeBuilder.create(odb);

        System.err.print("\tInserting nodes...");
        Stopwatch swputall = Stopwatch.createStarted();

        final int count = putAll(size, nodes, builder);

        System.err.printf("%,d nodes inserted in %s\n", count, swputall.stop());
        System.err.flush();

        System.err.print("\tbuilding...");
        Stopwatch swbuild = Stopwatch.createStarted();
        final RevTree tree = builder.build();
        System.err.printf("%,d features tree built in %s (%s)\n\tUpdating...", tree.size(),
                swbuild.stop(), tree.getId());
        assertEquals(size, tree.size());

        if (!(odb instanceof HeapObjectStore)) {
            odb.close();
            odb.open();
        }

        builder = CanonicalTreeBuilder.create(odb, tree);
        Stopwatch swputallupdate = Stopwatch.createStarted();
        final int updateCount = putAll(size / 2, nodes(size / 2, UPDATE_ID), builder);
        System.err.printf("%,d nodes updated in %s\n", updateCount, swputallupdate.stop());

        System.err.print("\tbuilding...");
        Stopwatch swbuildupdate = Stopwatch.createStarted();
        final RevTree updatedTree = builder.build();
        System.err.printf("%,d features tree updated in %s (%s)\n", updatedTree.size(),
                swbuildupdate.stop(), updatedTree.getId());

        totalTime.stop();
        System.err.printf("\tTotal time: %s\n", totalTime);
        if (odb instanceof HeapObjectStore) {
            HeapObjectStore hos = (HeapObjectStore) odb;
            System.err.printf("\tTotal trees created: %,d\n", hos.size());
        }
        System.err.println(
                "SIZE\tINSERT\tBUILD\tUPDATE\tBUILD UPDATE\tTOTAL CREATE\tTOTAL UPDATE\tTOTAL\tTreeID\tUpdatedTreeID");
        TimeUnit millis = TimeUnit.MILLISECONDS;
        System.err.printf("%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%s\t%s\n", //
                size, //
                swputall.elapsed(millis), //
                swbuild.elapsed(millis), //
                swputallupdate.elapsed(millis), //
                swbuildupdate.elapsed(millis), //
                swputall.elapsed(millis) + swbuild.elapsed(millis), //
                swputallupdate.elapsed(millis) + swbuildupdate.elapsed(millis), //
                totalTime.elapsed(millis), //
                tree.getId(), //
                updatedTree.getId()//
        );
        System.err.println("----------------------");
    }

    private Node createNode(int i, ObjectId fakeId) {
        String name = "Feature." + i;
        Envelope env = new Envelope(0, 0, i, i);
        Node ref = RevObjectFactory.defaultInstance().createNode(name, fakeId, ObjectId.NULL,
                TYPE.FEATURE, env, null);
        return ref;
    }

    private int putAll(final int size, final Stream<Node> nodes, final RevTreeBuilder b) {
        int count = 0, s = 0;
        final int step = size / 100;

        Node n = null;
        for (Iterator<Node> it = nodes.iterator(); it.hasNext();) {
            n = it.next();
            count++;
            s++;
            b.put(n);
            if (s == step) {
                s = 0;
                System.err.print('#');
            }
        }
        System.err.println();
        return count;
    }

}
