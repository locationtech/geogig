/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.api.plumbing.diff.RevObjectTestSupport.createFeaturesTreeBuilder;
import static org.locationtech.geogig.api.plumbing.diff.RevObjectTestSupport.createTreesTreeBuilder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.PostOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabse;

import com.vividsolutions.jts.geom.Envelope;

public class PostOrderDiffWalkTest {

    private ObjectDatabase leftSource;

    private ObjectDatabase rightSource;

    private TestConsumer testConsumer;

    private static class TestConsumer implements Consumer {

        List</* @Nullable */Bounded> orderedLeft = new ArrayList<>();

        List</* @Nullable */Bounded> orderedRight = new ArrayList<>();

        @Override
        public void feature(NodeRef left, NodeRef right) {
            synchronized (System.err) {
                System.err.printf("feature: %s / %s\n", left, right);
            }
            orderedLeft.add(left);
            orderedRight.add(right);
        }

        @Override
        public void tree(NodeRef left, NodeRef right) {
            synchronized (System.err) {
                System.err.printf("tree: %s / %s\n", left, right);
            }
            orderedLeft.add(left);
            orderedRight.add(right);
        }

        @Override
        public void bucket(NodeRef leftParent, NodeRef rightParent, int bucketIndex,
                int bucketDepth, Bucket left, Bucket right) {
            synchronized (System.err) {
                System.err.printf("bucket: %s / %s\n", left, right);
            }
            orderedLeft.add(left);
            orderedRight.add(right);
        }
    }

    @Before
    public void beforeTest() {
        leftSource = new HeapObjectDatabse();
        rightSource = new HeapObjectDatabse();

        leftSource.open();
        rightSource.open();
        testConsumer = new TestConsumer();
    }

    /**
     * Creates a root node for the given tree as the one {@link PostOrderDiffWalk} should use to
     * start the traversal
     */
    private static NodeRef nodeFor(RevTree root) {
        Envelope bounds = SpatialOps.boundsOf(root);
        return NodeRef.createRoot(Node.create(NodeRef.ROOT, root.getId(), ObjectId.NULL, TYPE.TREE,
                bounds));
    }

    @Test
    public void testSameRootTree() {
        RevTree left = createFeaturesTreeBuilder(leftSource, "f", 10).build();
        RevTree right = left;
        leftSource.put(left);
        rightSource.put(right);
        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        Consumer consumer = mock(Consumer.class);

        visitor.walk(consumer);

        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSameChildTree() {
        RevTree left = createFeaturesTreeBuilder(leftSource, "f", 10).build();
        RevTree right = left;
        leftSource.put(left);
        rightSource.put(right);
        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        Consumer consumer = mock(Consumer.class);
        visitor.walk(consumer);

        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSimple() {
        RevTree left = createFeaturesTreeBuilder(leftSource, "f", 1).build();
        RevTree right = createFeaturesTreeBuilder(rightSource, "f", 2).build();
        leftSource.put(left);
        rightSource.put(right);
        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        List<? extends Bounded> expectedLeft = newArrayList(null, nodeFor(left));
        List<? extends Bounded> expectedRight = newArrayList(featureNodeRef("f", 1), nodeFor(right));

        visitor.walk(testConsumer);
        // System.err.println(testConsumer.orderedLeft);
        // System.err.println(testConsumer.orderedRight);
        assertEquals(expectedLeft, testConsumer.orderedLeft);
        assertEquals(expectedRight, testConsumer.orderedRight);
    }

    @Test
    public void testLeafLeafTwoAdds() {
        // two leaf trees
        RevTree left = createFeaturesTreeBuilder(leftSource, "f", 3).build();
        RevTree right = createFeaturesTreeBuilder(rightSource, "f", 5).build();
        leftSource.put(left);
        rightSource.put(right);
        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        List<? extends Bounded> expectedLeft = newArrayList(//
                null,//
                null,//
                nodeFor(left));
        List<? extends Bounded> expectedRight = newArrayList(//
                featureNodeRef("f", 3),//
                featureNodeRef("f", 4),//
                nodeFor(right));

        visitor.walk(testConsumer);

        assertEquals(expectedLeft, testConsumer.orderedLeft);
        assertEquals(expectedRight, testConsumer.orderedRight);
    }

    public static NodeRef featureNodeRef(String namePrefix, int index) {
        Node node = RevObjectTestSupport.featureNode(namePrefix, index, false);
        return NodeRef.create(NodeRef.ROOT, node);
    }

    @Test
    public void testLeafLeafWithSubStrees() {
        // two leaf trees
        ObjectId metadataId = ObjectId.forString("fake");

        RevTree left = createTreesTreeBuilder(leftSource, 2, 2, metadataId).build();
        RevTree right = createTreesTreeBuilder(rightSource, 3, 2, metadataId).build();
        leftSource.put(left);
        rightSource.put(right);
        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        visitor.walk(testConsumer);

        List<Bounded> leftCalls = testConsumer.orderedLeft;
        List<Bounded> rightCalls = testConsumer.orderedRight;

        System.err.println(leftCalls);
        System.err.println(rightCalls);

        NodeRef lroot = nodeFor(left);
        NodeRef rroot = nodeFor(right);

        assertEquals(4, leftCalls.size());
        assertEquals(4, rightCalls.size());

        assertNull(leftCalls.get(0));
        assertNull(leftCalls.get(1));
        assertNull(leftCalls.get(2));
        assertEquals(lroot, leftCalls.get(3));

        assertEquals(rroot, rightCalls.get(3));
        assertNotNull(rightCalls.get(2));
        assertEquals(RevObject.TYPE.TREE, ((NodeRef) rightCalls.get(2)).getType());
        assertEquals(RevObject.TYPE.FEATURE, ((NodeRef) rightCalls.get(1)).getType());
        assertEquals(RevObject.TYPE.FEATURE, ((NodeRef) rightCalls.get(0)).getType());
    }

    @Test
    public void testBucketBucketFlat() {
        RevTree left = createFeaturesTreeBuilder(leftSource, "f", RevTree.NORMALIZED_SIZE_LIMIT + 1)
                .build();
        RevTree right = createFeaturesTreeBuilder(rightSource, "f",
                RevTree.NORMALIZED_SIZE_LIMIT + 2).build();
        leftSource.put(left);
        rightSource.put(right);

        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        visitor.walk(testConsumer);

        List<Bounded> leftCalls = testConsumer.orderedLeft;
        List<Bounded> rightCalls = testConsumer.orderedRight;

        // System.err.println(leftCalls);
        // System.err.println(rightCalls);

        NodeRef lroot = nodeFor(left);
        NodeRef rroot = nodeFor(right);

        assertEquals(3, leftCalls.size());
        assertEquals(3, rightCalls.size());

        assertNull(leftCalls.get(0));
        assertTrue(leftCalls.get(1) instanceof Bucket);
        assertEquals(lroot, leftCalls.get(2));

        assertEquals(rroot, rightCalls.get(2));
        assertTrue(rightCalls.get(1) instanceof Bucket);
        assertTrue(rightCalls.get(0) instanceof NodeRef);
        assertEquals(RevObject.TYPE.FEATURE, ((NodeRef) rightCalls.get(0)).getType());
    }

}
