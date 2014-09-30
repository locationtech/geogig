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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Maps.uniqueIndex;
import static com.google.common.collect.Sets.newTreeSet;
import static com.google.common.collect.Sets.union;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.NodeStorageOrder;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Provides a means to "walk" the differences between two {@link RevTree trees} in in-order order
 * and emit diff events to a {@link Consumer}, which can choose to skip parts of the walk when it
 * had collected enough information for its purpose and don't need to go further down a given pair
 * of trees (either named or bucket).
 */
@ParametersAreNonnullByDefault
public class PreOrderDiffWalk {

    private static final NodeStorageOrder ORDER = new NodeStorageOrder();

    private final RevTree left;

    private final RevTree right;

    private final ObjectDatabase leftSource;

    private final ObjectDatabase rightSource;

    public PreOrderDiffWalk(RevTree left, RevTree right, ObjectDatabase leftSource,
            ObjectDatabase rightSource) {

        checkNotNull(left, "left");
        checkNotNull(right, "right");
        checkNotNull(leftSource, "leftSource");
        checkNotNull(rightSource, "rightSource");

        this.left = left;
        this.right = right;
        this.leftSource = leftSource;
        this.rightSource = rightSource;
    }

    /**
     * Walk up the differences between the two trees and emit events to the {@code consumer}.
     * <p>
     * If the two root trees are not equal, an initial call to {@link Consumer#tree(Node, Node)}
     * will be made where the nodes will have {@link NodeRef#ROOT the root name} (i.e. empty
     * string), and provided the consumer indicates to continue with the traversal, further calls to
     * {@link Consumer#feature}, {@link Consumer#tree}, and/or {@link Consumer#bucket} will be made
     * as changes between the two trees are found.
     * <p>
     * At any time, if {@link Consumer#tree} or {@link Consumer#bucket} returns {@code false}, that
     * pair of trees won't be further evaluated and the traversal continues with their siblings or
     * parents if there are no more siblings.
     * <p>
     * Note the {@code consumer} is only notified of nodes or buckets that differ, using
     * {@code null} of either the left of right argument to indicate there's no matching object at
     * the left or right side of the comparison. Left side nulls indicate a new object, right side
     * nulls a deleted one. None of the {@code Consumer} method is ever called with equal left and
     * right arguments.
     * 
     * @param consumer the callback object that gets notified of changes between the two trees and
     *        can abort the walk for whole subtrees.
     */
    public final void walk(Consumer consumer) {

        if (left.equals(right)) {
            return;
        }
        // start by asking the consumer if go on with the walk at all with the
        // root nodes
        Envelope lbounds = SpatialOps.boundsOf(left);
        Node lnode = Node.create(NodeRef.ROOT, left.getId(), ObjectId.NULL, TYPE.TREE, lbounds);

        Envelope rbounds = SpatialOps.boundsOf(right);
        Node rnode = Node.create(NodeRef.ROOT, right.getId(), ObjectId.NULL, TYPE.TREE, rbounds);

        if (consumer.tree(lnode, rnode)) {
            traverseTree(consumer, left, right, 0);
        }
        consumer.endTree(lnode, rnode);
    }

