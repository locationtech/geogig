/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */

package org.locationtech.geogig.model.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.findNode;
import static org.locationtech.geogig.model.internal.QuadTreeClusteringStrategy_computeIdTest.MAX_BOUNDS_WGS84;
import static org.locationtech.geogig.model.internal.QuadTreeClusteringStrategy_computeIdTest.createNode;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.model.internal.ClusteringStrategy.DAGCache;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;

import com.vividsolutions.jts.geom.Envelope;

public class QuadTreeClusteringStrategy_putTest {

    private ObjectStore store;

    @Before
    public void before() {
        store = new HeapObjectStore();
        store.open();
    }

    @After
    public void after() {
        store.close();
    }

    @Test
    public void testSimpleSplitting() {
        QuadTreeClusteringStrategy quadStrategy = createStrategy(RevTree.EMPTY);

        // giant one at the root
        putNode(quadStrategy, new Quadrant[] {}); // world node

        assertEquals(1, quadStrategy.root.getChildCount());
        assertEquals(1, quadStrategy.root.numChildren());
        assertEquals(0, quadStrategy.root.numBuckets());

        // fill up children (not spit)
        putNodes(127, quadStrategy,
                new Quadrant[] { Quadrant.SW, Quadrant.NW, Quadrant.NE, Quadrant.SE });

        assertEquals(128, quadStrategy.root.getChildCount());
        assertEquals(128, quadStrategy.root.numChildren());
        assertEquals(0, quadStrategy.root.numBuckets());

        // cause split - will be 1 at the root (non-promotable) and 128 in [0]
        putNode(quadStrategy,
                new Quadrant[] { Quadrant.SW, Quadrant.NW, Quadrant.NE, Quadrant.SE });

        assertEquals(129, quadStrategy.root.getChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(2, quadStrategy.root.numBuckets());

        DAG dag = findDAG(quadStrategy.dagCache, "[0]");
        assertNotNull(dag);
        assertEquals(128, dag.getChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(128, dag.numChildren());

        // cause another multi-level split - will be 1 at the root (non-promotable) and 129
        // unpromotable in [0,1,2,3]
        // intermediate trees will be empty (just one bucket)
        putNode(quadStrategy,
                new Quadrant[] { Quadrant.SW, Quadrant.NW, Quadrant.NE, Quadrant.SE });

        assertEquals(130, quadStrategy.root.getChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(2, quadStrategy.root.numBuckets());

        // [0] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.dagCache, "[0]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [0,1] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.dagCache, "[0, 1]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [0,1,2] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.dagCache, "[0, 1, 2]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [0,1,2,3] -> will have the 129 unpromotable children
        dag = findDAG(quadStrategy.dagCache, "[0, 1, 2, 3]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // another giant one at the root
        putNode(quadStrategy, new Quadrant[] {}); // world node
        assertEquals(131, quadStrategy.root.getChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(2, quadStrategy.root.numBuckets());

        // put one in a totally different quad
        putNode(quadStrategy,
                new Quadrant[] { Quadrant.NW, Quadrant.NE, Quadrant.NE, Quadrant.SE }); // world
                                                                                        // node
        assertEquals(132, quadStrategy.root.getChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(3, quadStrategy.root.numBuckets());

        // [1]
        dag = findDAG(quadStrategy.dagCache, "[1]");
        assertNotNull(dag);
        assertEquals(1, dag.getChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(1, dag.numChildren());

        // fill up children of [1]
        putNodes(127, quadStrategy,
                new Quadrant[] { Quadrant.NW, Quadrant.NE, Quadrant.NE, Quadrant.SE });

        dag = findDAG(quadStrategy.dagCache, "[1]");
        assertNotNull(dag);
        assertEquals(128, dag.getChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(128, dag.numChildren());

        // cause [1] to split
        putNode(quadStrategy,
                new Quadrant[] { Quadrant.NW, Quadrant.NE, Quadrant.NE, Quadrant.SE });

        // [1] will be empty (just a link)
        dag = findDAG(quadStrategy.dagCache, "[1]");
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [1, 2] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.dagCache, "[1, 2]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [1,2,2] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.dagCache, "[1, 2, 2]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [1,2,2,3] -> will have the 129 unpromotable children
        dag = findDAG(quadStrategy.dagCache, "[1, 2, 2, 3]");
        assertNotNull(dag);
        // assertEquals(dag.numUnpromotable(), 129);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // lets add some unpromotables to existing nodes

        // [1]
        putNode(quadStrategy, new Quadrant[] { Quadrant.NW });
        // [1] will have one un-promotable
        dag = findDAG(quadStrategy.dagCache, "[1]");
        /// assertEquals(dag.numUnpromotable(), 1);

        // [1, 2]
        putNode(quadStrategy, new Quadrant[] { Quadrant.NW, Quadrant.NE });
        // [1, 2] will have one un-promotable
        dag = findDAG(quadStrategy.dagCache, "[1, 2]");
        /// assertEquals(dag.numUnpromotable(), 1);

        // [1, 2, 2]
        putNode(quadStrategy, new Quadrant[] { Quadrant.NW, Quadrant.NE, Quadrant.NE });

        // [1, 2, 2] will have one un-promotable
        dag = findDAG(quadStrategy.dagCache, "[1, 2, 2]");
        /// assertEquals(dag.numUnpromotable(), 1);
    }

    public static List<Node> putNodes(int n, QuadTreeClusteringStrategy quad, Quadrant[] location) {
        List<Node> result = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            result.add(putNode(quad, location));
        }
        return result;
    }

    public static Node putNode(QuadTreeClusteringStrategy quad, Quadrant[] location) {
        long fnumb = quad.root == null ? 0 : quad.root.getChildCount();
        String quadInfo = "[";
        for (Quadrant q : location) {
            quadInfo += q.name() + ",";
        }
        quadInfo += "] - [";
        for (Quadrant q : location) {
            quadInfo += q.getBucketNumber() + ",";
        }
        quadInfo += "]";

        Node n = createNode("node # " + fnumb + ", at " + quadInfo, MAX_BOUNDS_WGS84, location);

        quad.put(n);
        return n;
    }

    public static DAG findDAG(DAGCache cache, String key) {
        for (TreeId id : cache.treeBuff.keySet()) {
            if (id.toString().equals(key))
                return cache.treeBuff.get(id);
        }
        return null;
    }

    @Test
    public void testInitOriginalEmpty() {
        QuadTreeClusteringStrategy strategy = createStrategy(RevTree.EMPTY);
        RevTree quadTree = DAGTreeBuilder.build(strategy, store);
        assertEquals(RevTree.EMPTY, quadTree);
    }

    @Test
    public void testInitOriginalSinglePointFeature() {
        QuadTreeClusteringStrategy orig = createStrategy(RevTree.EMPTY);
        Node node = createNode("1", new Envelope(1, 1, 1, 1));
        orig.put(node);

        RevTree quadTree = DAGTreeBuilder.build(orig, store);
        assertEquals(1, quadTree.size());

        QuadTreeClusteringStrategy update = createStrategy(quadTree);
        Node node2 = createNode("2", new Envelope(2, 2, 2, 2));
        update.remove(node);
        update.put(node2);
        RevTree quadTree2 = DAGTreeBuilder.build(update, store);
        assertEquals(1, quadTree2.size());

        List<Node> lnodes = findNode("2", quadTree2, store);
        assertEquals(1, lnodes.size());
        assertEquals(node2, lnodes.get(0));
    }

    private QuadTreeClusteringStrategy createStrategy(RevTree original) {

        QuadTreeClusteringStrategy quadStrategy = ClusteringStrategyBuilder//
                .quadTree(store)//
                .original(original)//
                .maxBounds(MAX_BOUNDS_WGS84)//
                .build();
        return quadStrategy;
    }

    @Test
    public void testCollapsedTreeUpdatesAsExpected() {

        QuadTreeClusteringStrategy orig = createStrategy(RevTree.EMPTY);

        // force a DAG split with nodes that fall on the NE quadrant...
        for (int i = 1; i <= 129; i++) {
            Envelope bounds = new Envelope(i, i, 1, 1);
            Node node = createNode("node # " + i, bounds);
            orig.put(node);
        }

        assertEquals("DAG depth should be 2 (root and one quad)", 2, orig.depth(orig.root));
        assertEquals("DAG should have been collapsed", 1, orig.depth(orig.buildRoot()));

        RevTree originalCollapsedTree = DAGTreeBuilder.build(orig, store);

        // we know originalCollapsedTree has been collapsed to be shallower, make sure a new tree
        // based on it would preserve its structure

        QuadTreeClusteringStrategy update = ClusteringStrategyBuilder//
                .quadTree(store)//
                .original(originalCollapsedTree)//
                .maxBounds(MAX_BOUNDS_WGS84)//
                .build();

        Node node1 = createNode("node # 1", new Envelope(1, 1, 1, 1));
        Node node2 = createNode("node # 2", new Envelope(2, 2, 1, 1));
        Node node3 = createNode("node # 3", new Envelope(3, 3, 1, 1));

        // falls on the same bucket/quadrant
        Node node1Update = createNode(node1.getName(), new Envelope(1.1, 1.1, 1, 1));

        // changed, but same bounds
        Node node2Update = node2.update(RevObjectTestSupport.hashString("node2update"));

        // changed, falls on the opposite quadrant
        Node node3Update = node3.update(RevObjectTestSupport.hashString("node3update"),
                new Envelope(-3, -3, -1, -1));

        // update the DAG with the changed nodes
        update.update(node1, node1Update);
        update.update(node2, node2Update);
        update.update(node3, node3Update);

        RevTree updatedTree = DAGTreeBuilder.build(update, store);

        List<Node> node11 = findNode(node1.getName(), updatedTree, store);
        List<Node> node12 = findNode(node2.getName(), updatedTree, store);
        List<Node> node13 = findNode(node3.getName(), updatedTree, store);
        assertEquals(1, node11.size());
        assertEquals(1, node12.size());
        assertEquals(1, node13.size());

        assertEquals(node1Update, node11.get(0));
        assertEquals(node2Update, node12.get(0));
        assertEquals(node3Update, node13.get(0));

        assertEquals(originalCollapsedTree.size(), updatedTree.size());
    }

}
