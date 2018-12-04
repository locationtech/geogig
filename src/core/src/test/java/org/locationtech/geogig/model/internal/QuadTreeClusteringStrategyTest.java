/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static com.google.common.collect.Iterables.concat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.findNode;
import static org.locationtech.geogig.model.internal.Quadrant.NE;
import static org.locationtech.geogig.model.internal.Quadrant.NW;
import static org.locationtech.geogig.model.internal.Quadrant.SE;
import static org.locationtech.geogig.model.internal.Quadrant.SW;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Test suite for {@link QuadTreeClusteringStrategy} own methods
 * 
 * @see QuadTreeClusteringStrategy_putTest
 */
public class QuadTreeClusteringStrategyTest {

    private final Envelope testMaxBounds = new Envelope(-100, 100, -100, 100);

    private final int testMaxDepth = 8;

    private QuadTreeClusteringStrategy quad;

    @Rule
    public QuadTreeTestSupport support = new QuadTreeTestSupport();

    @Before
    public void before() {
        org.junit.Assume.assumeTrue(QuadTreeClusteringStrategy.ENABLE_EXPAND_COLLAPSE);

        support.setMaxBounds(testMaxBounds);
        support.setMaxDepth(testMaxDepth);
        quad = support.newStrategy();
    }

    public @Test void testComputeQuadrantPointOnQuadEdge() {
        Envelope quadBounds = new Envelope(testMaxBounds);
        for (int depthIndex = 0; depthIndex < testMaxDepth; depthIndex++) {
            Envelope centerPoint = new Envelope(quadBounds.centre());

            // the point is at the exact center of the quad.The evaluation is performed in
            // clockwise order so SW should win
            assertEquals(SW, quad.computeQuadrant(centerPoint, depthIndex));

            Envelope middleEastPoint = new Envelope(quadBounds.getMaxX(), quadBounds.getMaxX(),
                    quadBounds.centre().y, quadBounds.centre().y);
            // the point is the eastmost shared point between NE and SE. NE should win
            assertEquals(NE, quad.computeQuadrant(middleEastPoint, depthIndex));

            quadBounds = SW.slice(quadBounds);
        }
    }

    public @Test void testComputeQuadrantLineOnQuadEdge() {
        Envelope quadBounds = new Envelope(testMaxBounds);
        for (int depthIndex = 0; depthIndex < testMaxDepth; depthIndex++) {
            quadBounds = NE.slice(quadBounds);
            Envelope line = new Envelope(quadBounds.getMinX(), quadBounds.getMinX(),
                    quadBounds.getMinY(), quadBounds.getMaxY());
            // the line matches the edge shared by NW and NE. The evaluation is performed in
            // clockwise order so NW should win
            assertEquals(NW, quad.computeQuadrant(line, depthIndex));
        }
    }

    public @Test void testComputeQuadrantExpectedNullResult() {
        Envelope env = new Envelope(0, 1, 0, 1);
        assertNull(quad.computeQuadrant(env, testMaxDepth));
        assertNull(quad.computeQuadrant(env, testMaxDepth + 1));

        assertNull(quad.computeQuadrant((Envelope) null, 0));

        Envelope offside = new Envelope(-200, -150, -200, -150);
        assertFalse(testMaxBounds.intersects(offside));
        assertNull(quad.computeQuadrant(offside, 0));

        Envelope overlaps = new Envelope(-150, -50, -150, -50);
        assertTrue(testMaxBounds.intersects(overlaps));
        assertNull(quad.computeQuadrant(overlaps, 0));

        Envelope nil = new Envelope();
        assertNull(quad.computeQuadrant(nil, 0));
    }

    public @Test void testCollapse() {
        final Envelope deepestQuadBounds = support.quadBounds(NE, NE, NE, NE);
        final Envelope point = new Envelope(deepestQuadBounds.centre());
        System.err.println(deepestQuadBounds);
        System.err.println(point);
    }

    public @Test void testCollapseRootUnpromotables() {
        // unpromotable at root because it overlaps the 4 quadrants
        final Envelope unpromotableBounds = new Envelope(-1, 1, -1, 1);

        QuadTreeClusteringStrategy strategy = support.newStrategy();

        for (int i = 0; i < 130; i++) {
            Node node = support.createNode(String.valueOf(i), unpromotableBounds);
            strategy.put(node);
        }
        assertEquals(130, strategy.root.getTotalChildCount());
        assertEquals(1, strategy.root.numBuckets());
        assertEquals(2, strategy.depth(strategy.root));
        DAG unpromotables = support.findDAG(strategy, "[4]");
        assertEquals(12, unpromotables.numBuckets());
        assertNotNull(unpromotables);

        strategy.collapse(strategy.root);
        assertEquals(1, strategy.depth(strategy.root));
        assertEquals(130, strategy.root.getTotalChildCount());
        assertEquals(12, strategy.root.numBuckets());
    }

