/* Copyright (c) 2015-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import org.junit.Test;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.QuadTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.model.impl.RevTreeBuilder;
import org.locationtech.geogig.model.impl.RevTreeBuilderTest;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.vividsolutions.jts.geom.Envelope;

public class QuadTreeBuilderTest extends RevTreeBuilderTest {

    @Override
    protected ObjectStore createObjectStore() {
        return super.createObjectStore();
    }

    @Override
    protected RevTreeBuilder createBuiler() {
        return QuadTreeBuilder.quadTree(objectStore);
    }

    @Override
    protected RevTreeBuilder createBuiler(RevTree original) {
        return QuadTreeBuilder.quadTree(objectStore, original);
    }

    @Test
    public void testCreatePointsQuadTree() {
        testPoints(0);
        testPoints(1);
        testPoints(128);
        testPoints(129);
        testPoints(1000);
    }

    private void testPoints(final int size) {
        final Envelope maxBounds = new Envelope(-180, 180, -90, 90);
        List<Node> nodes = createPointNodes(nodeRange(size), maxBounds);
        testCreateQuadTree(maxBounds, nodes);
    }

    @Test
    public void testCreateQuadTreeSmallRects() throws IOException {
        testSmallRects(0);
        testSmallRects(1);
        testSmallRects(128);
        testSmallRects(129);
        testSmallRects(1000);
    }

    private void testSmallRects(final int size) {
        final Envelope maxBounds = new Envelope(-180, 180, -90, 90);
        List<Node> nodes = createSmallRectNodes(nodeRange(size), maxBounds);
        testCreateQuadTree(maxBounds, nodes);
    }

    @Test
    public void testCreateQuadTreeRandomRects() {
        testRandomRects(0);
        testRandomRects(1);
        testRandomRects(128);
        testRandomRects(4 * 128);
    }

    private void testRandomRects(final int size) {
        final Envelope maxBounds = new Envelope(-180, 180, -90, 90);
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
                assertTrue(tree.buckets().isEmpty());
                assertFalse(tree.features().isEmpty());
            } else {
                assertFalse(tree.buckets().isEmpty());
                assertTrue(tree.features().isEmpty());
            }
        }

        return revTreeFromRandomQuadTree;
    }

    private Set<Node> getNodes(RevTree t) {
        Set<Node> nodes = new TreeSet<>();
        if (t.buckets().isEmpty()) {
            nodes.addAll(t.features());
        } else {
            for (Bucket b : t.buckets().values()) {
                RevTree subtree = objectStore.getTree(b.getObjectId());
                nodes.addAll(getNodes(subtree));
            }
        }
        return nodes;
    }

    @Test
    public void diffQuadTreeTest() throws Exception {
        final Envelope maxBounds = new Envelope(-180, 180, -90, 90);
        final int ncount = 1000;

        final List<Node> oldNodes = createPointNodes(nodeRange(ncount), maxBounds);
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
            expectedAdditions.addAll(createPointNodes(newNodeIds, maxBounds));
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

    private List<Integer> nodeRange(final int ncount) {
        List<Integer> nodeIds = new ArrayList<>(ContiguousSet
                .create(Range.closedOpen(0, ncount), DiscreteDomain.integers()).asList());
        return nodeIds;
    }

    private List<Node> createPointNodes(List<Integer> nodeIds, Envelope maxBounds) {

        final double minX = maxBounds.getMinX();
        final double minY = maxBounds.getMinY();

        List<Node> nodes = new ArrayList<Node>(nodeIds.size());

        Random random = new Random();
        for (Integer intId : nodeIds) {
            String nodeName = String.valueOf(intId);
            String sid = "a" + Strings.padStart(nodeName, 39, '0');// avoid creating ObjectId.NULL
            ObjectId oid = ObjectId.valueOf(sid);

            double x = minX + maxBounds.getWidth() * random.nextDouble();
            double y = minY + maxBounds.getHeight() * random.nextDouble();

            Envelope bounds = new Envelope(x, x, y, y);

            Node node = Node.create(nodeName, oid, ObjectId.NULL, TYPE.FEATURE, bounds);
            nodes.add(node);
        }

        return nodes;
    }

    private List<Node> createSmallRectNodes(List<Integer> nodeIds, Envelope maxBounds) {

        final double minX = maxBounds.getMinX();
        final double minY = maxBounds.getMinY();
        final double stepx = maxBounds.getWidth() / nodeIds.size();
        final double stepy = maxBounds.getHeight() / nodeIds.size();

        List<Node> nodes = new ArrayList<Node>(nodeIds.size());

        Random random = new Random();

        for (Integer intId : nodeIds) {
            String nodeName = String.valueOf(intId);
            String sid = Strings.padStart(nodeName, 40, '0');
            ObjectId oid = RevObjectTestSupport.hashString(sid);

            double x1 = Math.min(maxBounds.getMaxX(), minX + (intId * stepx));
            double x2 = Math.min(maxBounds.getMaxX(), minX + (intId * stepx) + stepx);
            double y1 = Math.min(maxBounds.getMaxY(),
                    minY + maxBounds.getHeight() * random.nextDouble());
            double y2 = Math.min(maxBounds.getMaxY(), y1 + stepy);
            Envelope bounds = new Envelope(x1, x2, y1, y2);

            Preconditions.checkState(!bounds.isNull() && maxBounds.contains(bounds));

            Node node = Node.create(nodeName, oid, ObjectId.NULL, TYPE.FEATURE, bounds, null);
            nodes.add(node);
        }
        return nodes;
    }

    private List<Node> createRandomRectNodes(List<Integer> nodeIds, Envelope maxBounds) {

        final double minX = maxBounds.getMinX();
        final double minY = maxBounds.getMinY();
        final double maxX = maxBounds.getMaxX();
        final double maxY = maxBounds.getMaxY();
        final double maxWidth = maxBounds.getWidth();
        final double maxHeight = maxBounds.getHeight();

        Random random = new Random();
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
            Node node = Node.create(fid, oid, ObjectId.NULL, TYPE.FEATURE, bounds, extraData);
            nodes.add(node);
        }
        return nodes;
    }

    private RevTreeBuilder createQuadTree(Envelope maxBounds, final List<Node> nodes) {
        return createQuadTree(maxBounds, nodes, this.objectStore);
    }

    private RevTreeBuilder createQuadTree(Envelope maxBounds, final List<Node> nodes,
            final ObjectStore objectStore) {

        RevTreeBuilder qtree = QuadTreeBuilder.quadTree(objectStore, RevTree.EMPTY, maxBounds);

        for (Node node : nodes) {
            qtree.put(node);
        }
        return qtree;
    }

}
