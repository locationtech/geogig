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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.api.plumbing.diff.TreeTestSupport.createFeaturesTree;
import static org.locationtech.geogig.api.plumbing.diff.TreeTestSupport.createTreesTree;
import static org.locationtech.geogig.api.plumbing.diff.TreeTestSupport.featureNode;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabse;
import org.mockito.ArgumentCaptor;

import com.vividsolutions.jts.geom.Envelope;

public class PreOrderDiffWalkTest {

    private ObjectDatabase leftSource;

    private ObjectDatabase rightSource;

    private PreOrderDiffWalk.Consumer consumer;

    @Before
    public void beforeTest() {
        leftSource = new HeapObjectDatabse();
        rightSource = new HeapObjectDatabse();

        leftSource.open();
        rightSource.open();
        consumer = mock(Consumer.class);
    }

    /**
     * Creates a root node for the given tree as the one {@link PreOrderDiffWalk} should use to start
     * the traversal
     */
    private Node nodeFor(RevTree root) {
        Envelope bounds = SpatialOps.boundsOf(root);
        return Node.create(NodeRef.ROOT, root.getId(), ObjectId.NULL, TYPE.TREE, bounds);
    }

    @Test
    public void testSameRootTree() {
        RevTree left = createFeaturesTree(leftSource, "f", 10).build();
        RevTree right = left;
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        visitor.walk(consumer);

        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSameChildTree() {
        RevTree left = createFeaturesTree(leftSource, "f", 10).build();
        RevTree right = left;
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        visitor.walk(consumer);

        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testCallsRootNode() {
        RevTree left = createFeaturesTree(leftSource, "f", 1).build();
        RevTree right = createFeaturesTree(rightSource, "f", 2).build();
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        visitor.walk(consumer);

        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(false);

        final Node lNode = nodeFor(left);
        final Node rNode = nodeFor(right);

        ArgumentCaptor<Node> leftNode = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Node> rightNode = ArgumentCaptor.forClass(Node.class);

        verify(consumer, times(1)).tree(leftNode.capture(), rightNode.capture());

        assertEquals(lNode, leftNode.getValue());
        assertEquals(rNode, rightNode.getValue());

        verify(consumer, times(1)).endTree(leftNode.capture(), rightNode.capture());
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testLeafLeafTwoAdds() {
        // two leaf trees
        RevTree left = createFeaturesTree(leftSource, "f", 3).build();
        RevTree right = createFeaturesTree(rightSource, "f", 5).build();
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        final Node lroot = nodeFor(left);
        final Node rroot = nodeFor(right);

        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);

        visitor.walk(consumer);

        verify(consumer, times(1)).tree(eq(lroot), eq(rroot));

        ArgumentCaptor<Node> larg = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Node> rarg = ArgumentCaptor.forClass(Node.class);

        verify(consumer, times(2)).feature(larg.capture(), rarg.capture());

        assertEquals(2, larg.getAllValues().size());
        assertNull(larg.getAllValues().get(0));
        assertNull(larg.getAllValues().get(1));

        Node n1 = featureNode("f", 3);// the two added nodes
        Node n2 = featureNode("f", 4);
        assertTrue(rarg.getAllValues().contains(n1));
        assertTrue(rarg.getAllValues().contains(n2));

        verify(consumer, times(1)).endTree(eq(lroot), eq(rroot));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testLeafLeafTwoRemoves() {
        // two leaf trees
        RevTree left = createFeaturesTree(leftSource, "f", 5).build();
        RevTree right = createFeaturesTree(rightSource, "f", 3).build();
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        final Node lroot = nodeFor(left);
        final Node rroot = nodeFor(right);

        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);

        visitor.walk(consumer);

        verify(consumer, times(1)).tree(eq(lroot), eq(rroot));

        ArgumentCaptor<Node> larg = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Node> rarg = ArgumentCaptor.forClass(Node.class);

        verify(consumer, times(2)).feature(larg.capture(), rarg.capture());

        assertEquals(2, larg.getAllValues().size());
        assertNull(rarg.getAllValues().get(0));
        assertNull(rarg.getAllValues().get(1));

        Node n1 = featureNode("f", 3);// the two added nodes
        Node n2 = featureNode("f", 4);
        assertTrue(larg.getAllValues().contains(n1));
        assertTrue(larg.getAllValues().contains(n2));

        verify(consumer, times(1)).endTree(eq(lroot), eq(rroot));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testLeafLeafWithSubStrees() {
        // two leaf trees
        ObjectId metadataId = ObjectId.forString("fake");
        RevTree left = createTreesTree(leftSource, 2, 100, metadataId).build();
        RevTree right = createTreesTree(rightSource, 3, 100, metadataId).build();
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        final Node lroot = nodeFor(left);
        final Node rroot = nodeFor(right);

        // consume any tree diff
        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(true);

        visitor.walk(consumer);

        // capture calls to consumer.tree(node, node)
        ArgumentCaptor<Node> larg = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Node> rarg = ArgumentCaptor.forClass(Node.class);

        verify(consumer, times(2)).tree(larg.capture(), rarg.capture());

        assertEquals(2, larg.getAllValues().size());
        assertEquals("left side arg for the first tree() call is not the left root", lroot, larg
                .getAllValues().get(0));
        assertNull("left side arg for the second tree() call should be null", larg.getAllValues()
                .get(1));

        assertEquals(2, rarg.getAllValues().size());
        assertEquals(rroot, rarg.getAllValues().get(0));
        assertNotNull(rarg.getAllValues().get(1));

        verify(consumer, times(100)).feature((Node) isNull(), any(Node.class));

        verify(consumer, times(2)).endTree(any(Node.class), any(Node.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSkipAddedTree() {
        // two leaf trees
        ObjectId metadataId = ObjectId.forString("fake");
        RevTree left = createTreesTree(leftSource, 2, 10, metadataId).build();
        RevTree right = createTreesTree(rightSource, 3, 10, metadataId).build();
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        final Node lroot = nodeFor(left);
        final Node rroot = nodeFor(right);

        // consume the root tree
        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);
        // but skip the added tree
        when(consumer.tree((Node) isNull(), any(Node.class))).thenReturn(false);

        visitor.walk(consumer);

        // one call to tree() for the root tree, and another for the new subtree
        verify(consumer, times(2)).tree(any(Node.class), any(Node.class));

        // but no calls to feature() as we returned false on the second call to tree()
        verify(consumer, times(0)).feature(any(Node.class), any(Node.class));
        verify(consumer, times(2)).endTree(any(Node.class), any(Node.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSkipBucket() {
        // two bucket trees of depth 2
        final int size = RevTree.MAX_BUCKETS * RevTree.NORMALIZED_SIZE_LIMIT;
        RevTree left = createFeaturesTree(leftSource, "f", size).build();
        RevTree right = createFeaturesTree(rightSource, "f", size, 0, true).build();// all features
                                                                                    // changed
        assertDepth(left, leftSource, 2);
        assertDepth(right, rightSource, 2);

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        final Node lroot = nodeFor(left);
        final Node rroot = nodeFor(right);
        // consume the root tree
        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);

        // skip all buckets of depth 0
        when(consumer.bucket(anyInt(), eq(0), any(Bucket.class), any(Bucket.class))).thenReturn(
                false);

        visitor.walk(consumer);

        verify(consumer, times(1)).tree(eq(lroot), eq(rroot));

        verify(consumer, times(32)).bucket(anyInt(), eq(0), any(Bucket.class), any(Bucket.class));

        // should not be any call to consumer.features as we skipped all buckets of depth 0 (which
        // point to leaf trees)
        verify(consumer, times(0)).feature(any(Node.class), any(Node.class));

        verify(consumer, times(32))
                .endBucket(anyInt(), eq(0), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).endTree(eq(lroot), eq(rroot));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSkipRemovedTree() {
        // two leaf trees
        ObjectId metadataId = ObjectId.forString("fake");
        RevTree left = createTreesTree(leftSource, 3, 10, metadataId).build();
        RevTree right = createTreesTree(rightSource, 2, 10, metadataId).build();
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        final Node lroot = nodeFor(left);
        final Node rroot = nodeFor(right);

        // consume the root tree
        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);
        // but skip the removed tree
        when(consumer.tree(any(Node.class), (Node) isNull())).thenReturn(false);

        visitor.walk(consumer);

        // one call to tree() for the root tree, and another for the removed subtree
        verify(consumer, times(2)).tree(any(Node.class), any(Node.class));

        // but no calls to feature() as we returned false on the second call to tree()
        verify(consumer, times(0)).feature(any(Node.class), any(Node.class));
        verify(consumer, times(2)).endTree(any(Node.class), any(Node.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testLeafLeafChanged() {
        // two leaf trees
        final RevTree left;
        final RevTree right;
        final Node nodeChange1 = Node.create("f2", ObjectId.forString("forcechange"),
                ObjectId.NULL, TYPE.FEATURE, null);
        final Node nodeChange2 = Node.create("f3", ObjectId.forString("fakefake"), ObjectId.NULL,
                TYPE.FEATURE, null);
        {
            left = createFeaturesTree(leftSource, "f", 5).build();
            // change two nodes
            RevTreeBuilder builder = createFeaturesTree(rightSource, "f", 5);
            builder.put(nodeChange1);
            builder.put(nodeChange2);

            right = builder.build();
        }
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(true);
        visitor.walk(consumer);
        // call of the root tree nodes
        verify(consumer, times(1)).tree(any(Node.class), any(Node.class));

        ArgumentCaptor<Node> larg = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Node> rarg = ArgumentCaptor.forClass(Node.class);

        verify(consumer, times(2)).feature(larg.capture(), rarg.capture());

        assertEquals(2, larg.getAllValues().size());
        assertEquals(2, rarg.getAllValues().size());

        Node n1 = featureNode("f", 2);// the two added nodes
        Node n2 = featureNode("f", 3);

        assertTrue(larg.getAllValues().contains(n1));
        assertTrue(larg.getAllValues().contains(n2));

        assertTrue(rarg.getAllValues().contains(nodeChange1));
        assertTrue(rarg.getAllValues().contains(nodeChange2));

        verify(consumer, times(1)).endTree(any(Node.class), any(Node.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testBucketBucketFlat() {
        RevTree left = createFeaturesTree(leftSource, "f", RevTree.NORMALIZED_SIZE_LIMIT + 1)
                .build();
        RevTree right = createFeaturesTree(rightSource, "f", RevTree.NORMALIZED_SIZE_LIMIT + 2)
                .build();

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(true);
        when(consumer.bucket(anyInt(), anyInt(), any(Bucket.class), any(Bucket.class))).thenReturn(
                true);

        visitor.walk(consumer);
        verify(consumer, times(1)).tree(any(Node.class), any(Node.class));
        verify(consumer, times(1)).bucket(anyInt(), eq(0), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).feature((Node) isNull(), any(Node.class));

        verify(consumer, times(1)).endTree(any(Node.class), any(Node.class));
        verify(consumer, times(1)).endBucket(anyInt(), eq(0), any(Bucket.class), any(Bucket.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testBucketBucketFlatMoreDepth() {
        RevTree left = createFeaturesTree(leftSource, "f",
                RevTree.MAX_BUCKETS * RevTree.NORMALIZED_SIZE_LIMIT).build();
        RevTree right = createFeaturesTree(rightSource, "f",
                RevTree.MAX_BUCKETS * RevTree.NORMALIZED_SIZE_LIMIT + 1).build();

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(true);
        when(consumer.bucket(anyInt(), anyInt(), any(Bucket.class), any(Bucket.class))).thenReturn(
                true);

        visitor.walk(consumer);
        verify(consumer, times(1)).tree(any(Node.class), any(Node.class));

        // consumer.bucket should be called for depth 0 and then 1
        verify(consumer, times(1)).bucket(anyInt(), eq(0), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).bucket(anyInt(), eq(1), any(Bucket.class), any(Bucket.class));

        verify(consumer, times(1)).feature((Node) isNull(), any(Node.class));

        verify(consumer, times(1)).endBucket(anyInt(), eq(0), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).endBucket(anyInt(), eq(1), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).endTree(any(Node.class), any(Node.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testBucketLeafSimple() {
        final int leftsize = 1 + RevTree.NORMALIZED_SIZE_LIMIT;
        RevTree left = createFeaturesTree(leftSource, "f", leftsize).build();
        RevTree right = createFeaturesTree(rightSource, "f", 1).build();

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        // consume all
        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(true);
        when(consumer.bucket(anyInt(), anyInt(), any(Bucket.class), any(Bucket.class))).thenReturn(
                true);

        visitor.walk(consumer);
        // there's only the root tree
        verify(consumer, times(1)).tree(any(Node.class), any(Node.class));

        // there's only one feature on the right tree, so all right trees features fall on a single
        // bucket
        final int leftBucketCount = left.buckets().get().size();
        final int expectedBucketCalls = leftBucketCount - 1;
        verify(consumer, times(expectedBucketCalls)).bucket(anyInt(), eq(0), any(Bucket.class),
                any(Bucket.class));

        verify(consumer, times(leftsize - 1)).feature(any(Node.class), (Node) isNull());

        verify(consumer, times(expectedBucketCalls)).endBucket(anyInt(), eq(0), any(Bucket.class),
                any(Bucket.class));
        verify(consumer, times(1)).endTree(any(Node.class), any(Node.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testLeafBucketSimple() {
        final int rightsize = 1 + RevTree.NORMALIZED_SIZE_LIMIT;
        RevTree left = createFeaturesTree(leftSource, "f", 1).build();
        RevTree right = createFeaturesTree(rightSource, "f", rightsize).build();

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        // consume all
        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(true);
        when(consumer.bucket(anyInt(), anyInt(), any(Bucket.class), any(Bucket.class))).thenReturn(
                true);

        visitor.walk(consumer);
        // there's only the root tree
        verify(consumer, times(1)).tree(any(Node.class), any(Node.class));

        // there's only one feature on the right tree, so all right trees features fall on a single
        // bucket
        final int leftBucketCount = right.buckets().get().size();
        final int expectedBucketCalls = leftBucketCount - 1;
        verify(consumer, times(expectedBucketCalls)).bucket(anyInt(), eq(0), any(Bucket.class),
                any(Bucket.class));

        verify(consumer, times(rightsize - 1)).feature((Node) isNull(), any(Node.class));

        verify(consumer, times(expectedBucketCalls)).endBucket(anyInt(), eq(0), any(Bucket.class),
                any(Bucket.class));
        verify(consumer, times(1)).endTree(any(Node.class), any(Node.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testBucketLeafOneLevelDepth() {
        final int leftsize = 2 * RevTree.NORMALIZED_SIZE_LIMIT;
        final int rightsize = RevTree.NORMALIZED_SIZE_LIMIT;
        final int overlapCount = 100;

        RevTree left = createFeaturesTree(leftSource, "f", leftsize).build();
        assertDepth(left, leftSource, 1);
        testBucketLeafDeeper(left, rightsize, overlapCount);
    }

    @Test
    public void testBucketLeafTwoLevelsDepth() {
        final int leftsize = RevTree.MAX_BUCKETS * RevTree.NORMALIZED_SIZE_LIMIT;

        RevTree left = createFeaturesTree(leftSource, "f", leftsize).build();
        assertDepth(left, leftSource, 2);

        final int rightsize = RevTree.NORMALIZED_SIZE_LIMIT;
        final int overlapCount = 100;
        testBucketLeafDeeper(left, rightsize, overlapCount);
    }

    // goes OOM with the deafult test heap size, but can be manually run with a bigger one
    @Ignore
    @Test
    public void testBucketLeafThreeLevelsDepth() {
        final int leftsize = RevTree.MAX_BUCKETS * RevTree.MAX_BUCKETS
                * RevTree.NORMALIZED_SIZE_LIMIT;

        RevTree left = createFeaturesTree(leftSource, "f", leftsize).build();
        assertDepth(left, leftSource, 3);

        final int rightsize = RevTree.NORMALIZED_SIZE_LIMIT;
        final int overlapCount = 100;
        testBucketLeafDeeper(left, rightsize, overlapCount);
    }

    private void testBucketLeafDeeper(final RevTree left, final int rightsize,
            final int overlapCount) {

        consumer = mock(Consumer.class);
        // left tree has feature nodes "f0" to "f<leftsize-1>"
        final int leftsize = (int) left.size();
        // the right tree feature node names start at "f<leftsize - 100>", so there's a 100 node
        // overlap
        RevTree right = createFeaturesTree(rightSource, "f", rightsize, leftsize - overlapCount,
                true).build();

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        // consume all
        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(true);
        when(consumer.bucket(anyInt(), anyInt(), any(Bucket.class), any(Bucket.class))).thenReturn(
                true);

        visitor.walk(consumer);

        // there's only the root tree
        verify(consumer, times(1)).tree(any(Node.class), any(Node.class));

        // there shall be <overlapCount> calls to feature with both non null args
        verify(consumer, times(overlapCount)).feature((Node) notNull(), (Node) notNull());

        final int expectedDeletes = leftsize - overlapCount;
        final int expectedAdds = rightsize - overlapCount;
        verify(consumer, times(expectedDeletes)).feature((Node) notNull(), (Node) isNull());
        verify(consumer, times(expectedAdds)).feature((Node) isNull(), (Node) notNull());
    }

    @Test
    public void testLeafBucketOneLevelDepth() {
        final int leftsize = RevTree.NORMALIZED_SIZE_LIMIT;
        final int rightsize = 2 * RevTree.NORMALIZED_SIZE_LIMIT;
        final int overlapCount = 100;

        RevTree right = createFeaturesTree(rightSource, "f", rightsize).build();
        assertDepth(right, rightSource, 1);
        testLeafBucketDeeper(leftsize, right, overlapCount);
    }

    @Test
    public void testLeafBucketTwoLevelsDepth() {
        final int leftsize = RevTree.NORMALIZED_SIZE_LIMIT;
        final int rightsize = RevTree.MAX_BUCKETS * RevTree.NORMALIZED_SIZE_LIMIT;
        final int overlapCount = 100;

        RevTree right = createFeaturesTree(rightSource, "f", rightsize).build();
        assertDepth(right, rightSource, 2);
        testLeafBucketDeeper(leftsize, right, overlapCount);
    }

    private void testLeafBucketDeeper(final int leftsize, final RevTree rightRoot,
            final int overlapCount) {

        consumer = mock(Consumer.class);
        // right tree has feature nodes "f0" to "f<rightsize-1>"
        final int rightsize = (int) rightRoot.size();
        // the left tree feature node names start at "f<rightsize - 100>", so there's a 100 node
        // overlap
        RevTree leftRoot = createFeaturesTree(leftSource, "f", leftsize, rightsize - overlapCount,
                true).build();

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(leftRoot, rightRoot, leftSource, rightSource);

        // consume all
        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(true);
        when(consumer.bucket(anyInt(), anyInt(), any(Bucket.class), any(Bucket.class))).thenReturn(
                true);

        visitor.walk(consumer);

        // there's only the root tree
        verify(consumer, times(1)).tree(any(Node.class), any(Node.class));

        // there shall be <overlapCount> calls to feature with both non null args
        verify(consumer, times(overlapCount)).feature((Node) notNull(), (Node) notNull());

        final int expectedDeletes = leftsize - overlapCount;
        final int expectedAdds = rightsize - overlapCount;
        verify(consumer, times(expectedDeletes)).feature((Node) notNull(), (Node) isNull());
        verify(consumer, times(expectedAdds)).feature((Node) isNull(), (Node) notNull());
    }

    private void assertDepth(RevTree tree, ObjectDatabase source, int expectedDepth) {
        int depth = getTreeDepth(tree, source, 0);
        assertEquals(expectedDepth, depth);
    }

    /**
     *
     */
    private int getTreeDepth(RevTree tree, ObjectDatabase source, final int depth) {
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(tree, RevTree.EMPTY, source, source);
        final AtomicInteger maxDepth = new AtomicInteger();
        visitor.walk(new Consumer() {

            @Override
            public boolean tree(Node left, Node right) {
                return true;
            }

            @Override
            public void feature(Node left, Node right) {
                //
            }

            @Override
            public boolean bucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
                maxDepth.set(Math.max(maxDepth.get(), bucketDepth + 1));// use +1 cause we want the
                                                                        // number of levels, not the
                                                                        // zero-based level index
                return true;
            }

            @Override
            public void endTree(Node left, Node right) {
                // TODO Auto-generated method stub
            }

            @Override
            public void endBucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
                // TODO Auto-generated method stub
            }
        });
        return maxDepth.get();
    }

    @Test
    public void testBucketLeafSeveral() {
        final int leftsize = 1 + RevTree.NORMALIZED_SIZE_LIMIT;
        RevTree left = createFeaturesTree(leftSource, "f", leftsize).build();
        RevTree right = createFeaturesTree(rightSource, "f", 1).build();

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);

        // consume all
        when(consumer.tree(any(Node.class), any(Node.class))).thenReturn(true);
        when(consumer.bucket(anyInt(), anyInt(), any(Bucket.class), any(Bucket.class))).thenReturn(
                true);

        visitor.walk(consumer);
        // there's only the root tree
        verify(consumer, times(1)).tree(any(Node.class), any(Node.class));

        // there's only one feature on the right tree, so all right trees features fall on a single
        // bucket
        final int leftBucketCount = left.buckets().get().size();
        final int expectedBucketCalls = leftBucketCount - 1;
        verify(consumer, times(expectedBucketCalls)).bucket(anyInt(), eq(0), any(Bucket.class),
                any(Bucket.class));

        verify(consumer, times(leftsize - 1)).feature(any(Node.class), (Node) isNull());

        verify(consumer, times(expectedBucketCalls)).endBucket(anyInt(), eq(0), any(Bucket.class),
                any(Bucket.class));
        verify(consumer, times(1)).endTree(any(Node.class), any(Node.class));
        verifyNoMoreInteractions(consumer);
    }
}
