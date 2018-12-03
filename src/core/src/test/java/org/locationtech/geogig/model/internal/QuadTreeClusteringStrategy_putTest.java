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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.findNode;
import static org.locationtech.geogig.model.internal.Quadrant.NE;
import static org.locationtech.geogig.model.internal.Quadrant.NW;
import static org.locationtech.geogig.model.internal.Quadrant.SE;
import static org.locationtech.geogig.model.internal.Quadrant.SW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class QuadTreeClusteringStrategy_putTest {

    @Rule
    public QuadTreeTestSupport support = new QuadTreeTestSupport();

    @Test
    public void testSimpleSplitting() {
        QuadTreeClusteringStrategy quadStrategy = support.newStrategy();

        // giant one at the root
        support.putNode(quadStrategy, new Quadrant[] {}); // world node

        assertEquals(1, quadStrategy.root.getTotalChildCount());
        assertEquals(1, quadStrategy.root.numChildren());
        assertEquals(0, quadStrategy.root.numBuckets());

        // fill up children (not spit)
        support.putNodes(127, quadStrategy, new Quadrant[] { SW, NW, NE, SE });

        assertEquals(128, quadStrategy.root.getTotalChildCount());
        assertEquals(128, quadStrategy.root.numChildren());
        assertEquals(0, quadStrategy.root.numBuckets());

        // cause split - will be 1 at the root (non-promotable) and 128 in [0]
        support.putNode(quadStrategy, new Quadrant[] { SW, NW, NE, SE });

        assertEquals(1 + quadStrategy.normalizedSizeLimit(),
                quadStrategy.root.getTotalChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(2, quadStrategy.root.numBuckets());

        DAG dag = support.findDAG(quadStrategy, "[0]");
        assertNotNull(dag);
        assertEquals(128, dag.getTotalChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(128, dag.numChildren());

        // cause another multi-level split - will be 1 at the root (non-promotable) and 129
        // unpromotable in [0,1,2,3]
        // intermediate trees will be empty (just one bucket)
        support.putNode(quadStrategy, new Quadrant[] { SW, NW, NE, SE });

        assertEquals(130, quadStrategy.root.getTotalChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(2, quadStrategy.root.numBuckets());

        // [0] -> will be empty (just a link node)
        dag = support.findDAG(quadStrategy, "[0]");
        assertNotNull(dag);
        assertEquals(1 + quadStrategy.normalizedSizeLimit(), dag.getTotalChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [0,1] -> will be empty (just a link node)
        dag = support.findDAG(quadStrategy, "[0, 1]");
        assertNotNull(dag);
        assertEquals(1 + quadStrategy.normalizedSizeLimit(), dag.getTotalChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [0,1,2] -> will be empty (just a link node)
        dag = support.findDAG(quadStrategy, "[0, 1, 2]");
        assertNotNull(dag);
        assertEquals(1 + quadStrategy.normalizedSizeLimit(), dag.getTotalChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [0,1,2,3] -> will have the 129 unpromotable children
        dag = support.findDAG(quadStrategy, "[0, 1, 2, 3]");
        assertNotNull(dag);
        assertEquals(1 + quadStrategy.normalizedSizeLimit(), dag.getTotalChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // another giant one at the root
        support.putNode(quadStrategy, new Quadrant[] {}); // world node
        assertEquals(131, quadStrategy.root.getTotalChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(2, quadStrategy.root.numBuckets());

        // put one in a totally different quad
        support.putNode(quadStrategy, new Quadrant[] { NW, NE, NE, SE }); // world
                                                                          // node
        assertEquals(132, quadStrategy.root.getTotalChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(3, quadStrategy.root.numBuckets());

        // [1]
        dag = support.findDAG(quadStrategy, "[1]");
        assertNotNull(dag);
        assertEquals(1, dag.getTotalChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(1, dag.numChildren());

        // fill up children of [1]
        support.putNodes(127, quadStrategy, new Quadrant[] { NW, NE, NE, SE });

        dag = support.findDAG(quadStrategy, "[1]");
        assertNotNull(dag);
        assertEquals(128, dag.getTotalChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(128, dag.numChildren());

        // cause [1] to split
        support.putNode(quadStrategy, new Quadrant[] { NW, NE, NE, SE });

        // [1] will be empty (just a link)
        dag = support.findDAG(quadStrategy, "[1]");
        assertEquals(1 + quadStrategy.normalizedSizeLimit(), dag.getTotalChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [1, 2] -> will be empty (just a link node)
        dag = support.findDAG(quadStrategy, "[1, 2]");
        assertNotNull(dag);
        assertEquals(1 + quadStrategy.normalizedSizeLimit(), dag.getTotalChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [1,2,2] -> will be empty (just a link node)
        dag = support.findDAG(quadStrategy, "[1, 2, 2]");
        assertNotNull(dag);
        assertEquals(1 + quadStrategy.normalizedSizeLimit(), dag.getTotalChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [1,2,2,3] -> will have the 129 unpromotable children
        dag = support.findDAG(quadStrategy, "[1, 2, 2, 3]");
        assertNotNull(dag);
        // assertEquals(dag.numUnpromotable(), 129);
        assertEquals(1 + quadStrategy.normalizedSizeLimit(), dag.getTotalChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // lets add some unpromotables to existing nodes

        // [1]
        support.putNode(quadStrategy, new Quadrant[] { NW });
        // [1] will have one un-promotable
        dag = support.findDAG(quadStrategy, "[1]");
        /// assertEquals(dag.numUnpromotable(), 1);

        // [1, 2]
        support.putNode(quadStrategy, new Quadrant[] { NW, NE });
        // [1, 2] will have one un-promotable
        dag = support.findDAG(quadStrategy, "[1, 2]");
        /// assertEquals(dag.numUnpromotable(), 1);

        // [1, 2, 2]
        support.putNode(quadStrategy, new Quadrant[] { NW, NE, NE });

        // [1, 2, 2] will have one un-promotable
        dag = support.findDAG(quadStrategy, "[1, 2, 2]");
        /// assertEquals(dag.numUnpromotable(), 1);

        quadStrategy.dispose();
    }

    @Test
    public void testInitOriginalEmpty() {
        QuadTreeClusteringStrategy strategy = support.newStrategy();
        RevTree quadTree = DAGTreeBuilder.build(strategy, support.store());
        assertEquals(RevTree.EMPTY, quadTree);
    }

    @Test
    public void testInitOriginalSinglePointFeature() {
        QuadTreeClusteringStrategy orig = support.newStrategy();
        Node node = support.createNode("1", new Envelope(1, 1, 1, 1));
        orig.put(node);

        RevTree quadTree = DAGTreeBuilder.build(orig, support.store());
        assertEquals(1, quadTree.size());

        QuadTreeClusteringStrategy update = support.newStrategy(quadTree);
        Node node2 = support.createNode("2", new Envelope(2, 2, 2, 2));
        update.remove(node);
        update.put(node2);
        RevTree quadTree2 = DAGTreeBuilder.build(update, support.store());
        assertEquals(1, quadTree2.size());

        List<Node> lnodes = findNode("2", quadTree2, support.store());
        assertEquals(1, lnodes.size());
        assertEquals(node2, lnodes.get(0));
    }

    @Test
    public void testCollapsedTreeUpdatesAsExpected() {

        QuadTreeClusteringStrategy orig = support.newStrategy();

        // force a DAG split with nodes that fall on the NE ..
        for (int i = 1; i <= 1 + orig.normalizedSizeLimit(); i++) {
            Envelope bounds = new Envelope(i, i, 1, 1);
            Node node = support.createNode("node # " + i, bounds);
            orig.put(node);
        }
        print(orig, orig.root);
        // assertEquals("DAG depth should be 2 (root and one quad)", 2, orig.depth(orig.root));
        // assertEquals("DAG should have been collapsed", 1, orig.depth(orig.buildRoot()));

        RevTree originalCollapsedTree = DAGTreeBuilder.build(orig, support.store());

        // we know originalCollapsedTree has been collapsed to be shallower, make sure a new tree
        // based on it would preserve its structure

        QuadTreeClusteringStrategy update = support.newStrategy(originalCollapsedTree);

        Node node1 = support.createNode("node # 1", new Envelope(1, 1, 1, 1));
        Node node2 = support.createNode("node # 2", new Envelope(2, 2, 1, 1));
        Node node3 = support.createNode("node # 3", new Envelope(3, 3, 1, 1));

        // falls on the same bucket/quadrant
        Node node1Update = support.createNode(node1.getName(), new Envelope(1.1, 1.1, 1, 1));

        // changed, but same bounds
        Node node2Update = node2.update(RevObjectTestSupport.hashString("node2update"));

        // changed, falls on the opposite quadrant
        Node node3Update = node3.update(RevObjectTestSupport.hashString("node3update"),
                new Envelope(-3, -3, -1, -1));

        // update the DAG with the changed nodes
        assertEquals(1, update.update(node1, node1Update));
        assertEquals(1, update.update(node2, node2Update));
        assertEquals(1, update.update(node3, node3Update));

        // print(update, update.root);

        RevTree updatedTree = DAGTreeBuilder.build(update, support.store());

        List<Node> node11 = findNode(node1.getName(), updatedTree, support.store());
        List<Node> node12 = findNode(node2.getName(), updatedTree, support.store());
        List<Node> node13 = findNode(node3.getName(), updatedTree, support.store());
        assertEquals(node11.toString(), 1, node11.size());
        assertEquals(1, node12.size());
        assertEquals(1, node13.size());

        assertEquals(node1Update, node11.get(0));
        assertEquals(node2Update, node12.get(0));
        assertEquals(node3Update, node13.get(0));

        assertEquals(originalCollapsedTree.size(), updatedTree.size());
    }

    public @Test void testUpdatesShrinksAndExpand() {

        final ArrayList<Quadrant> targetQuadrant = Lists.newArrayList(NW, NE, NE, SE);
        // create a buckets node at some depth
        final List<Node> origNodes;
        RevTree origTree;
        DAG dag;
        {
            QuadTreeClusteringStrategy origTreeBuilder = support.newStrategy();
            origNodes = support.putNodes(1000, origTreeBuilder, targetQuadrant);
            dag = support.findDAG(origTreeBuilder, targetQuadrant);
            assertNotNull(dag);

            // build the tree
            origTree = DAGTreeBuilder.build(origTreeBuilder, support.store());
            origTreeBuilder.dispose();
        }

        // create a new builder based on the original tree
        QuadTreeClusteringStrategy updateBuilder = support.newStrategy(origTree);

        // force the [NW, NE, NE, SE] node to shrink to a leaf DAG
        List<Node> removeNodes = origNodes.subList(10, origNodes.size());
        removeNodes.forEach((n) -> assertTrue(updateBuilder.remove(n)));

        assertNull(support.findDAG(updateBuilder, targetQuadrant));

        dag = updateBuilder.root;
        assertNotNull(dag);
        assertEquals(10, dag.getTotalChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(10, dag.numChildren());

        // force the quad DAG to re-expand to buckets
        List<Node> newNodes = support.createNodes(1000, "new-", targetQuadrant);
        newNodes.forEach((n) -> assertEquals(1, updateBuilder.put(n)));

        final int expectedSize = newNodes.size() + 10;
        dag = support.findDAG(updateBuilder, targetQuadrant);
        assertEquals(expectedSize, dag.getTotalChildCount());
        assertTrue(dag.numBuckets() > 0);
        assertEquals(0, dag.numChildren());

        RevTree newTree = DAGTreeBuilder.build(updateBuilder, support.store());
        assertEquals(expectedSize, newTree.size());

        updateBuilder.dispose();
    }

    private void print(ClusteringStrategy st, DAG root) {
        int indent = root.getId().depthLength();
        System.err.print(Strings.padStart("", indent, ' '));
        System.err.println(root);
        Set<TreeId> buckets = new TreeSet<>();
        root.forEachBucket((id) -> buckets.add(id));
        for (TreeId id : buckets) {
            DAG dag = st.getOrCreateDAG(id);
            print(st, dag);
        }
    }

}