    public @Test void testCollapseAndExpandsRootUnpromotablesToPromotables() {
        // unpromotable at root because it overlaps the 4 quadrants
        final Envelope unpromotableBounds = new Envelope(-1, 1, -1, 1);
        final Envelope promotableBounds = support.getQuadCenter(NE, SW, NW, NE);

        final List<Node> unpromotables = support.createNodes(130, unpromotableBounds);
        final RevTree tree;
        final RevTree updatedTree;
        {
            final QuadTreeClusteringStrategy strategy = support.newStrategy();

            support.putNodes(strategy, unpromotables);

            // "[4]" matches the arguments to getQuadCenter above
            support.assertDag(strategy, "[4]", unpromotables);
            assertCollapsesTo(strategy, "[4]", "[]", unpromotables);
            tree = DAGTreeBuilder.build(strategy, support.store());
        }
        final List<Node> promotables = support.createNodes(130, promotableBounds);
        {
            final QuadTreeClusteringStrategy strategy = support.newStrategy(tree);
            // strategy needs to figure out [2, 0, 1, 2, 4] was collapsed to [] and expand
            // accordingly
            assertExpandsTo(strategy, "[]", "[4]", tree);

            support.updateNodes(strategy, unpromotables, promotables);
            assertNull("[4] should have been removed as all its nodes where moved to another quad",
                    support.findDAG(strategy, "[4]"));

            support.assertDag(strategy, "[2, 0, 1, 2, 0, 2, 2, 2]", promotables);
            support.assertDag(strategy, "[2, 0, 1, 2, 0, 2, 2, 2, 4]", promotables);

            assertCollapsesTo(strategy, "[2, 0, 1, 2, 0, 2, 2, 2, 4]", "[]", promotables);

            updatedTree = DAGTreeBuilder.build(strategy, support.store());
        }

        assertTreeContents(tree, promotables);
        assertTreeContents(updatedTree, unpromotables);
    }

    public @Test void testCollapseAndExpandsRootPromotablesToUnpromotables() {
        // unpromotable at root because it overlaps the 4 quadrants
        final Envelope unpromotableBounds = new Envelope(-1, 1, -1, 1);
        final Envelope promotableBounds = support.getQuadCenter(NE, SW, NW, NE);

        final List<Node> promotables = support.createNodes(130, promotableBounds);
        final RevTree tree;
        final RevTree updatedTree;
        {
            final QuadTreeClusteringStrategy strategy = support.newStrategy();
            List<Integer> bucketsByDepth = strategy.bucketsByDepth(promotableBounds, testMaxDepth);
            assertEquals("[2, 0, 1, 2, 0, 2, 2, 2]", bucketsByDepth.toString());
            support.putNodes(strategy, promotables);

            // "[2, 0, 1, 2]" matches the arguments to getQuadCenter above
            support.assertDag(strategy, "[2, 0, 1, 2]", promotables);
            // being points falling at the center, the depth will increase up to max depth
            support.assertDag(strategy, "[2, 0, 1, 2, 0, 2, 2, 2]", promotables);
            // and being overflowed, and having reached max depth, they'll be promoted to the
            // unpromotables bucket (4)
            support.assertDag(strategy, "[2, 0, 1, 2, 0, 2, 2, 2, 4]", promotables);
            assertCollapsesTo(strategy, "[2, 0, 1, 2, 0, 2, 2, 2, 4]", "[]", promotables);
            tree = DAGTreeBuilder.build(strategy, support.store());
        }
        final List<Node> unpromotables = support.createNodes(130, unpromotableBounds);
        {
            final QuadTreeClusteringStrategy strategy = support.newStrategy(tree);
            // strategy needs to figure out [2, 0, 1, 2, 0, 2, 2, 2, 4] was collapsed to [] and
            // expand accordingly
            assertExpandsTo(strategy, "[]", "[2, 0, 1, 2, 0, 2, 2, 2, 4]", tree);
            support.updateNodes(strategy, promotables, unpromotables);
            assertNull(
                    "[2, 0, 1, 2, 0, 2, 2, 2, 4] should have been removed as all its nodes where moved to another quad",
                    support.findDAG(strategy, "[2, 0, 1, 2, 0, 2, 2, 2, 4]"));
            support.assertDag(strategy, "[4]", unpromotables);

            assertCollapsesTo(strategy, "[4]", "[]", unpromotables);

            updatedTree = DAGTreeBuilder.build(strategy, support.store());
            assertEquals(130, updatedTree.size());
        }

        assertTreeContents(tree, promotables);
        assertTreeContents(updatedTree, unpromotables);
    }

