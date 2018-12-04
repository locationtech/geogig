/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.diff.PostOrderDiffWalk.Consumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;
import org.locationtech.jts.geom.Envelope;

public class PostOrderDiffWalkTest {

    private ObjectDatabase leftSource;

    private ObjectDatabase rightSource;

    private TestConsumer testConsumer;

    private static class TestConsumer implements Consumer {

        List</* @Nullable */Bounded> orderedLeft = new ArrayList<>();

        List</* @Nullable */Bounded> orderedRight = new ArrayList<>();

        @Override
        public void feature(NodeRef left, NodeRef right) {
            orderedLeft.add(left);
            orderedRight.add(right);
        }

        @Override
        public void tree(NodeRef left, NodeRef right) {
            orderedLeft.add(left);
            orderedRight.add(right);
        }

        @Override
        public void bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                Bucket left, Bucket right) {
            orderedLeft.add(left);
            orderedRight.add(right);
        }
    }

    @Before
    public void beforeTest() {
        leftSource = new HeapObjectDatabase();
        rightSource = new HeapObjectDatabase();

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
        return NodeRef.createRoot(RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT,
                root.getId(), ObjectId.NULL, TYPE.TREE, bounds, null));
    }

    @Test
    public void testSameRootTree() {
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTreeBuilder(leftSource, "f", 10)
                .build();
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
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTreeBuilder(leftSource, "f", 10)
                .build();
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
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTreeBuilder(leftSource, "f", 1)
                .build();
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTreeBuilder(rightSource, "f", 2)
                .build();
        leftSource.put(left);
        rightSource.put(right);
        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        List<? extends Bounded> expectedLeft = newArrayList(null, nodeFor(left));
        List<? extends Bounded> expectedRight = newArrayList(featureNodeRef("f", 1),
                nodeFor(right));

        visitor.walk(testConsumer);
        assertEquals(expectedLeft, testConsumer.orderedLeft);
        assertEquals(expectedRight, testConsumer.orderedRight);
    }

    @Test
    public void testLeafLeafTwoAdds() {
        // two leaf trees
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTreeBuilder(leftSource, "f", 3)
                .build();
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTreeBuilder(rightSource, "f", 5)
                .build();
        leftSource.put(left);
        rightSource.put(right);
        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        List<? extends Bounded> expectedLeft = newArrayList(//
                null, //
                null, //
                nodeFor(left));
        List<? extends Bounded> expectedRight = newArrayList(//
                featureNodeRef("f", 3), //
                featureNodeRef("f", 4), //
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
        ObjectId metadataId = RevObjectTestSupport.hashString("fake");

        RevTree left = RevObjectTestSupport.INSTANCE
                .createTreesTreeBuilder(leftSource, 2, 2, metadataId).build();
        RevTree right = RevObjectTestSupport.INSTANCE
                .createTreesTreeBuilder(rightSource, 3, 2, metadataId).build();
        leftSource.put(left);
        rightSource.put(right);
        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        visitor.walk(testConsumer);

        List<Bounded> leftCalls = testConsumer.orderedLeft;
        List<Bounded> rightCalls = testConsumer.orderedRight;

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
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTreeBuilder(leftSource, "f",
                CanonicalNodeNameOrder.normalizedSizeLimit(0) + 1).build();
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTreeBuilder(rightSource, "f",
                CanonicalNodeNameOrder.normalizedSizeLimit(0) + 2).build();
        leftSource.put(left);
        rightSource.put(right);

        PostOrderDiffWalk visitor = new PostOrderDiffWalk(left, right, leftSource, rightSource);

        visitor.walk(testConsumer);

        List<Bounded> leftCalls = testConsumer.orderedLeft;
        List<Bounded> rightCalls = testConsumer.orderedRight;

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

    /**
     * Checks that a tree split into more than one depth level is fully reported to the postorder
     * consumer
     */
    @Test
    public void testBucketNested() {
        final RevTree origLeft = RevTree.EMPTY;
        final RevTree origRight = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f",
                CanonicalNodeNameOrder.normalizedSizeLimit(0)
                        * CanonicalNodeNameOrder.maxBucketsForLevel(0));

        PostOrderDiffWalk visitor = new PostOrderDiffWalk(origLeft, origRight, leftSource,
                leftSource);

        Consumer copyConsumer = new Consumer() {

            private void copy(Bounded nodeOrBucket) {
                if (nodeOrBucket != null) {
                    ObjectId objectId = nodeOrBucket.getObjectId();
                    if (!RevTree.EMPTY_TREE_ID.equals(objectId)) {
                        RevObject object = leftSource.get(objectId);
                        rightSource.put(object);
                    }
                }
            }

            @Override
            public void tree(@Nullable NodeRef left, @Nullable NodeRef right) {
                copy(left);
                copy(right);
            }

            @Override
            public void feature(@Nullable NodeRef left, @Nullable NodeRef right) {
                // features are not put in the db by the
                // RevObjectTestSupport.INSTANCE.createFeaturesTree() helper function, but
                // we're testing the tree is well reported to the consumer anyway
                // copy(left);
                // copy(right);
            }

            @Override
            public void bucket(@Nullable NodeRef leftParent, @Nullable NodeRef rightParent,
                    BucketIndex bucketIndex, @Nullable Bucket left, @Nullable Bucket right) {
                copy(left);
                copy(right);
            }
        };
        visitor.walk(copyConsumer);

        // make sure all tree objects have been copied over
        walkTree(origRight.getId(), rightSource);
    }

    private void walkTree(ObjectId treeId, ObjectDatabase source) {
        assertTrue(source.exists(treeId));
        RevTree tree = source.getTree(treeId);
        for (Bucket b : tree.getBuckets()) {
            walkTree(b.getObjectId(), source);
        }
    }
}