    /**
     * When this method is called its guaranteed that either {@link Consumer#tree} returned
     * {@code true} (i.e. its a pair of trees pointed out by a Node), or {@link Consumer#bucket}
     * returned {@code true} (i.e. they are trees pointed out by buckets).
     * 
     * @param consumer the callback object
     * @param left the tree at the left side of the comparisson
     * @param right the tree at the right side of the comparisson
     * @param bucketDepth the depth of bucket traversal (only non zero if comparing two bucket
     *        trees, as when called from {@link #handleBucketBucket})
     * @precondition {@code left != null && right != null}
     */
    private void traverseTree(Consumer consumer, RevTree left, RevTree right, int bucketDepth) {
        checkArgument(left != null && right != null);
        if (Objects.equal(left, right)) {
            return;
        }
        // Possible cases:
        // 1- left and right are leaf trees
        // 2- left and right are bucket trees
        // 3- left is leaf and right is bucketed
        // 4- left is bucketed and right is leaf
        final boolean leftIsLeaf = !left.buckets().isPresent();
        final boolean rightIsLeaf = !right.buckets().isPresent();
        Iterator<Node> leftc = leftIsLeaf ? left.children() : null;
        Iterator<Node> rightc = rightIsLeaf ? right.children() : null;
        if (leftIsLeaf && rightIsLeaf) {// 1-
            traverseLeafLeaf(consumer, leftc, rightc);
        } else if (!(leftIsLeaf || rightIsLeaf)) {// 2-
            traverseBucketBucket(consumer, left, right, bucketDepth);
        } else if (leftIsLeaf) {// 3-
            traverseLeafBucket(consumer, leftc, right, bucketDepth);
        } else {// 4-
            traverseBucketLeaf(consumer, left, rightc, bucketDepth);
        }
    }

    /**
     * Traverse and compare the {@link RevTree#children() children} nodes of two leaf trees, calling
     * {@link #node(Consumer, Node, Node)} for each diff.
     */
    private void traverseLeafLeaf(Consumer consumer, Iterator<Node> leftc, Iterator<Node> rightc) {
        PeekingIterator<Node> li = Iterators.peekingIterator(leftc);
        PeekingIterator<Node> ri = Iterators.peekingIterator(rightc);

        while (li.hasNext() && ri.hasNext()) {
            Node lpeek = li.peek();
            Node rpeek = ri.peek();
            int order = ORDER.compare(lpeek, rpeek);
            if (order < 0) {
                node(consumer, li.next(), null);// removal
            } else if (order == 0) {// change
                // same feature at both sides of the traversal, consume them and check if its
                // changed it or not
                Node l = li.next();
                Node r = ri.next();
                if (!l.equals(r)) {
                    node(consumer, l, r);
                }
            } else {
                node(consumer, null, ri.next());// addition
            }
        }

        checkState(!li.hasNext() || !ri.hasNext(),
                "either the left or the right iterator should have been fully consumed");

        // right fully consumed, any remaining node in left is a removal
        while (li.hasNext()) {
            node(consumer, li.next(), null);
        }

        // left fully consumed, any remaining node in right is an add
        while (ri.hasNext()) {
            node(consumer, null, ri.next());
        }
    }

    /**
     * Called when found a difference between two nodes. It can be a removal ({@code right} is
     * null), an added node ({@code left} is null}, or a modified feature/tree (neither is null);
     * but {@code left} and {@code right} can never be equal.
     * <p>
     * Depending on the type of node, this method will call {@link Consumer#tree} or
     * {@link Consumer#feature}, and continue the traversal down the trees in case it was a tree and
     * {@link Consumer#tree} returned null.
     */
    private void node(Consumer consumer, @Nullable final Node left, @Nullable final Node right) {
        checkState(left != null || right != null, "both nodes can't be null");
        checkArgument(!Objects.equal(left, right));

        final TYPE type = left == null ? right.getType() : left.getType();

        if (TYPE.FEATURE.equals(type)) {
            consumer.feature(left, right);
        } else {
            checkState(TYPE.TREE.equals(type));
            if (consumer.tree(left, right)) {
                RevTree leftTree;
                RevTree rightTree;
                leftTree = left == null ? RevTree.EMPTY : leftSource.getTree(left.getObjectId());
                rightTree = right == null ? RevTree.EMPTY : rightSource
                        .getTree(right.getObjectId());
                traverseTree(consumer, leftTree, rightTree, 0);
            }
            consumer.endTree(left, right);
        }
    }