    public @Test void testCollapseAndExpandsMoreComplexStructure() {
        // unpromotable at root because it overlaps the 4 quadrants
        final Envelope unpromotableBounds = new Envelope(-1, 1, -1, 1);
        final Envelope offAreaOfValidityBounds = new Envelope(1000, 10001, 1000, 10001);
        final Envelope level1Bounds = support.createBounds("[2]");
        final Envelope level3Bounds = support.createBounds("[2, 0, 1]");
        final Envelope level5Bounds = support.createBounds("[2, 0, 1, 3, 0]");
        final Envelope level7Bounds = support.createBounds("[2, 0, 1, 3, 0, 1, 2]");

        final List<Node> level0Unpromotables = support.createNodes(130, "level0unpromotable-",
                unpromotableBounds);
        final List<Node> offMaxBoundsUnpromotables = support.createNodes(130, "offbounds-",
                offAreaOfValidityBounds);
        final List<Node> l1Nodes = support.createNodes(130, "L1-", level1Bounds);
        final List<Node> l3Nodes = support.createNodes(130, "L3-", level3Bounds);
        final List<Node> l5Nodes = support.createNodes(130, "L5-", level5Bounds);
        final List<Node> l7Nodes = support.createNodes(130, "L7-", level7Bounds);

        final RevTree tree;
        {
            QuadTreeClusteringStrategy strategy = support.newStrategy();

            support.putNodes(strategy, level0Unpromotables);
            support.assertDag(strategy, "[4]", level0Unpromotables);
            assertCollapsesTo(strategy, "[4]", "[]", level0Unpromotables);

            support.putNodes(strategy, offMaxBoundsUnpromotables);
            support.assertDag(strategy, "[4]",
                    concat(level0Unpromotables, offMaxBoundsUnpromotables));
            assertCollapsesTo(strategy, "[4]", "[]",
                    concat(level0Unpromotables, offMaxBoundsUnpromotables));

            support.putNodes(strategy, l1Nodes);
            support.assertDag(strategy, "[2]", l1Nodes);
            support.assertDag(strategy, "[2, 4]", l1Nodes);
            assertCollapsesTo(strategy, "[2, 4]", "[2]", l1Nodes);
            support.assertDag(strategy, "[]",
                    concat(level0Unpromotables, offMaxBoundsUnpromotables, l1Nodes));
            support.assertDag(strategy, "[4]",
                    concat(level0Unpromotables, offMaxBoundsUnpromotables));

            support.putNodes(strategy, l3Nodes);
            support.assertDag(strategy, "[2, 0]", l3Nodes);
            support.assertDag(strategy, "[2, 0, 1]", l3Nodes);
            support.assertDag(strategy, "[2, 0, 1,  4]", l3Nodes);
            support.assertDag(strategy, "[2]", concat(l1Nodes, l3Nodes));
            assertCollapsesTo(strategy, "[2, 0, 1, 4]", "[2, 0]", l3Nodes);

            support.putNodes(strategy, l5Nodes);
            support.assertDag(strategy, "[2, 0, 1, 3]", l5Nodes);
            support.assertDag(strategy, "[2, 0, 1, 3, 0]", l5Nodes);
            support.assertDag(strategy, "[2, 0, 1, 3, 0, 4]", l5Nodes);
            support.assertDag(strategy, "[2, 0, 1]", concat(l3Nodes, l5Nodes));
            support.assertDag(strategy, "[2, 0]", concat(l3Nodes, l5Nodes));
            support.assertDag(strategy, "[2]", concat(l1Nodes, l3Nodes, l5Nodes));
            assertCollapsesTo(strategy, "[2, 0, 1, 3, 0, 4]", "[2, 0, 3]", l5Nodes);

            support.putNodes(strategy, l7Nodes);
            support.assertDag(strategy, "[2, 0, 1, 3, 0, 1, 2]", l7Nodes);
            support.assertDag(strategy, "[2, 0, 1, 3, 0, 1, 2, 4]", l7Nodes);
            support.assertDag(strategy, "[2, 0, 1, 3, 0]", concat(l5Nodes, l7Nodes));
            support.assertDag(strategy, "[2, 0, 1]", concat(l3Nodes, l5Nodes, l7Nodes));
            support.assertDag(strategy, "[2]", concat(l1Nodes, l3Nodes, l5Nodes, l7Nodes));
            assertCollapsesTo(strategy, "[2, 0, 1, 3, 0, 1, 2, 4]", "[2, 0, 3, 1]", l7Nodes);

            strategy.buildRoot();

            support.assertDag(strategy, "[]", concat(level0Unpromotables, offMaxBoundsUnpromotables,
                    l1Nodes, l3Nodes, l5Nodes, l7Nodes));
            support.assertDag(strategy, "[4]",
                    concat(level0Unpromotables, offMaxBoundsUnpromotables));
            support.assertDag(strategy, "[2]", concat(l1Nodes, l3Nodes, l5Nodes, l7Nodes));
            support.assertDag(strategy, "[2, 0]", concat(l3Nodes, l5Nodes, l7Nodes));
            support.assertDag(strategy, "[2, 0, 3]", concat(l5Nodes, l7Nodes));
            support.assertDag(strategy, "[2, 0, 3, 1]", l7Nodes);

            tree = DAGTreeBuilder.build(strategy, support.store());
            assertEquals(2, tree.bucketsSize());
            assertTrue(tree.getBucket(2).isPresent());
            assertTrue(tree.getBucket(4).isPresent());

            strategy = support.newStrategy(tree);
            assertExpandsTo(strategy, "[4]", "[4]", tree);
            // assertExpandsTo(strategy, "[2, 0]", "[2, 0, 1, 4]", tree);

        }
        // final List<Node> unpromotables = createNodes(130, unpromotableBounds);
        // {
        // final QuadTreeClusteringStrategy strategy = support.newStrategy(tree);
        // // strategy needs to figure out [2, 0, 1, 2, 4] was collapsed to [] and expand
        // // accordingly
        // assertExpandsTo(strategy, "[]", "[2, 0, 1, 2, 4]", tree);
        // support.updateNodes(strategy, promotables, unpromotables);
        // assertNull(
        // "[2, 0, 1, 2, 4] should have been removed as all its nodes where moved to another quad",
        // support.findDAG(strategy, "[2, 0, 1, 2, 4]"));
        // assertDag(strategy, "[4]", unpromotables);
        //
        // assertCollapsesTo(strategy, "[4]", "[]", unpromotables);
        //
        // updatedTree = DAGTreeBuilder.build(strategy, support.store());
        // assertEquals(130, updatedTree.size());
        // }
        //
        // assertTreeContents(tree, promotables);
        // assertTreeContents(updatedTree, unpromotables);
    }

