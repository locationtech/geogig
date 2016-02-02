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
import static org.locationtech.geogig.api.plumbing.diff.RevObjectTestSupport.createFeaturesTree;
import static org.locationtech.geogig.api.plumbing.diff.RevObjectTestSupport.createFeaturesTreeBuilder;
import static org.locationtech.geogig.api.plumbing.diff.RevObjectTestSupport.createLargeFeaturesTree;
import static org.locationtech.geogig.api.plumbing.diff.RevObjectTestSupport.createTreesTree;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.MaxFeatureDiffsLimiter;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.NodePathStorageOrder;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabse;
import org.mockito.ArgumentCaptor;

import com.google.common.base.Stopwatch;
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
        when(consumer.feature(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
    }

    private PreOrderDiffWalk newVisitor(RevTree left, RevTree right) {
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);
        return visitor;
    }

    /**
     * Creates a root node for the given tree as the one {@link PreOrderDiffWalk} should use to
     * start the traversal
     */
    private NodeRef nodeFor(RevTree root) {
        Envelope bounds = SpatialOps.boundsOf(root);
        return NodeRef.createRoot(Node.create(NodeRef.ROOT, root.getId(), ObjectId.NULL, TYPE.TREE,
                bounds));
    }

    @Test
    public void testSameRootTree() {
        RevTree left = createFeaturesTree(leftSource, "f", 10);
        RevTree right = left;
        PreOrderDiffWalk visitor = newVisitor(left, right);

        visitor.walk(consumer);

        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSameChildTree() {
        RevTree left = createFeaturesTree(leftSource, "f", 10);
        RevTree right = left;
        PreOrderDiffWalk visitor = newVisitor(left, right);

        visitor.walk(consumer);

        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testCallsRootNode() {
        RevTree left = createFeaturesTree(leftSource, "f", 1);
        RevTree right = createFeaturesTree(rightSource, "f", 2);
        PreOrderDiffWalk visitor = newVisitor(left, right);

        visitor.walk(consumer);

        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(false);

        final NodeRef lNode = nodeFor(left);
        final NodeRef rNode = nodeFor(right);

        ArgumentCaptor<NodeRef> leftNode = ArgumentCaptor.forClass(NodeRef.class);
        ArgumentCaptor<NodeRef> rightNode = ArgumentCaptor.forClass(NodeRef.class);

        verify(consumer, times(1)).tree(leftNode.capture(), rightNode.capture());

        assertEquals(lNode, leftNode.getValue());
        assertEquals(rNode, rightNode.getValue());

        verify(consumer, times(1)).endTree(leftNode.capture(), rightNode.capture());
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testLeafLeafTwoAdds() {
        // two leaf trees
        RevTree left = createFeaturesTree(leftSource, "f", 3);
        RevTree right = createFeaturesTree(rightSource, "f", 5);
        PreOrderDiffWalk visitor = newVisitor(left, right);

        final NodeRef lroot = nodeFor(left);
        final NodeRef rroot = nodeFor(right);

        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);

        visitor.walk(consumer);

        verify(consumer, times(1)).tree(eq(lroot), eq(rroot));

        ArgumentCaptor<NodeRef> larg = ArgumentCaptor.forClass(NodeRef.class);
        ArgumentCaptor<NodeRef> rarg = ArgumentCaptor.forClass(NodeRef.class);

        verify(consumer, times(2)).feature(larg.capture(), rarg.capture());

        assertEquals(2, larg.getAllValues().size());
        assertNull(larg.getAllValues().get(0));
        assertNull(larg.getAllValues().get(1));

        NodeRef n1 = featureNodeRef("f", 3);// the two added nodes
        NodeRef n2 = featureNodeRef("f", 4);
        assertTrue(rarg.getAllValues().contains(n1));
        assertTrue(rarg.getAllValues().contains(n2));

        verify(consumer, times(1)).endTree(eq(lroot), eq(rroot));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testLeafLeafTwoRemoves() {
        // two leaf trees
        RevTree left = createFeaturesTree(leftSource, "f", 5);
        RevTree right = createFeaturesTree(rightSource, "f", 3);
        PreOrderDiffWalk visitor = newVisitor(left, right);

        final NodeRef lroot = nodeFor(left);
        final NodeRef rroot = nodeFor(right);

        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);

        visitor.walk(consumer);

        verify(consumer, times(1)).tree(eq(lroot), eq(rroot));

        ArgumentCaptor<NodeRef> larg = ArgumentCaptor.forClass(NodeRef.class);
        ArgumentCaptor<NodeRef> rarg = ArgumentCaptor.forClass(NodeRef.class);

        verify(consumer, times(2)).feature(larg.capture(), rarg.capture());

        assertEquals(2, larg.getAllValues().size());
        assertNull(rarg.getAllValues().get(0));
        assertNull(rarg.getAllValues().get(1));

        NodeRef n1 = featureNodeRef("f", 3);// the two added nodes
        NodeRef n2 = featureNodeRef("f", 4);
        assertTrue(larg.getAllValues().contains(n1));
        assertTrue(larg.getAllValues().contains(n2));

        verify(consumer, times(1)).endTree(eq(lroot), eq(rroot));
        verifyNoMoreInteractions(consumer);
    }

    private NodeRef featureNodeRef(String namePrefix, int index) {
        boolean randomIds = false;
        return NodeRef.create(NodeRef.ROOT,
                RevObjectTestSupport.featureNode(namePrefix, index, randomIds));
    }

    @Test
    public void testLeafLeafWithSubStrees() {
        // two leaf trees
        ObjectId metadataId = ObjectId.forString("fake");
        RevTree left = createTreesTree(leftSource, 2, 100, metadataId);
        RevTree right = createTreesTree(rightSource, 3, 100, metadataId);
        PreOrderDiffWalk visitor = newVisitor(left, right);

        final NodeRef lroot = nodeFor(left);
        final NodeRef rroot = nodeFor(right);

        // consume any tree diff
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);

        visitor.walk(consumer);

        // capture calls to consumer.tree(node, node)
        ArgumentCaptor<NodeRef> larg = ArgumentCaptor.forClass(NodeRef.class);
        ArgumentCaptor<NodeRef> rarg = ArgumentCaptor.forClass(NodeRef.class);

        verify(consumer, times(2)).tree(larg.capture(), rarg.capture());

        assertEquals(2, larg.getAllValues().size());
        assertEquals("left side arg for the first tree() call is not the left root", lroot, larg
                .getAllValues().get(0));
        assertNull("left side arg for the second tree() call should be null", larg.getAllValues()
                .get(1));

        assertEquals(2, rarg.getAllValues().size());
        assertEquals(rroot, rarg.getAllValues().get(0));
        assertNotNull(rarg.getAllValues().get(1));

        verify(consumer, times(100)).feature((NodeRef) isNull(), any(NodeRef.class));

        verify(consumer, times(2)).endTree(any(NodeRef.class), any(NodeRef.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSkipAddedTree() {
        // two leaf trees
        ObjectId metadataId = ObjectId.forString("fake");
        RevTree left = createTreesTree(leftSource, 2, 10, metadataId);
        RevTree right = createTreesTree(rightSource, 3, 10, metadataId);
        PreOrderDiffWalk visitor = newVisitor(left, right);

        final NodeRef lroot = nodeFor(left);
        final NodeRef rroot = nodeFor(right);

        // consume the root tree
        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);
        // but skip the added tree
        when(consumer.tree((NodeRef) isNull(), any(NodeRef.class))).thenReturn(false);

        visitor.walk(consumer);

        // one call to tree() for the root tree, and another for the new subtree
        verify(consumer, times(2)).tree(any(NodeRef.class), any(NodeRef.class));

        // but no calls to feature() as we returned false on the second call to tree()
        verify(consumer, times(0)).feature(any(NodeRef.class), any(NodeRef.class));
        verify(consumer, times(2)).endTree(any(NodeRef.class), any(NodeRef.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSkipBucket() {
        // two bucket trees of depth 2
        final int size = NodePathStorageOrder.maxBucketsForLevel(0)
                * NodePathStorageOrder.normalizedSizeLimit(0);
        RevTree left = createFeaturesTree(leftSource, "f", size);
        RevTree right = createFeaturesTree(rightSource, "f", size, 0, true);// all features
                                                                            // changed
        assertDepth(left, leftSource, 2);
        assertDepth(right, rightSource, 2);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        final NodeRef lroot = nodeFor(left);
        final NodeRef rroot = nodeFor(right);
        // consume the root tree
        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);

        // skip all buckets of depth 0
        when(
                consumer.bucket(any(NodeRef.class), any(NodeRef.class), argThat(depthMatches(0)),
                        any(Bucket.class), any(Bucket.class))).thenReturn(false);

        visitor.walk(consumer);

        verify(consumer, times(1)).tree(eq(lroot), eq(rroot));

        verify(consumer, times(32)).bucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));

        // should not be any call to consumer.features as we skipped all buckets of depth 0 (which
        // point to leaf trees)
        verify(consumer, times(0)).feature(any(NodeRef.class), any(NodeRef.class));

        verify(consumer, times(32)).endBucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).endTree(eq(lroot), eq(rroot));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSkipRemovedTree() {
        // two leaf trees
        ObjectId metadataId = ObjectId.forString("fake");
        RevTree left = createTreesTree(leftSource, 3, 10, metadataId);
        RevTree right = createTreesTree(rightSource, 2, 10, metadataId);
        PreOrderDiffWalk visitor = newVisitor(left, right);

        final NodeRef lroot = nodeFor(left);
        final NodeRef rroot = nodeFor(right);

        // consume the root tree
        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);
        // but skip the removed tree
        when(consumer.tree(any(NodeRef.class), (NodeRef) isNull())).thenReturn(false);

        visitor.walk(consumer);

        // one call to tree() for the root tree, and another for the removed subtree
        verify(consumer, times(2)).tree(any(NodeRef.class), any(NodeRef.class));

        // but no calls to feature() as we returned false on the second call to tree()
        verify(consumer, times(0)).feature(any(NodeRef.class), any(NodeRef.class));
        verify(consumer, times(2)).endTree(any(NodeRef.class), any(NodeRef.class));
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
            left = createFeaturesTree(leftSource, "f", 5);
            // change two nodes
            RevTreeBuilder builder = createFeaturesTreeBuilder(rightSource, "f", 5);
            builder.put(nodeChange1);
            builder.put(nodeChange2);

            right = builder.build();
            rightSource.put(right);
        }
        PreOrderDiffWalk visitor = newVisitor(left, right);

        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        visitor.walk(consumer);
        // call of the root tree nodes
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        ArgumentCaptor<NodeRef> larg = ArgumentCaptor.forClass(NodeRef.class);
        ArgumentCaptor<NodeRef> rarg = ArgumentCaptor.forClass(NodeRef.class);

        verify(consumer, times(2)).feature(larg.capture(), rarg.capture());

        assertEquals(2, larg.getAllValues().size());

        List<NodeRef> allValuesAtTheRight = rarg.getAllValues();
        assertEquals(2, allValuesAtTheRight.size());

        NodeRef n1 = featureNodeRef("f", 2);// the two added nodes
        NodeRef n2 = featureNodeRef("f", 3);

        assertTrue(larg.getAllValues().contains(n1));
        assertTrue(larg.getAllValues().contains(n2));

        NodeRef nodeRefChange1 = NodeRef.create(NodeRef.ROOT, nodeChange1);
        NodeRef nodeRefChange2 = NodeRef.create(NodeRef.ROOT, nodeChange2);

        assertTrue(allValuesAtTheRight.contains(nodeRefChange1));
        assertTrue(allValuesAtTheRight.contains(nodeRefChange2));

        verify(consumer, times(1)).endTree(any(NodeRef.class), any(NodeRef.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testBucketBucketFlat() {
        RevTree left = createFeaturesTree(leftSource, "f",
                NodePathStorageOrder.normalizedSizeLimit(0) + 1);
        RevTree right = createFeaturesTree(rightSource, "f",
                NodePathStorageOrder.normalizedSizeLimit(0) + 2);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(
                consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                        any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));
        verify(consumer, times(1)).bucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).feature((NodeRef) isNull(), any(NodeRef.class));

        verify(consumer, times(1)).endTree(any(NodeRef.class), any(NodeRef.class));
        verify(consumer, times(1)).endBucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testBucketBucketFlatMoreDepth() {
        RevTree left = createFeaturesTree(
                leftSource,
                "f",
                NodePathStorageOrder.maxBucketsForLevel(0)
                        * NodePathStorageOrder.normalizedSizeLimit(0));
        RevTree right = createFeaturesTree(
                rightSource,
                "f",
                NodePathStorageOrder.maxBucketsForLevel(0)
                        * NodePathStorageOrder.normalizedSizeLimit(0) + 1);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(
                consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                        any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // consumer.bucket should be called for depth 0 and then 1
        verify(consumer, times(1)).bucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).bucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(1)), any(Bucket.class), any(Bucket.class));

        verify(consumer, times(1)).feature((NodeRef) isNull(), any(NodeRef.class));

        verify(consumer, times(1)).endBucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).endBucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(1)), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).endTree(any(NodeRef.class), any(NodeRef.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testBucketLeafSimple() {
        final int leftsize = 1 + NodePathStorageOrder.normalizedSizeLimit(0);
        RevTree left = createFeaturesTree(leftSource, "f", leftsize);
        RevTree right = createFeaturesTree(rightSource, "f", 1);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(
                consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                        any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);
        // there's only the root tree
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // there's only one feature on the right tree, so all right trees features fall on a single
        // bucket
        final int leftBucketCount = left.buckets().get().size();
        final int expectedBucketCalls = leftBucketCount - 1;
        verify(consumer, times(expectedBucketCalls)).bucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));

        verify(consumer, times(leftsize - 1)).feature(any(NodeRef.class), (NodeRef) isNull());

        verify(consumer, times(expectedBucketCalls)).endBucket(any(NodeRef.class),
                any(NodeRef.class), argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).endTree(any(NodeRef.class), any(NodeRef.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testLeafBucketSimple() {
        final int rightsize = 1 + NodePathStorageOrder.normalizedSizeLimit(0);
        RevTree left = createFeaturesTree(leftSource, "f", 1);
        RevTree right = createFeaturesTree(rightSource, "f", rightsize);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(
                consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                        any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);
        // there's only the root tree
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // there's only one feature on the right tree, so all right trees features fall on a single
        // bucket
        final int leftBucketCount = right.buckets().get().size();
        final int expectedBucketCalls = leftBucketCount - 1;
        verify(consumer, times(expectedBucketCalls)).bucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));

        verify(consumer, times(rightsize - 1)).feature((NodeRef) isNull(), any(NodeRef.class));

        verify(consumer, times(expectedBucketCalls)).endBucket(any(NodeRef.class),
                any(NodeRef.class), argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).endTree(any(NodeRef.class), any(NodeRef.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testBucketLeafOneLevelDepth() {
        final int leftsize = 2 * NodePathStorageOrder.normalizedSizeLimit(0);
        final int rightsize = NodePathStorageOrder.normalizedSizeLimit(0);
        final int overlapCount = 100;

        RevTree left = createFeaturesTree(leftSource, "f", leftsize);
        assertDepth(left, leftSource, 1);
        testBucketLeafDeeper(left, rightsize, overlapCount);
    }

    @Test
    public void testBucketLeafTwoLevelsDepth() {
        final int leftsize = NodePathStorageOrder.maxBucketsForLevel(0)
                * NodePathStorageOrder.normalizedSizeLimit(0);

        RevTree left = createFeaturesTree(leftSource, "f", leftsize);
        assertDepth(left, leftSource, 2);

        final int rightsize = NodePathStorageOrder.normalizedSizeLimit(0);
        final int overlapCount = 100;
        testBucketLeafDeeper(left, rightsize, overlapCount);
    }

    // goes OOM with the deafult test heap size, but can be manually run with a bigger one
    @Test
    public void testBucketLeafThreeLevelsDepth() {
        final int leftsize = NodePathStorageOrder.maxBucketsForLevel(0)
                * NodePathStorageOrder.maxBucketsForLevel(0)
                * NodePathStorageOrder.normalizedSizeLimit(0);

        RevTree left = createLargeFeaturesTree(leftSource, "f", leftsize, 0, false);

        assertDepth(left, leftSource, 3);

        final int rightsize = NodePathStorageOrder.normalizedSizeLimit(0)
                * NodePathStorageOrder.normalizedSizeLimit(0);
        final int overlapCount = 100;

        long totalMillis = 0;
        int runs = 1;
        for (int i = 0; i < runs; i++)
            totalMillis += testBucketLeafPerf(left, rightsize, overlapCount);

        // System.err.printf("Total: %sms, Average: %sms\n", totalMillis, totalMillis / runs);
    }

    private long testBucketLeafDeeper(final RevTree left, final int rightsize,
            final int overlapCount) {

        consumer = mock(Consumer.class);
        when(consumer.feature(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        // left tree has feature nodes "f0" to "f<leftsize-1>"
        final int leftsize = (int) left.size();
        // the right tree feature node names start at "f<leftsize - 100>", so there's a 100 node
        // overlap
        RevTree right = createLargeFeaturesTree(rightSource, "f", rightsize, leftsize
                - overlapCount, false);
        rightSource.put(right);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(
                consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                        any(Bucket.class), any(Bucket.class))).thenReturn(true);

        Stopwatch sw = Stopwatch.createStarted();
        visitor.walk(consumer);
        sw.stop();
        System.err.printf("%s: walked %,d vs %d trees in %s\n", getClass().getSimpleName(),
                left.size(), right.size(), sw);

        // there's only the root tree
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // there shall be <overlapCount> calls to feature with both non null args
        // verify(consumer, times(overlapCount)).feature((Node) notNull(), (Node) notNull());

        final int expectedDeletes = leftsize - overlapCount;
        final int expectedAdds = rightsize - overlapCount;
        verify(consumer, times(expectedDeletes)).feature((NodeRef) notNull(), (NodeRef) isNull());
        verify(consumer, times(expectedAdds)).feature((NodeRef) isNull(), (NodeRef) notNull());
        return sw.elapsed(TimeUnit.MILLISECONDS);
    }

    private long testBucketLeafPerf(final RevTree left, final int rightsize, final int overlapCount) {

        consumer = new Consumer() {

            @Override
            public boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
                return true;
            }

            @Override
            public boolean bucket(NodeRef leftParent, NodeRef rigthParent, BucketIndex bucketIndex,
                    @Nullable Bucket left, @Nullable Bucket right) {

                int bucketDepth = bucketIndex.depthIndex();
                if (bucketDepth > 3) {
                    throw new IllegalStateException();
                }
                return true;
            }

            @Override
            public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
                return true;
            }

            @Override
            public void endTree(@Nullable NodeRef left, @Nullable NodeRef right) {
            }

            @Override
            public void endBucket(NodeRef leftParent, NodeRef rigthParent, BucketIndex bucketIndex,
                    @Nullable Bucket left, @Nullable Bucket right) {
                int bucketDepth = bucketIndex.depthIndex();
                if (bucketDepth > 3) {
                    throw new IllegalStateException();
                }
            }

        };
        // left tree has feature nodes "f0" to "f<leftsize-1>"
        final int leftsize = (int) left.size();
        // the right tree feature node names start at "f<leftsize - 100>", so there's a 100 node
        // overlap
        RevTree right = createLargeFeaturesTree(rightSource, "f", rightsize, leftsize
                - overlapCount, false);
        rightSource.put(right);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        Stopwatch sw = Stopwatch.createStarted();
        visitor.walk(consumer);
        sw.stop();
        System.err.printf("%s: walked %,d vs %d trees in %s\n", getClass().getSimpleName(),
                left.size(), right.size(), sw);

        return sw.elapsed(TimeUnit.MILLISECONDS);
    }

    @Test
    public void testLeafBucketOneLevelDepth() {
        final int leftsize = NodePathStorageOrder.normalizedSizeLimit(0);
        final int rightsize = 2 * NodePathStorageOrder.normalizedSizeLimit(0);
        final int overlapCount = 100;

        RevTree right = createFeaturesTree(rightSource, "f", rightsize);
        assertDepth(right, rightSource, 1);
        testLeafBucketDeeper(leftsize, right, overlapCount);
    }

    @Test
    public void testLeafBucketTwoLevelsDepth() {
        final int leftsize = NodePathStorageOrder.normalizedSizeLimit(0);
        final int rightsize = NodePathStorageOrder.maxBucketsForLevel(0)
                * NodePathStorageOrder.normalizedSizeLimit(0);
        final int overlapCount = 100;

        RevTree right = createFeaturesTree(rightSource, "f", rightsize);
        assertDepth(right, rightSource, 2);
        testLeafBucketDeeper(leftsize, right, overlapCount);
    }

    private void testLeafBucketDeeper(final int leftsize, final RevTree rightRoot,
            final int overlapCount) {

        consumer = mock(Consumer.class);
        when(consumer.feature(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        // right tree has feature nodes "f0" to "f<rightsize-1>"
        final int rightsize = (int) rightRoot.size();
        // the left tree feature node names start at "f<rightsize - 100>", so there's a 100 node
        // overlap
        RevTree leftRoot = createFeaturesTree(leftSource, "f", leftsize, rightsize - overlapCount,
                true);

        PreOrderDiffWalk visitor = newVisitor(leftRoot, rightRoot);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(
                consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                        any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);

        // there's only the root tree
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // there shall be <overlapCount> calls to feature with both non null args
        verify(consumer, times(overlapCount)).feature((NodeRef) notNull(), (NodeRef) notNull());

        final int expectedDeletes = leftsize - overlapCount;
        final int expectedAdds = rightsize - overlapCount;
        verify(consumer, times(expectedDeletes)).feature((NodeRef) notNull(), (NodeRef) isNull());
        verify(consumer, times(expectedAdds)).feature((NodeRef) isNull(), (NodeRef) notNull());
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
            public boolean tree(NodeRef left, NodeRef right) {
                // System.err.printf("tree:  %s, %s\n", left, right);
                return true;
            }

            @Override
            public boolean feature(NodeRef left, NodeRef right) {
                return true;
            }

            @Override
            public boolean bucket(NodeRef lparent, NodeRef rparent, BucketIndex bucketIndex,
                    Bucket left, Bucket right) {
                int bucketDepth = bucketIndex.depthIndex();
                // System.err.printf("bucket:  %s, %s, %d, %d, %s %s\n", lparent, rparent,
                // bucketIndex, bucketDepth, left, right);
                // System.err.printf("bucket: index: %d, depth: %d, %s %s\n", bucketIndex,
                // bucketDepth, left, right);
                // System.err.printf("bucket %s, depth: %d\n", left.getObjectId(), bucketDepth);
                maxDepth.set(Math.max(maxDepth.get(), bucketDepth + 1));// use +1 cause we want the
                                                                        // number of levels, not the
                                                                        // zero-based level index
                return true;
            }

            @Override
            public void endTree(NodeRef left, NodeRef right) {
                // System.err.printf("end tree:  %s, %s\n", left, right);
            }

            @Override
            public void endBucket(NodeRef lparent, NodeRef rparent, BucketIndex bucketIndex,
                    Bucket left, Bucket right) {
                // System.err.printf("end bucket: index: %d, depth: %d, %s %s\n", bucketIndex,
                // bucketDepth, left, right);
            }
        });
        return maxDepth.get();
    }

    @Test
    public void testBucketLeafSeveral() {
        final int leftsize = 1 + NodePathStorageOrder.normalizedSizeLimit(0);
        RevTree left = createFeaturesTree(leftSource, "f", leftsize);
        RevTree right = createFeaturesTree(rightSource, "f", 1);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(
                consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                        any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);
        // there's only the root tree
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // there's only one feature on the right tree, so all right trees features fall on a single
        // bucket
        final int leftBucketCount = left.buckets().get().size();
        final int expectedBucketCalls = leftBucketCount - 1;
        verify(consumer, times(expectedBucketCalls)).bucket(any(NodeRef.class), any(NodeRef.class),
                argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));

        verify(consumer, times(leftsize - 1)).feature(any(NodeRef.class), (NodeRef) isNull());

        verify(consumer, times(expectedBucketCalls)).endBucket(any(NodeRef.class),
                any(NodeRef.class), argThat(depthMatches(0)), any(Bucket.class), any(Bucket.class));
        verify(consumer, times(1)).endTree(any(NodeRef.class), any(NodeRef.class));
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testMaxFeatureDiffsFilter() {
        final int leftsize = 2 * NodePathStorageOrder.normalizedSizeLimit(0);
        final int rightsize = NodePathStorageOrder.normalizedSizeLimit(0);

        final RevTree left = createFeaturesTree(leftSource, "f", leftsize);

        final RevTree right = createFeaturesTree(rightSource, "f", rightsize);

        FeatureCountingConsumer counter = new FeatureCountingConsumer();
        PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, leftSource, rightSource);
        walk.walk(counter);
        final int totalDiffCount = leftsize - rightsize;
        assertEquals(totalDiffCount, counter.count.intValue());

        counter = new FeatureCountingConsumer();
        MaxFeatureDiffsLimiter limiter = new PreOrderDiffWalk.MaxFeatureDiffsLimiter(counter, 10);

        new PreOrderDiffWalk(left, right, leftSource, rightSource).walk(limiter);
        assertEquals(10, counter.count.intValue());
    }

    @Test
    public void testFalseReturnValueOnConsumerFeatureAbortsTraversal() {

        final int leftsize = 100;// RevTree.NORMALIZED_SIZE_LIMIT;
        final int rightsize = 10 * NodePathStorageOrder.normalizedSizeLimit(0);

        final RevTree left = createFeaturesTree(leftSource, "f", leftsize);

        final RevTree right = createFeaturesTree(rightSource, "f", rightsize);

        checkFalseReturnValueOnConsumerFeatureAbortsTraversal(left, right);
    }

    public void checkFalseReturnValueOnConsumerFeatureAbortsTraversal(RevTree left, RevTree right) {
        final long leftsize = left.size();
        final long rightsize = right.size();
        // sanity check
        {
            FeatureCountingConsumer counter = new FeatureCountingConsumer();
            PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, leftSource, rightSource);
            walk.walk(counter);
            final long totalDiffCount = Math.abs(rightsize - leftsize);
            assertEquals(totalDiffCount, counter.count.intValue());
        }

        FeatureCountingConsumer counter = new FeatureCountingConsumer();
        consumer = new PreOrderDiffWalk.MaxFeatureDiffsLimiter(counter, 3);

        PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, leftSource, rightSource);
        walk.walk(consumer);
        final int abortedAtCount = counter.count.intValue();
        assertEquals(3, abortedAtCount);
    }

    private static final class FeatureCountingConsumer implements Consumer {

        final AtomicLong count = new AtomicLong();

        @Override
        public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
            count.incrementAndGet();
            return true;
        }

        @Override
        public boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
            return true;
        }

        @Override
        public void endTree(@Nullable NodeRef left, @Nullable NodeRef right) {
        }

        @Override
        public boolean bucket(final NodeRef leftParent, final NodeRef rightParent,
                final BucketIndex bucketIndex, @Nullable final Bucket left,
                @Nullable final Bucket right) {
            return true;
        }

        @Override
        public void endBucket(NodeRef leftParent, NodeRef rightParent,
                final BucketIndex bucketIndex, @Nullable final Bucket left,
                @Nullable final Bucket right) {
        }

    }

    private static Matcher<BucketIndex> depthMatches(final int expectedDepth) {
        return new CustomMatcher<BucketIndex>("<Bucket depth equals> " + expectedDepth) {
            @Override
            public boolean matches(Object item) {
                int bucketDepth = ((BucketIndex) item).depthIndex();
                return bucketDepth == expectedDepth;
            }
        };
    }
}