    /**
     * Compares a bucket tree (i.e. its size is greater than {@link RevTree#NORMALIZED_SIZE_LIMIT}
     * and hence has been split into buckets) at the left side of the comparison, and a the
     * {@link RevTree#children() children} nodes of a leaf tree at the right side of the comparison.
     * <p>
     * This happens when the left tree is much larger than the right tree
     * <p>
     * This traversal is symmetric to {@link #traverseLeafBucket} so be careful that any change made
     * to this method shall have a matching change at {@link #traverseLeafBucket}
     * 
     * @precondition {@code left.buckets().isPresent()}
     */
    private void traverseBucketLeaf(final Consumer consumer, final RevTree left,
            final Iterator<Node> right, final int bucketDepth) {

        checkState(left.buckets().isPresent());
        final SortedMap<Integer, Bucket> leftBuckets = left.buckets().get();
        final ListMultimap<Integer, Node> nodesByBucket = splitNodesToBucketsAtDepth(right,
                bucketDepth);

        final SortedSet<Integer> bucketIndexes = Sets.newTreeSet(Sets.union(leftBuckets.keySet(),
                nodesByBucket.keySet()));

        // get all buckets at once, to leverage ObjectDatabase optimizations
        final Map<ObjectId, RevObject> bucketTrees;
        bucketTrees = uniqueIndex(leftSource.getAll(transform(leftBuckets.values(), BUCKET_ID)),
                OBJECT_ID);

        for (Integer bucketIndex : bucketIndexes) {
            Bucket leftBucket = leftBuckets.get(bucketIndex);
            List<Node> rightNodes = nodesByBucket.get(bucketIndex);// never returns null, but empty
            if (null == leftBucket) {
                traverseLeafLeaf(consumer, Iterators.<Node> emptyIterator(), rightNodes.iterator());
            } else if (rightNodes.isEmpty()) {
                if (consumer.bucket(bucketIndex, bucketDepth, leftBucket, null)) {
                    RevTree leftTree = (RevTree) bucketTrees.get(leftBucket.id());
                    // traverseBucketBucket(consumer, leftTree, RevTree.EMPTY, bucketDepth);
                    traverseTree(consumer, leftTree, RevTree.EMPTY, bucketDepth + 1);
                }
                consumer.endBucket(bucketIndex, bucketDepth, leftBucket, null);
            } else {
                RevTree leftTree = (RevTree) bucketTrees.get(leftBucket.id());
                if (leftTree.buckets().isPresent()) {
                    traverseBucketLeaf(consumer, leftTree, rightNodes.iterator(), bucketDepth + 1);
                } else {
                    traverseLeafLeaf(consumer, leftTree.children(), rightNodes.iterator());
                }
            }
        }
    }

    private static final Function<Bucket, ObjectId> BUCKET_ID = new Function<Bucket, ObjectId>() {
        @Override
        public ObjectId apply(Bucket b) {
            return b.id();
        }
    };

    private static final Function<RevObject, ObjectId> OBJECT_ID = new Function<RevObject, ObjectId>() {
        @Override
        public ObjectId apply(RevObject o) {
            return o.getId();
        }
    };