    public @Test void testVerySmallGeometries() throws Exception {
        support.setMaxBounds(QuadTreeTestSupport.wgs84Bounds());
        support.setMaxDepth(-1);

        final Geometry smallGeom = new WKTReader().read(
                "LINESTRING(1.401298464324817E-45 1.401298464324817E-45,2.802596928649634E-45 2.802596928649634E-45)");
        final Geometry updateGeom = new WKTReader().read("LINESTRING(1 1, 1.000001 1.000001)");

        final Envelope geomBounds = smallGeom.getEnvelopeInternal();
        final Envelope updateBounds = updateGeom.getEnvelopeInternal();

        List<Node> nodes = support.createNodes(130, geomBounds);
        List<Node> updateNodes = support.createNodes(130, updateBounds);

        QuadTreeClusteringStrategy builder = support.newStrategy();

        final int maxDepth = builder.getMaxDepth();
        final TreeId sourceBucketId = TreeId.valueOf(builder.bucketsByDepth(geomBounds, maxDepth));
        final TreeId targetBucketId = TreeId
                .valueOf(builder.bucketsByDepth(updateBounds, maxDepth));
        {
            TreeId expectedSource = TreeId.fromString(
                    "[2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]");
            TreeId expectedTarget = TreeId.fromString(
                    "[2, 0, 0, 0, 0, 0, 0, 1, 3, 1, 2, 3, 1, 2, 3, 0, 0, 0, 0, 1, 3, 1, 2, 3]");
            assertEquals(expectedSource, sourceBucketId);
            assertEquals(expectedTarget, targetBucketId);
        }

        support.putNodes(builder, nodes);

        DAG root = builder.buildRoot();
        assertEquals(130, root.getTotalChildCount());

        final RevTree tree = DAGTreeBuilder.build(builder, support.store());
        assertEquals(130, tree.size());

        QuadTreeClusteringStrategy updateBuilder = support.newStrategy(tree);
        support.updateNodes(updateBuilder, nodes, updateNodes);

        DAG newRoot = builder.buildRoot();
        assertEquals(130, newRoot.getTotalChildCount());

        final RevTree newTree = DAGTreeBuilder.build(updateBuilder, support.store());
        assertEquals(130, newTree.size());

    }

