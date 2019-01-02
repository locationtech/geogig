/* Copyright (c) 2015-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.findNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Strings;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public abstract class QuadTreeBuilderTest extends RevTreeBuilderTest {

    private Envelope maxBounds;

    private Random random;

    @Before
    public void before() {
        maxBounds = createMaxBounds();
        random = new Random();
    }

    protected abstract Envelope createMaxBounds();

    @Override
    protected RevTreeBuilder createBuiler() {
        return createBuiler(RevTree.EMPTY);
    }

    @Override
    protected RevTreeBuilder createBuiler(RevTree original) {
        return RevTreeBuilder.quadBuilder(objectStore, objectStore, original, maxBounds);
    }

    @Override
    protected Node createNode(int i) {
        Node node = createRandomSmallRectNode(i);
        return node;
    }

    @Test
    public void testCreatePointsQuadTree() {
        testPoints(0);
        testPoints(1);
        testPoints(128);
        testPoints(129);
        testPoints(1000);
        testPoints(10000);
    }

    private void testPoints(final int size) {
        List<Node> nodes = createPointNodes(nodeRange(size));
        testCreateQuadTree(maxBounds, nodes);
    }

    @Test
    public void testCreateQuadTreeSmallRects() throws IOException {
        testSmallRects(0);
        testSmallRects(1);
        testSmallRects(128);
        testSmallRects(129);
        testSmallRects(1000);
        testSmallRects(10000);
    }

    private void testSmallRects(final int size) {
        List<Node> nodes = createSmallRectNodes(nodeRange(size), maxBounds);
        testCreateQuadTree(maxBounds, nodes);
    }

    @Test
    public void testCreateQuadTreeRandomRects() {
        testRandomRects(0);
        testRandomRects(1);
        testRandomRects(128);
        testRandomRects(129);
        testRandomRects(1000);
    }

    private void testRandomRects(final int size) {
        List<Node> nodes = createRandomRectNodes(nodeRange(size), maxBounds);
        testCreateQuadTree(maxBounds, nodes);
    }

    private RevTree testCreateQuadTree(final Envelope maxBounds, List<Node> nodes) {
        RevTreeBuilder sequentialBuilder = createQuadTree(maxBounds, nodes);

        Collections.shuffle(nodes);

        RevTreeBuilder randomOrderTree = createQuadTree(maxBounds, nodes);

        RevTree revTreeFromSequentialQuadTree = sequentialBuilder.build();
        RevTree revTreeFromRandomQuadTree = randomOrderTree.build();
        assertEquals(revTreeFromSequentialQuadTree, revTreeFromRandomQuadTree);

        Set<Node> expectedNodes = new HashSet<>(nodes);
        Set<Node> actualNodes = getNodes(revTreeFromRandomQuadTree);
        if (!expectedNodes.equals(actualNodes)) {
            SetView<Node> difference = Sets.difference(expectedNodes, actualNodes);
            Assert.fail("Missing: " + difference);
        }
        // print(revTreeFromSequentialQuadTree);
        assertEquals(nodes.size(), revTreeFromSequentialQuadTree.size());

        final int size = nodes.size();
        final RevTree tree = revTreeFromRandomQuadTree;
        if (size == 0) {
            assertEquals(RevTree.EMPTY, tree);
        } else {
            if (size < 129) {
                assertEquals(0, tree.bucketsSize());
                assertFalse(tree.features().isEmpty());
            } else {
                assertNotEquals(0, tree.bucketsSize());
                assertTrue(tree.features().isEmpty());
            }
        }

        return revTreeFromRandomQuadTree;
    }

    private Set<Node> getNodes(RevTree t) {
        Set<Node> nodes = new TreeSet<>();
        if (t.bucketsSize() == 0) {
            nodes.addAll(t.features());
        } else {
            for (Bucket b : t.getBuckets()) {
                RevTree subtree = objectStore.getTree(b.getObjectId());
                nodes.addAll(getNodes(subtree));
            }
        }
        return nodes;
    }

    @Test
    public void diffQuadTreeTest() throws Exception {
        final int ncount = 1000;

        final List<Node> oldNodes = createPointNodes(nodeRange(ncount));
        final List<Node> newNodes = new ArrayList<>(oldNodes);
        final Set<Node> expectedRemoves = new HashSet<>();
        final Map<Node, Node> expectedChanges = new HashMap<>();
        final Set<Node> expectedAdditions = new HashSet<>();

        {
            expectedRemoves.addAll(oldNodes.subList(0, 10));
            newNodes.removeAll(expectedRemoves);

            List<Node> changes = new ArrayList<>(oldNodes.subList(50, 60));
            newNodes.removeAll(changes);

            for (Node n : changes) {
                ObjectId newId = RevObjectTestSupport.hashString(n.toString());
                Envelope newBounds = new Envelope(n.bounds().get());
                newBounds.translate(0.1, 0.1);
                Node c = n.update(newId, newBounds);
                expectedChanges.put(n, c);
                newNodes.add(c);
            }

            List<Integer> newNodeIds = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                newNodeIds.add(ncount + i);
            }
            expectedAdditions.addAll(createPointNodes(newNodeIds));
            newNodes.addAll(expectedAdditions);
        }

        final RevTree oldTree = createQuadTree(maxBounds, oldNodes).build();
        final RevTree newTree = createQuadTree(maxBounds, newNodes).build();

        PreOrderDiffWalk walk = new PreOrderDiffWalk(oldTree, newTree, objectStore, objectStore,
                true);

        Map<String, Node> added = new HashMap<>();
        Map<String, Node> removed = new HashMap<>();
        walk.walk(new PreOrderDiffWalk.AbstractConsumer() {
            @Override
            public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
                if (left != null) {
                    removed.put(left.name(), left.getNode());
                }
                if (right != null) {
                    added.put(right.name(), right.getNode());
                }
                return true;
            }
        });
        // since they're not canonical trees, diff reports adds and removes instead of changes
        Map<Node, Node> changed = new HashMap<>();
        for (String name : new HashSet<>(Sets.union(added.keySet(), removed.keySet()))) {
            if (added.containsKey(name) && removed.containsKey(name)) {
                changed.put(removed.remove(name), added.remove(name));
            }
        }

        assertEquals(expectedAdditions, new HashSet<>(added.values()));
        assertEquals(expectedChanges.size(), changed.size());
        assertEquals(expectedChanges, changed);
        assertEquals(expectedRemoves, new HashSet<>(removed.values()));
    }

    public @Test void testRemove10() throws Exception {
        testRemove(10);
    }

    public @Test void testRemove128() throws Exception {
        testRemove(128);
    }

    public @Test void testRemove129() throws Exception {
        testRemove(129);
    }

    private void testRemove(final int ncount) {
        final Set<Node> nodes = new HashSet<>(createPointNodes(nodeRange(ncount)));

        RevTreeBuilder initialTreeBuilder = createQuadTree(maxBounds, nodes);

        final RevTree tree = initialTreeBuilder.build();

        final Set<Node> removedNodes;

        RevTreeBuilder builder = RevTreeBuilder.quadBuilder(objectStore, objectStore, tree,
                maxBounds);
        // collect some keys to remove
        {
            Set<Node> treeNodes = RevObjectTestSupport.getTreeNodes(tree, objectStore);
            assertEquals(nodes, treeNodes);

            List<Node> list = new ArrayList<>(treeNodes);
            Collections.shuffle(list);
            int removeCount = ncount / 10;
            removedNodes = ImmutableSet.copyOf(list.subList(0, removeCount));
            for (Node node : removedNodes) {
                boolean removed = builder.remove(node);
                assertTrue("Not removed: " + node, removed);
            }
            assertFalse(removedNodes.isEmpty());
        }

        final RevTree result = builder.build();

        Set<Node> resultNodes = getNodes(result);

        SetView<Node> difference = Sets.difference(nodes, resultNodes);
        assertEquals(removedNodes.size(), difference.size());

        assertEquals(removedNodes, difference);
    }

    @Test
    public void testUpdate() throws Exception {
        final int ncount = 1000;
        final Set<Node> origNodes = new HashSet<>(createPointNodes(nodeRange(ncount)));

        final RevTree tree = createQuadTree(maxBounds, origNodes).build();

        RevTreeBuilder builder = RevTreeBuilder.quadBuilder(objectStore, objectStore, tree,
                maxBounds);

        final Set<Node> removedNodes = new HashSet<>();
        final Set<Node> addedNodes = new HashSet<>();
        {
            int i = 0;
            DepthTreeIterator it = new DepthTreeIterator("", ObjectId.NULL, tree, objectStore,
                    Strategy.CHILDREN);
            for (; it.hasNext(); i++) {
                NodeRef entry = it.next();
                if (i % 5 == 0) {
                    Node oldNode = entry.getNode();
                    ObjectId newId = RevObjectTestSupport.hashString(oldNode.toString());
                    Envelope newBounds = new Envelope(oldNode.bounds().get());
                    if (i % 10 == 0) {
                        newBounds.translate(0.1, 0.1);
                    }
                    Node newNode = oldNode.update(newId, newBounds);
                    builder.update(oldNode, newNode);
                    removedNodes.add(oldNode);
                    addedNodes.add(newNode);
                }
            }
            assertFalse(removedNodes.isEmpty());
        }

        final RevTree result = builder.build();

        Set<Node> resultNodes = getNodes(result);

        SetView<Node> removed = Sets.difference(origNodes, resultNodes);
        SetView<Node> added = Sets.difference(resultNodes, origNodes);

        assertEquals(removedNodes.size(), removed.size());
        assertEquals(addedNodes.size(), added.size());

        assertEquals(removedNodes, removed);
        assertEquals(addedNodes, added);
    }

    public @Test void testNullGeometriesGoToRootUnpromotablesTree() {
        int size = 128;
        List<Node> nodes = createPointNodes(nodeRange(size));
        RevTreeBuilder builder = createQuadTree(maxBounds, nodes);

        Node nullEnvNode = createNode(10000, null);
        builder.put(nullEnvNode);
        RevTree tree = builder.build();
        assertNotEquals(0, tree.bucketsSize());
        List<Node> matches = findNode(nullEnvNode.getName(), tree, objectStore);
        assertEquals(1, matches.size());

        Integer unpromotablesBucketIndex = Integer.valueOf(4);
        assertTrue(tree.getBucket(unpromotablesBucketIndex).isPresent());

        RevTree unpromotables = objectStore
                .getTree(tree.getBucket(unpromotablesBucketIndex).get().getObjectId());
        matches = findNode(nullEnvNode.getName(), unpromotables, objectStore);
        assertEquals(1, matches.size());
    }

    private List<Integer> nodeRange(final int ncount) {
        List<Integer> nodeIds = new ArrayList<>(ContiguousSet
                .create(Range.closedOpen(0, ncount), DiscreteDomain.integers()).asList());
        return nodeIds;
    }

    private List<Node> createPointNodes(List<Integer> nodeIds) {

        List<Node> nodes = new ArrayList<Node>(nodeIds.size());

        for (Integer intId : nodeIds) {
            Node node = createRandomPointNode(intId);
            nodes.add(node);
        }

        return nodes;
    }

    private Node createRandomPointNode(Integer intId) {
        final double minX = maxBounds.getMinX();
        final double minY = maxBounds.getMinY();

        double x = minX + maxBounds.getWidth() * random.nextDouble();
        double y = minY + maxBounds.getHeight() * random.nextDouble();

        Envelope bounds = new Envelope(x, x, y, y);

        Node node = createNode(intId, bounds);
        return node;
    }

    private List<Node> createSmallRectNodes(List<Integer> nodeIds, Envelope maxBounds) {

        List<Node> nodes = new ArrayList<Node>(nodeIds.size());

        for (Integer intId : nodeIds) {
            Node node = createRandomSmallRectNode(intId);
            nodes.add(node);
        }
        return nodes;
    }

    private Node createRandomSmallRectNode(int intId) {
        final double minX = maxBounds.getMinX();
        final double minY = maxBounds.getMinY();

        final double x1 = minX + maxBounds.getWidth() * random.nextDouble();
        final double y1 = minY + maxBounds.getHeight() * random.nextDouble();
        final double stepx = (maxBounds.getWidth() / 1000) * random.nextDouble();
        final double stepy = (maxBounds.getHeight() / 1000) * random.nextDouble();

        final double x2 = Math.min(maxBounds.getMaxX(), x1 + stepx);
        final double y2 = Math.min(maxBounds.getMaxY(), y1 + stepy);

        Envelope bounds = new Envelope(x1, x2, y1, y2);

        return createNode(intId, bounds);
    }

    private Node createNode(int intId, final @Nullable Envelope bounds) {
        String nodeName = String.valueOf(intId);
        String sid = Strings.padStart(nodeName, 40, '0');
        ObjectId oid = RevObjectTestSupport.hashString(sid);

        checkState(bounds == null || (!bounds.isNull() && maxBounds.contains(bounds)));

        Node node = RevObjectFactory.defaultInstance().createNode(nodeName, oid, ObjectId.NULL,
                TYPE.FEATURE, bounds, null);

        Envelope nodeBounds = node.bounds().orNull();
        if (bounds != null) {
            checkState(nodeBounds.contains(bounds));
            checkState(maxBounds.contains(nodeBounds));
        }

        return node;
    }

    private List<Node> createRandomRectNodes(List<Integer> nodeIds, Envelope maxBounds) {

        final double minX = maxBounds.getMinX();
        final double minY = maxBounds.getMinY();
        final double maxX = maxBounds.getMaxX();
        final double maxY = maxBounds.getMaxY();
        final double maxWidth = maxBounds.getWidth();
        final double maxHeight = maxBounds.getHeight();

        List<Node> nodes = new ArrayList<Node>(nodeIds.size());

        for (Integer intId : nodeIds) {
            String fid = String.valueOf(intId);
            ObjectId oid = RevObjectTestSupport.hashString(fid);

            double x1 = minX + maxWidth * random.nextDouble();
            double y1 = minY + maxHeight * random.nextDouble();
            double x2 = Math.min(maxX, x1 + (maxWidth / 4) * random.nextDouble());
            double y2 = Math.min(maxY, y1 + (maxHeight / 4) * random.nextDouble());

            Envelope bounds = new Envelope(x1, x2, y1, y2);

            Map<String, Object> extraData = null;
            Node node = RevObjectFactory.defaultInstance().createNode(fid, oid, ObjectId.NULL,
                    TYPE.FEATURE, bounds, extraData);
            nodes.add(node);
        }
        return nodes;
    }

    private RevTreeBuilder createQuadTree(Envelope maxBounds, final Iterable<Node> nodes) {
        return createQuadTree(maxBounds, nodes, this.objectStore);
    }

    private RevTreeBuilder createQuadTree(Envelope maxBounds, final Iterable<Node> nodes,
            final ObjectStore objectStore) {

        RevTreeBuilder qtree = RevTreeBuilder.quadBuilder(objectStore, objectStore, RevTree.EMPTY,
                maxBounds);

        for (Node node : nodes) {
            assertTrue(qtree.put(node));
        }
        return qtree;
    }
}