    /**
     * Compares a bucket tree (i.e. its size is greater than {@link RevTree#NORMALIZED_SIZE_LIMIT}
     * and hence has been split into buckets) at the right side of the comparison, and a the
     * {@link RevTree#children() children} nodes of a leaf tree at the left side of the comparison.
     * <p>
     * This happens when the right tree is much larger than the left tree
     * <p>
     * This traversal is symmetric to {@link #traverseBucketLeaf} so be careful that any change made
     * to this method shall have a matching change at {@link #traverseBucketLeaf}
     * 
     * @precondition {@code right.buckets().isPresent()}
     */
    private void traverseLeafBucket(final Consumer consumer, final Iterator<Node> left,
            final RevTree right, final int bucketDepth) {

        checkState(right.buckets().isPresent());
        final SortedMap<Integer, Bucket> rightBuckets = right.buckets().get();
        final ListMultimap<Integer, Node> nodesByBucket = splitNodesToBucketsAtDepth(left,
                bucketDepth);

        final SortedSet<Integer> bucketIndexes = Sets.newTreeSet(Sets.union(rightBuckets.keySet(),
                nodesByBucket.keySet()));

        // get all buckets at once, to leverage ObjectDatabase optimizations
        final Map<ObjectId, RevObject> bucketTrees;
        bucketTrees = uniqueIndex(rightSource.getAll(transform(rightBuckets.values(), BUCKET_ID)),
                OBJECT_ID);

        for (Integer bucketIndex : bucketIndexes) {
            Bucket rightBucket = rightBuckets.get(bucketIndex);
            List<Node> leftNodes = nodesByBucket.get(bucketIndex);// never returns null, but empty
            if (null == rightBucket) {
                traverseLeafLeaf(consumer, leftNodes.iterator(), Iterators.<Node> emptyIterator());
            } else if (leftNodes.isEmpty()) {
                if (consumer.bucket(bucketIndex, bucketDepth, null, rightBucket)) {
                    RevTree rightTree = (RevTree) bucketTrees.get(rightBucket.id());
                    // traverseBucketBucket(consumer, RevTree.EMPTY, rightTree, bucketDepth);
                    traverseTree(consumer, RevTree.EMPTY, rightTree, bucketDepth + 1);
                }
                consumer.endBucket(bucketIndex, bucketDepth, null, rightBucket);
            } else {
                RevTree rightTree = (RevTree) bucketTrees.get(rightBucket.id());
                if (rightTree.buckets().isPresent()) {
                    traverseLeafBucket(consumer, leftNodes.iterator(), rightTree, bucketDepth + 1);
                } else {
                    traverseLeafLeaf(consumer, leftNodes.iterator(), rightTree.children());
                }
            }
        }
    }

    /**
     * Split the given nodes into lists keyed by the bucket indes they would belong if they were
     * part of a tree bucket at the given {@code bucketDepth}
     */
    private ListMultimap<Integer, Node> splitNodesToBucketsAtDepth(Iterator<Node> nodes,
            final int bucketDepth) {

        Function<Node, Integer> keyFunction = new Function<Node, Integer>() {
            @Override
            public Integer apply(Node node) {
                return ORDER.bucket(node, bucketDepth);
            }
        };
        ListMultimap<Integer, Node> nodesByBucket = Multimaps.index(nodes, keyFunction);
        return nodesByBucket;
    }

    /**
     * Traverse two bucket trees and notify their differences to the {@code consumer}.
     * <p>
     * If this method is called than its guaranteed that the two bucket trees are note equal (one of
     * them may be empty though), and that {@link Consumer#bucket} returned {@code true}
     * <p>
     * For each bucket index present in the joint set of the two trees buckets,
     * {@link #traverseTree(Consumer, RevTree, RevTree, int)} will be called for the bucket trees
     * that are not equal with {@code bucketDepth} incremented by one.
     * 
     * @param consumer the callback object to receive diff events from the comparison of the two
     *        trees
     * @param left the bucket tree at the left side of the comparison
     * @param right the bucket tree at the right side of the comparison
     * @param bucketDepth the current depth at which the comparison is evaluating these two bucket
     *        trees
     * @see #traverseTree(Consumer, RevTree, RevTree, int)
     * @precondition {@code !left.equals(right)}
     * @precondition {@code left.isEmpty() || left.buckets().isPresent()}
     * @precondition {@code right.isEmpty() || right.buckets().isPresent()}
     */
    private void traverseBucketBucket(Consumer consumer, final RevTree left, final RevTree right,
            final int bucketDepth) {
        checkState(left.isEmpty() || left.buckets().isPresent());
        checkState(right.isEmpty() || right.buckets().isPresent());

        ImmutableSortedMap<Integer, Bucket> lb = left.buckets().get();
        ImmutableSortedMap<Integer, Bucket> rb = right.buckets().get();
        TreeSet<Integer> availableIndexes = newTreeSet(union(lb.keySet(), rb.keySet()));

        @Nullable
        Bucket lbucket;
        @Nullable
        Bucket rbucket;
        for (Integer index : availableIndexes) {
            lbucket = lb.get(index);
            rbucket = rb.get(index);
            if (Objects.equal(lbucket, rbucket)) {
                continue;
            }
            if (consumer.bucket(index.intValue(), bucketDepth, lbucket, rbucket)) {
                RevTree ltree = lbucket == null ? RevTree.EMPTY : leftSource.getTree(lbucket.id());
                RevTree rtree = rbucket == null ? RevTree.EMPTY : rightSource.getTree(rbucket.id());
                traverseTree(consumer, ltree, rtree, bucketDepth + 1);
            }
            consumer.endBucket(index.intValue(), bucketDepth, lbucket, rbucket);
        }
    }