    private void assertTreeContents(RevTree tree, List<Node> expected) {
        Set<Node> expectedNodes = new HashSet<>(expected);
        Preconditions.checkArgument(expectedNodes.size() == expected.size());

        Set<Node> actual = RevObjectTestSupport.getTreeNodes(tree, support.store());
        assertEquals(expectedNodes, actual);
    }

    private void assertExpandsTo(QuadTreeClusteringStrategy strategy, String collapsedTreeId,
            String expectedExpandedTreeId, RevTree original) {

        TreeId collapsedId = TreeId.fromString(collapsedTreeId);
        TreeId expectedTreeId = TreeId.fromString(expectedExpandedTreeId);
        TreeId expandsTo = strategy.computeExpandedTreeId(original, collapsedId);
        assertEquals(expectedTreeId, expandsTo);
    }

    /**
     * Tests {@code deepPath} collapses to {@code collapsedPath} when calling {@code buildRoot()},
     * but doesn't change {@code strategy}, uses a cloned strategy instead
     */
    private void assertCollapsesTo(QuadTreeClusteringStrategy strategy, String deepPath,
            String collapsedPath, Iterable<Node> expectedNodes) {

        QuadTreeClusteringStrategy clone = support.clone(strategy);
        support.assertDag(clone, deepPath, expectedNodes);

        clone.collapse(clone.root);

        support.assertDag(clone, collapsedPath, expectedNodes);
    }

    @Test
    public void testCollapsedTreeDeletesAsExpected() {

        QuadTreeClusteringStrategy orig = support.newStrategy();

        // force a DAG split with nodes that fall on the NE ..
        for (int i = 1; i <= 1 + orig.normalizedSizeLimit(); i++) {
            Envelope bounds = new Envelope(i, i, 1, 1);
            Node node = support.createNode("node # " + i, bounds);
            orig.put(node);
        }
        // print(orig, orig.root);
        assertEquals("DAG depth should be 2 (root and one quad)", 2, orig.depth(orig.root));
        assertEquals("DAG should have been collapsed", 1, orig.depth(orig.buildRoot()));

        RevTree originalCollapsedTree = DAGTreeBuilder.build(orig, support.store());

        // we know originalCollapsedTree has been collapsed to be shallower, make sure a new tree
        // based on it would preserve its structure

        QuadTreeClusteringStrategy update = support.newStrategy(originalCollapsedTree);

        Node node1 = support.createNode("node # 1", new Envelope(1, 1, 1, 1));
        Node node2 = support.createNode("node # 2", new Envelope(2, 2, 1, 1));
        Node node3 = support.createNode("node # 3", new Envelope(3, 3, 1, 1));

        // update the DAG with the changed nodes
        assertTrue(update.remove(node1));
        assertTrue(update.remove(node2));
        assertTrue(update.remove(node3));

        RevTree updatedTree = DAGTreeBuilder.build(update, support.store());

        List<Node> node11 = findNode(node1.getName(), updatedTree, support.store());
        List<Node> node12 = findNode(node2.getName(), updatedTree, support.store());
        List<Node> node13 = findNode(node3.getName(), updatedTree, support.store());
        assertEquals(0, node11.size());
        assertEquals(0, node12.size());
        assertEquals(0, node13.size());

        assertEquals(originalCollapsedTree.size() - 3, updatedTree.size());
    }

