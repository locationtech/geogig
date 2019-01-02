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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.AbstractConsumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.MaxFeatureDiffsLimiter;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;
import org.locationtech.jts.geom.Envelope;
import org.mockito.ArgumentCaptor;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

public class PreOrderDiffWalkTest {

    private ObjectDatabase leftSource;

    private ObjectDatabase rightSource;

    private PreOrderDiffWalk.Consumer consumer;

    @Before
    public void beforeTest() {
        leftSource = new HeapObjectDatabase();
        rightSource = new HeapObjectDatabase();

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
        return NodeRef.createRoot(RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT,
                root.getId(), ObjectId.NULL, TYPE.TREE, bounds, null));
    }

    @Test
    public void testSameRootTree() {
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", 10);
        RevTree right = left;
        PreOrderDiffWalk visitor = newVisitor(left, right);

        visitor.walk(consumer);

        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testSameChildTree() {
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", 10);
        RevTree right = left;
        PreOrderDiffWalk visitor = newVisitor(left, right);

        visitor.walk(consumer);

        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void testCallsRootNode() {
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", 1);
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f", 2);
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
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", 3);
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f", 5);
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
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", 5);
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f", 3);
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
        ObjectId metadataId = RevObjectTestSupport.hashString("fake");
        RevTree left = RevObjectTestSupport.INSTANCE.createTreesTree(leftSource, 2, 100,
                metadataId);
        RevTree right = RevObjectTestSupport.INSTANCE.createTreesTree(rightSource, 3, 100,
                metadataId);
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
        assertEquals("left side arg for the first tree() call is not the left root", lroot,
                larg.getAllValues().get(0));
        assertNull("left side arg for the second tree() call should be null",
                larg.getAllValues().get(1));

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
        ObjectId metadataId = RevObjectTestSupport.hashString("fake");
        RevTree left = RevObjectTestSupport.INSTANCE.createTreesTree(leftSource, 2, 10, metadataId);
        RevTree right = RevObjectTestSupport.INSTANCE.createTreesTree(rightSource, 3, 10,
                metadataId);
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
        final int size = CanonicalNodeNameOrder.maxBucketsForLevel(0)
                * CanonicalNodeNameOrder.normalizedSizeLimit(0);
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", size);
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f", size, 0,
                true);// all features
        // changed
        assertDepth(left, leftSource, 2);
        assertDepth(right, rightSource, 2);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        final NodeRef lroot = nodeFor(left);
        final NodeRef rroot = nodeFor(right);
        // consume the root tree
        when(consumer.tree(eq(lroot), eq(rroot))).thenReturn(true);

        // skip all buckets of depth 0
        when(consumer.bucket(any(NodeRef.class), any(NodeRef.class), argThat(depthMatches(0)),
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
        ObjectId metadataId = RevObjectTestSupport.hashString("fake");
        RevTree left = RevObjectTestSupport.INSTANCE.createTreesTree(leftSource, 3, 10, metadataId);
        RevTree right = RevObjectTestSupport.INSTANCE.createTreesTree(rightSource, 2, 10,
                metadataId);
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
        final Node nodeChange1 = RevObjectFactory.defaultInstance().createNode("f2",
                RevObjectTestSupport.hashString("forcechange"), ObjectId.NULL, TYPE.FEATURE, null,
                null);
        final Node nodeChange2 = RevObjectFactory.defaultInstance().createNode("f3",
                RevObjectTestSupport.hashString("fakefake"), ObjectId.NULL, TYPE.FEATURE, null,
                null);
        {
            left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", 5);
            // change two nodes
            RevTreeBuilder builder = RevObjectTestSupport.INSTANCE
                    .createFeaturesTreeBuilder(rightSource, "f", 5);
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
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f",
                CanonicalNodeNameOrder.normalizedSizeLimit(0) + 1);
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f",
                CanonicalNodeNameOrder.normalizedSizeLimit(0) + 2);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
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
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f",
                CanonicalNodeNameOrder.maxBucketsForLevel(0)
                        * CanonicalNodeNameOrder.normalizedSizeLimit(0));
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f",
                CanonicalNodeNameOrder.maxBucketsForLevel(0)
                        * CanonicalNodeNameOrder.normalizedSizeLimit(0) + 1);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
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
        final int leftsize = 1 + CanonicalNodeNameOrder.normalizedSizeLimit(0);
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", leftsize);
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f", 1);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);
        // there's only the root tree
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // there's only one feature on the right tree, so all right trees features fall on a single
        // bucket
        final int leftBucketCount = left.bucketsSize();
        final int expectedBucketCalls = leftBucketCount;
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
        final int rightsize = 1 + CanonicalNodeNameOrder.normalizedSizeLimit(0);
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", 1);
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f",
                rightsize);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);
        // there's only the root tree
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // there's only one feature on the right tree, so all right trees features fall on a single
        // bucket
        final int leftBucketCount = right.bucketsSize();
        final int expectedBucketCalls = leftBucketCount;
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
        final int leftsize = 2 * CanonicalNodeNameOrder.normalizedSizeLimit(0);
        final int rightsize = CanonicalNodeNameOrder.normalizedSizeLimit(0);
        final int overlapCount = 100;

        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", leftsize);
        assertDepth(left, leftSource, 1);
        testBucketLeafDeeper(left, rightsize, overlapCount);
    }

    @Test
    public void testBucketLeafTwoLevelsDepth() {
        final int leftsize = CanonicalNodeNameOrder.maxBucketsForLevel(0)
                * CanonicalNodeNameOrder.normalizedSizeLimit(0);

        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", leftsize);
        assertDepth(left, leftSource, 2);

        final int rightsize = CanonicalNodeNameOrder.normalizedSizeLimit(0);
        final int overlapCount = 100;
        testBucketLeafDeeper(left, rightsize, overlapCount);
    }

    // goes OOM with the deafult test heap size, but can be manually run with a bigger one
    @Test
    public void testBucketLeafThreeLevelsDepth() {
        final int leftsize = CanonicalNodeNameOrder.maxBucketsForLevel(0)
                * CanonicalNodeNameOrder.maxBucketsForLevel(0)
                * CanonicalNodeNameOrder.normalizedSizeLimit(0);

        RevTree left = RevObjectTestSupport.INSTANCE.createLargeFeaturesTree(leftSource, "f",
                leftsize, 0, false);

        assertDepth(left, leftSource, 3);

        final int rightsize = CanonicalNodeNameOrder.normalizedSizeLimit(0)
                * CanonicalNodeNameOrder.normalizedSizeLimit(0);
        final int overlapCount = 100;

        int runs = 1;
        for (int i = 0; i < runs; i++)
            testBucketLeafPerf(left, rightsize, overlapCount);
    }

    private long testBucketLeafDeeper(final RevTree left, final int rightsize,
            final int overlapCount) {

        consumer = mock(Consumer.class);
        when(consumer.feature(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        // left tree has feature nodes "f0" to "f<leftsize-1>"
        final int leftsize = (int) left.size();
        // the right tree feature node names start at "f<leftsize - 100>", so there's a 100 node
        // overlap
        RevTree right = RevObjectTestSupport.INSTANCE.createLargeFeaturesTree(rightSource, "f",
                rightsize, leftsize - overlapCount, false);
        rightSource.put(right);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
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

    private long testBucketLeafPerf(final RevTree left, final int rightsize,
            final int overlapCount) {

        consumer = new PreOrderDiffWalk.AbstractConsumer() {
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
        RevTree right = RevObjectTestSupport.INSTANCE.createLargeFeaturesTree(rightSource, "f",
                rightsize, leftsize - overlapCount, false);
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
        final int leftsize = CanonicalNodeNameOrder.normalizedSizeLimit(0);
        final int rightsize = 2 * CanonicalNodeNameOrder.normalizedSizeLimit(0);
        final int overlapCount = 0;

        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f",
                rightsize);
        assertDepth(right, rightSource, 1);
        testLeafBucketDeeper(leftsize, right, overlapCount);
    }

    @Test
    public void testLeafBucketTwoLevelsDepth() {
        final int leftsize = CanonicalNodeNameOrder.normalizedSizeLimit(0);
        final int rightsize = CanonicalNodeNameOrder.maxBucketsForLevel(0)
                * CanonicalNodeNameOrder.normalizedSizeLimit(0);
        final int overlapCount = 100;

        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f",
                rightsize);
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
        RevTree leftRoot = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f",
                leftsize, rightsize - overlapCount, true);

        PreOrderDiffWalk visitor = newVisitor(leftRoot, rightRoot);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);

        // there's only the root tree
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // there shall be <overlapCount> calls to feature with both non null args
        final int totalFeatureNotifications = (rightsize + leftsize) - overlapCount;
        verify(consumer, times(totalFeatureNotifications)).feature(any(NodeRef.class),
                any(NodeRef.class));

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

        visitor.walk(new PreOrderDiffWalk.AbstractConsumer() {
            @Override
            public boolean bucket(NodeRef lparent, NodeRef rparent, BucketIndex bucketIndex,
                    Bucket left, Bucket right) {
                int bucketDepth = bucketIndex.depthIndex();
                maxDepth.set(Math.max(maxDepth.get(), bucketDepth + 1));// use +1 cause we want the
                                                                        // number of levels, not the
                                                                        // zero-based level index
                return true;
            }
        });
        return maxDepth.get();
    }

    @Test
    public void testBucketLeafSeveral() {
        final int leftsize = 1 + CanonicalNodeNameOrder.normalizedSizeLimit(0);
        RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f", leftsize);
        RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f", 1);

        PreOrderDiffWalk visitor = newVisitor(left, right);

        // consume all
        when(consumer.tree(any(NodeRef.class), any(NodeRef.class))).thenReturn(true);
        when(consumer.bucket(any(NodeRef.class), any(NodeRef.class), any(BucketIndex.class),
                any(Bucket.class), any(Bucket.class))).thenReturn(true);

        visitor.walk(consumer);
        // there's only the root tree
        verify(consumer, times(1)).tree(any(NodeRef.class), any(NodeRef.class));

        // there's only one feature on the right tree, so all right trees features fall on a single
        // bucket
        final int leftBucketCount = left.bucketsSize();
        final int expectedBucketCalls = leftBucketCount;
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
        final int leftsize = 2 * CanonicalNodeNameOrder.normalizedSizeLimit(0);
        final int rightsize = CanonicalNodeNameOrder.normalizedSizeLimit(0);

        final RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f",
                leftsize);

        final RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f",
                rightsize);

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
        final int rightsize = 10 * CanonicalNodeNameOrder.normalizedSizeLimit(0);

        final RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "f",
                leftsize);

        final RevTree right = RevObjectTestSupport.INSTANCE.createFeaturesTree(rightSource, "f",
                rightsize);

        checkFalseReturnValueOnConsumerFeatureAbortsTraversal(left, right);
    }

    @Test
    public void checkExpectedNotificationOrder() {
        final int size = 100_000;

        final RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(leftSource, "", size);
        ArrayList<NodeRef> leftFeatureNodes = Lists.newArrayList(new DepthTreeIterator("",
                ObjectId.NULL, left, leftSource, Strategy.RECURSIVE_FEATURES_ONLY));

        RevTreeBuilder rightBuilder = CanonicalTreeBuilder.create(rightSource);

        Map<String, Node> rightChanges = new HashMap<>();
        Collections.shuffle(leftFeatureNodes);
        int i = 0;
        for (NodeRef nr : leftFeatureNodes) {
            Node node = nr.getNode();
            if (i++ < 100) {
                // make a change
                node = RevObjectFactory.defaultInstance().createNode(node.getName(),
                        RevObjectTestSupport.hashString("changed-" + i),
                        node.getMetadataId().or(ObjectId.NULL), TYPE.FEATURE, (Envelope) null,
                        null);
                rightChanges.put(node.getName(), node);
            }
            rightBuilder.put(node);
        }

        final RevTree right = rightBuilder.build();
        rightSource.put(right);
        List<String> expectedOrder = new ArrayList<>(rightChanges.keySet());
        Collections.sort(expectedOrder, CanonicalNodeNameOrder.INSTANCE);

        final List<String> actualOrder = new ArrayList<>();

        Consumer c = new AbstractConsumer() {
            @Override
            public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
                actualOrder.add(right.name());
                return true;
            }
        };

        final boolean preserveIterationOrder = true;
        PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, leftSource, rightSource,
                preserveIterationOrder);
        walk.walk(c);

        assertEquals(expectedOrder.size(), actualOrder.size());
        assertEquals(expectedOrder, actualOrder);
    }

    @Test
    public void checkIsPreOrderTraversal() {
        final int size = 30_000;
        final ObjectStore store = this.leftSource;
        // 30k features tree
        final RevTree left = RevObjectTestSupport.INSTANCE.createFeaturesTree(store, "", size);
        // 30k features tree, same than left except all nodes in it's first bucket at level zero are
        // modified
        final RevTree right;
        // the bucket from left who's tree nodes will be modified on "right"
        final Bucket leftBucket = left.getBuckets().iterator().next();
        // the bucket tree from left that's used to create the modified nodes
        final RevTree leftBucketTree;
        {// create "right", same as left, except all nodes in the first bucket are changes
            RevTreeBuilder rightBuilder = CanonicalTreeBuilder.create(store, left);
            // change all features in first bucket
            ObjectId bucketId = leftBucket.getObjectId();
            leftBucketTree = store.getTree(bucketId);
            Iterator<NodeRef> refs = new DepthTreeIterator("", ObjectId.NULL, leftBucketTree, store,
                    Strategy.RECURSIVE_FEATURES_ONLY);
            while (refs.hasNext()) {
                Node node = refs.next().getNode();
                // just some random objectid
                Node changed = node.update(RevObjectTestSupport.hashString(node.toString()));
                rightBuilder.put(changed);
            }
            right = rightBuilder.build();
        }

        final List<Bounded> expectedLeft = preorder(store, left);
        final List<Bounded> expectedRight = preorder(store, right);

        // check a bucket-leaf traversal is done in pre-order
        testIsPreorderTraversal(expectedLeft, left, RevTree.EMPTY, true, false);
        // check a leaf-bucket traversal is done in pre-order
        testIsPreorderTraversal(expectedRight, RevTree.EMPTY, right, false, true);

        // check a bucket-bucket traversal is done in pre-order
        final Bucket rightBucket = right.getBuckets().iterator().next();
        final RevTree rightBucketTree = store.getTree(rightBucket.getObjectId());

        List<Bounded> expectedChanges;
        expectedChanges = preorder(store, leftBucketTree);
        expectedChanges.add(0, leftBucket);
        expectedChanges.add(leftBucket);
        testIsPreorderTraversal(expectedChanges, left, right, true, false);

        expectedChanges = preorder(store, rightBucketTree);
        expectedChanges.add(0, rightBucket);
        expectedChanges.add(rightBucket);
        testIsPreorderTraversal(expectedChanges, left, right, false, true);
    }

    private void testIsPreorderTraversal(List<Bounded> expectedEvents, RevTree left, RevTree right,
            final boolean collectLeft, final boolean collectRight) {

        class Accumulator extends AbstractConsumer {
            final List<Bounded> events = new ArrayList<>();

            @Override
            public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
                if (collectLeft && left != null)
                    events.add(left.getNode());
                if (collectRight && right != null)
                    events.add(right.getNode());
                return true;
            }

            @Override
            public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                    @Nullable Bucket left, @Nullable Bucket right) {
                add(left, right);
                return true;
            }

            @Override
            public void endBucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                    @Nullable Bucket left, @Nullable Bucket right) {
                add(left, right);
            }

            private void add(@Nullable Bounded left, @Nullable Bounded right) {
                if (collectLeft && left != null)
                    events.add(left);
                if (collectRight && right != null)
                    events.add(right);
            }
        }

        final boolean preserveIterationOrder = true;
        PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, leftSource, leftSource,
                preserveIterationOrder);

        Accumulator c = new Accumulator();
        walk.walk(c);

        // System.err.println("expected:");
        // expectedEvents.subList(0, 20).forEach((b) -> System.err.println(b));
        // System.err.println("actual:");
        // c.events.subList(0, 20).forEach((b) -> System.err.println(b));
        //
        assertEquals(expectedEvents.size(), c.events.size());
        for (int i = 0; i < expectedEvents.size(); i++) {
            assertEquals("At index " + i, expectedEvents.get(i), c.events.get(i));
        }
    }

    private List<Bounded> preorder(ObjectStore source, RevTree tree) {
        List<Bounded> res = new ArrayList<>();
        if (tree.bucketsSize() == 0) {
            res.addAll(tree.features());
        } else {
            tree.getBuckets().forEach((b) -> {
                res.add(b);
                RevTree bucketTree = source.getTree(b.getObjectId());
                res.addAll(preorder(source, bucketTree));
                res.add(b);
            });
        }
        return res;
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

    private static final class FeatureCountingConsumer extends PreOrderDiffWalk.AbstractConsumer {

        final AtomicLong count = new AtomicLong();

        @Override
        public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
            count.incrementAndGet();
            return true;
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