    /**
     * Defines an interface to consume the events emitted by a diff-tree "depth first" traversal,
     * with the ability to be notified of changes to feature and tree nodes, as well as to buckets,
     * and to skip the further traversal of whole trees (either as pointed out by "name tree" nodes,
     * or internal tree buckets).
     * <p>
     * This is especially useful when there's no need to traverse the whole diff to compute the
     * desired result, as it can be the case of counting changes between two trees where one side of
     * the comparison does not have such tree, or the spatial bounds of the difference between two
     * trees on the same case.
     * <p>
     * The first call will always be to {@link #tree(Node, Node)} with the root tree nodes, or there
     * may be no call to any method at all if the two tree nodes are equal.
     * <p>
     * This also allows to parallelize some computations where there's no need to have the output of
     * the tree comparison in "prescribed storage order" as defined by {@link NodeStorageOrder}.
     */
    public static interface Consumer {

        /**
         * Called when either two leaf trees are being compared and a feature node have changed (i.e
         * neither {@code left} nor {@code right} is null, or a feature has been deleted (
         * {@code left} is null), or added ({@code right} is null).
         * 
         * @param left the feature node at the left side of the traversal; may be {@code null) in
         *        which case {@code right} has been added.
         * @param right the feature node at the right side of the traversal; may be {@code null} in
         *        which case {@code left} has been removed.
         * @precondition {@code left != null || right != null}
         * @precondition {@code if(left != null && right != null) then left.name() == right.name()}
         */
        public abstract void feature(@Nullable final Node left, @Nullable final Node right);

        /**
         * Called when the traversal finds a tree node at both sides of the traversal with the same
         * name and pointing to different trees (i.e. a changed tree), or just one node tree at
         * either side of the traversal with no corresponding tree node at the other side (i.e.
         * either an added tree - {@code left} is null -, or a deleted tree - {@code right} is null
         * -).
         * <p>
         * If this method returns {@code true} then the traversal will continue down the tree(s)
         * contents, calling {@link #bucket}, {@link #feature}, or {@link #tree} as appropriate. If
         * this method returns {@code false} the traversal of the tree(s) contents will be skipped
         * and continue with the siblings or parents' siblings if there are no more nodes to
         * evaluate at the current depth.
         * 
         * @param left the left tree of the traversal
         * @param right the right tree of the traversal
         * @return {@code true} if the traversal of the contents of these trees should come right
         *         after this method returns, {@code false} if this consumer does not want to
         *         continue traversing the trees pointed out by these nodes
         * @precondition {@code left != null || right != null}
         */
        public abstract boolean tree(@Nullable final Node left, @Nullable final Node right);

        /**
         * Called once done with a {@link #tree}, regardless of the returned value
         */
        public abstract void endTree(@Nullable final Node left, @Nullable final Node right);