    /**
     * Build a DAG where all nodes are points that fall on the exact same quad, leading to a DAG of
     * depth CanonicalClusteringStrategy.maxDepth, and since the leaf tree at maxDepth is
     * overloaded, it's split into an unpromotables canonical tree (at bucket index 4), then
     * collapsed at buildRoot()
     */
    @Test
    public void testCollapsedTreeDeletesAsExpectedOnDeepTree() {

        final QuadTreeClusteringStrategy orig = support.newStrategy();
        final int maxDepth = orig.getMaxDepth();
        final int nodeCount = 1 + orig.normalizedSizeLimit();
        List<Node> nodes = support.createNodes(nodeCount, Collections.nCopies(maxDepth, NE));

        support.putNodes(orig, nodes);

        // System.err.println("DAG before buildRoot");
        // print(orig, orig.root);

        assertEquals(nodes.size(), orig.root.getTotalChildCount());
        assertEquals(String.format("DAG depth should be %d + 2", maxDepth), //
                (maxDepth + 2), orig.depth(orig.root));

        orig.buildRoot();
        // System.err.println("DAG after buildRoot");
        // print(orig, orig.root);

        assertEquals(nodes.size(), orig.root.getTotalChildCount());
        assertEquals("DAG should have been collapsed", 1, orig.depth(orig.root));

        RevTree originalCollapsedTree = DAGTreeBuilder.build(orig, support.store());
        assertEquals(1, RevObjectTestSupport.depth(support.store(), originalCollapsedTree));
        assertEquals(orig.root.getTotalChildCount(), originalCollapsedTree.size());

        // we know originalCollapsedTree has been collapsed to be shallower, make sure a new tree
        // based on it would preserve its structure

        QuadTreeClusteringStrategy update = support.newStrategy(originalCollapsedTree);

        Node node1 = nodes.get(0);
        Node node2 = nodes.get(1);
        Node node3 = nodes.get(2);

        // update the DAG with the changed nodes
        // print(update, update.root);
        assertTrue(update.remove(node1));
        // print(update, update.root);
        assertTrue(update.remove(node2));
        assertTrue(update.remove(node3));

        // print(update, update.root);
        // print(update, update.buildRoot());

        RevTree updatedTree = DAGTreeBuilder.build(update, support.store());

        List<Node> node11 = findNode(node1.getName(), updatedTree, support.store());
        List<Node> node12 = findNode(node2.getName(), updatedTree, support.store());
        List<Node> node13 = findNode(node3.getName(), updatedTree, support.store());
        assertEquals(0, node11.size());
        assertEquals(0, node12.size());
        assertEquals(0, node13.size());

        assertEquals(originalCollapsedTree.size() - 3, updatedTree.size());
    }

    /**
     * Make a DAG that has 129 nodes at
     */
    @Test
    public void testCollepsesAndExpandsAsExpectedWithMoreComplexStructure() {

        final QuadTreeClusteringStrategy orig = support.newStrategy();
        final int maxDepth = orig.getMaxDepth();

        final List<Quadrant> level4Path = ImmutableList.of(SW, NE, SW, NW);
        final List<Quadrant> level7Path = ImmutableList.of(SW, NE, SW, NW, SE, SE, NE);
        final List<Quadrant> level11Path = ImmutableList.of(SW, NE, SW, NW, SE, SE, NE, SW, NE, NW,
                SW);

        final int splitSize = 1 + orig.normalizedSizeLimit();
        // print(orig, orig.root);
        List<Node> level11Nodes = support.putNodes(splitSize, orig, level11Path);
        // print(orig, orig.root);
        for (Node n : level11Nodes) {
            NodeId nid = orig.computeId(n);
            List<Quadrant> quadrantsByDepth = orig.quadrantsByDepth(nid, maxDepth);
            assertEquals(level11Path, quadrantsByDepth);
        }

        verify(orig, level4Path, 3 * splitSize);
        verify(orig, level7Path, 2 * splitSize);
        verify(orig, level11Path, splitSize);

        DAG dag = support.findDAG(orig, level4Path);
        assertNotNull(dag);

        // assertEquals(totalCount, orig.root.getTotalChildCount());
        final int expectedDepth = 13;// nodes3.size() + 2;
        assertEquals(String.format("DAG depth should be %d + 2", expectedDepth), //
                expectedDepth, orig.depth(orig.root));

        orig.buildRoot();
    }

    private void verify(QuadTreeClusteringStrategy quadStrategy, List<Quadrant> dagPath,
            int expectedSize) {
        DAG dag = support.findDAG(quadStrategy, dagPath);
        assertNotNull("Expected dag at location " + dagPath, dag);
        assertEquals(expectedSize, dag.getTotalChildCount());
    }

}