        /**
         * Called when the traversal finds either a bucket at both sides of the traversal with same
         * depth an index that have changed, or just one at either side of the comparison with no
         * node at the other side that would fall into that bucket if it existed.
         * <p>
         * When comparing the contents of two trees, it could be that both are bucket trees and then
         * this method will be called for each bucket index/depth, resulting in calls to this method
         * with wither both buckets or one depending on the existence of buckets at the given index
         * at both sides.
         * <p>
         * Or it can also be that only one of the trees is be a bucket tree and the other a leaf
         * tree, in which case this method can be called only if the leaf tree has no node that
         * would fall on the same bucket index at the current bucket depth; otherwise
         * {@link #feature}, {@link #tree}, or this same method will be called recursively while
         * evaluating the leaf tree nodes that would fall on this bucket index and depth, as
         * compared with the nodes of the tree pointed out by the bucket that exists at the other
         * side of the traversal, or any of its children bucket trees at a more deep bucket, until
         * there's no ambiguity.
         * <p>
         * If this method returns {@code true}, then the traversal will continue down to the
         * contents of the trees pointed out but the bucket(s), otherwise the bucket(s) contents
         * will be skipped and the traversal continues with the next bucket index, or the parents
         * trees siblings.
         * 
         * @param bucketIndex the index of the bucket inside the bucket trees being evaluated, its
         *        the same for both buckets in case buckets for the same index are present in both
         *        trees
         * @param bucketDepth the depth of the bucket(s)
         * @param left the bucket at the given index on the left-tree of the traversal, or
         *        {@code null} if no bucket exists on the left tree for that index
         * @param right the bucket at the given index on the right-tree of the traversal, or
         *        {@code null} if no bucket exists on the left tree for that index
         * @return {@code true} if a call to {@link #tree(Node, Node)} should come right after this
         *         method is called, {@code false} if this consumer does not want to continue the
         *         traversal deeper for the trees pointed out by these buckets.
         * @precondition {@code left != null || right != null}
         */
        public abstract boolean bucket(final int bucketIndex, final int bucketDepth,
                @Nullable final Bucket left, @Nullable final Bucket right);

        /**
         * Called once done with a {@link #bucket}, regardless of the returned value
         */
        public abstract void endBucket(final int bucketIndex, final int bucketDepth,
                @Nullable final Bucket left, @Nullable final Bucket right);
    }

    /**
     * Template class for consumer decorators, forwards all event calls to the provided consumer;
     * concrete subclasses shall override the event methods of their interest.
     */
    public static abstract class ForwardingConsumer implements Consumer {

        private Consumer delegate;

        public ForwardingConsumer(final Consumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void feature(Node left, Node right) {
            delegate.feature(left, right);
        }

        @Override
        public boolean tree(Node left, Node right) {
            return delegate.tree(left, right);
        }

        @Override
        public void endTree(Node left, Node right) {
            delegate.endTree(left, right);
        }

        @Override
        public boolean bucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            return delegate.bucket(bucketIndex, bucketDepth, left, right);
        }

        @Override
        public void endBucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            delegate.endBucket(bucketIndex, bucketDepth, left, right);
        }
    }

    public static class FilteringConsumer extends ForwardingConsumer {

        private final Predicate<Bounded> predicate;

        public FilteringConsumer(final Consumer delegate, final Predicate<Bounded> predicate) {
            super(delegate);
            this.predicate = predicate;
        }

        @Override
        public void feature(Node left, Node right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                super.feature(left, right);
            }
        }

        @Override
        public boolean tree(Node left, Node right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                return super.tree(left, right);
            }
            return false;
        }

        @Override
        public void endTree(Node left, Node right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                super.endTree(left, right);
            }
        }

        @Override
        public boolean bucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                return super.bucket(bucketIndex, bucketDepth, left, right);
            }
            return false;
        }

        @Override
        public void endBucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                super.endBucket(bucketIndex, bucketDepth, left, right);
            }
        }

    }
}
