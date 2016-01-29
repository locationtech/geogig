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
import static org.locationtech.geogig.api.RevTree.EMPTY;
import static org.locationtech.geogig.api.RevTree.EMPTY_TREE_ID;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
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
@NonNullByDefault
public class PreOrderDiffWalk {

    private static final NodeStorageOrder ORDER = new NodeStorageOrder();

    private static final ForkJoinPool FORK_JOIN_POOL;
    static {
        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        FORK_JOIN_POOL = new ForkJoinPool(parallelism);
    }

    private final RevTree left;

    private final RevTree right;

    private final ObjectStore leftSource;

    private final ObjectStore rightSource;

    private ObjectId metadataId;

    public PreOrderDiffWalk(RevTree left, RevTree right, ObjectStore leftSource,
            ObjectStore rightSource) {

        checkNotNull(left, "left");
        checkNotNull(right, "right");
        checkNotNull(leftSource, "leftSource");
        checkNotNull(rightSource, "rightSource");

        this.left = left;
        this.right = right;
        this.leftSource = leftSource;
        this.rightSource = rightSource;
    }

    public void setDefaultMetadataId(ObjectId metadataId) {
        this.metadataId = metadataId;
    }

    /**
     * Walk up the differences between the two trees and emit events to the {@code consumer}.
     * <p>
     * NOTE: the {@link Consumer} must be thread safe.
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
        final RevTree left = this.left;
        final RevTree right = this.right;
        Envelope lbounds = SpatialOps.boundsOf(left);

        final ObjectId metadataId = this.metadataId == null ? ObjectId.NULL : this.metadataId;

        Node lnode = Node.create(NodeRef.ROOT, left.getId(), metadataId, TYPE.TREE, lbounds);

        Envelope rbounds = SpatialOps.boundsOf(right);
        Node rnode = Node.create(NodeRef.ROOT, right.getId(), metadataId, TYPE.TREE, rbounds);

        NodeRef leftRef = NodeRef.createRoot(lnode);
        NodeRef rightRef = NodeRef.createRoot(rnode);

        TraverseTree task = new TraverseTree(new CancellableConsumer(consumer), leftSource,
                rightSource, leftRef, rightRef);
        FORK_JOIN_POOL.invoke(task);

    }

    @SuppressWarnings("serial")
    private static abstract class WalkAction extends RecursiveAction {

        protected final ObjectStore leftSource, rightSource;

        protected final CancellableConsumer consumer;

        protected final int bucketDepth;

        protected final NodeRef leftParent;

        protected final NodeRef rightParent;

        WalkAction(CancellableConsumer consumer, ObjectStore ls, ObjectStore rs,
                NodeRef leftParent, NodeRef rightParent) {
            this(consumer, ls, rs, leftParent, rightParent, 0);
        }

        WalkAction(CancellableConsumer consumer, ObjectStore ls, ObjectStore rs,
                NodeRef leftParent, NodeRef rightParent, int bucketDepth) {
            checkNotNull(consumer, "consumer");
            checkNotNull(ls, "left source database");
            checkNotNull(rs, "right soourda datbaase");
            checkArgument(leftParent != null || rightParent != null,
                    "leftParent and rightParent can't be null at the same time");
            checkArgument(bucketDepth > -1);
            checkArgument(ls != null && rs != null);
            // checkArgument(leftParent != null && rightParent != null);
            this.consumer = consumer;
            this.leftSource = ls;
            this.rightSource = rs;
            this.leftParent = leftParent;
            this.rightParent = rightParent;
            this.bucketDepth = bucketDepth;
        }

        TraverseTree traverseTree(NodeRef left, NodeRef right) {
            return new TraverseTree(consumer, leftSource, rightSource, left, right);
        }

        TraverseTreeContents traverseTreeContents(NodeRef leftParent, NodeRef rightParent,
                RevTree left, RevTree right, int bucketDepth) {

            return new TraverseTreeContents(consumer, leftSource, rightSource, leftParent,
                    rightParent, left, right, bucketDepth);
        }

        TraverseLeafLeaf leafLeaf(NodeRef leftParent, NodeRef rightParent,
                Iterator<Node> leftChildren, Iterator<Node> rightChildren) {
            return new TraverseLeafLeaf(consumer, leftSource, rightSource, leftParent, rightParent,
                    leftChildren, rightChildren);
        }

        TraverseBucketBucket bucketBucket(RevTree left, RevTree right, int bucketDepth) {

            return new TraverseBucketBucket(consumer, leftSource, rightSource, leftParent,
                    rightParent, left, right, bucketDepth);
        }

        TraverseBucketLeaf bucketLeaf(RevTree bucket, Iterator<Node> rightcChildren, int bucketDepth) {
            return new TraverseBucketLeaf(consumer, leftSource, rightSource, leftParent,
                    rightParent, bucket, rightcChildren, bucketDepth);
        }

        TraverseLeafBucket leafBucket(Iterator<Node> leftcChildren, RevTree bucket, int bucketDepth) {
            return new TraverseLeafBucket(consumer, leftSource, rightSource, leftParent,
                    rightParent, leftcChildren, bucket, bucketDepth);
        }

        /**
         * Called when found a difference between two nodes. It can be a removal ({@code right} is
         * null), an added node ({@code left} is null}, or a modified feature/tree (neither is
         * null); but {@code left} and {@code right} can never be equal.
         * <p>
         * Depending on the type of node, this method will call {@link Consumer#tree} or
         * {@link Consumer#feature}, and continue the traversal down the trees in case it was a tree
         * and {@link Consumer#tree} returned null.
         */
        @Nullable
        protected final WalkAction node(@Nullable final NodeRef left, @Nullable final NodeRef right) {
            if (consumer.isCancelled()) {
                return null;
            }
            checkState(left != null || right != null, "both nodes can't be null");
            checkArgument(!Objects.equal(left, right));

            final TYPE type = left == null ? right.getType() : left.getType();

            if (TYPE.FEATURE.equals(type)) {
                consumer.feature(left, right);
                return null;
            }

            checkState(TYPE.TREE.equals(type));
            return traverseTree(left, right);
        }

        /**
         * Split the given nodes into lists keyed by the bucket indes they would belong if they were
         * part of a tree bucket at the given {@code bucketDepth}
         */
        protected final ListMultimap<Integer, Node> splitNodesToBucketsAtDepth(
                Iterator<Node> nodes, final int bucketDepth) {

            Function<Node, Integer> keyFunction = new Function<Node, Integer>() {
                @Override
                public Integer apply(Node node) {
                    return ORDER.bucket(node, bucketDepth);
                }
            };
            ListMultimap<Integer, Node> nodesByBucket = Multimaps.index(nodes, keyFunction);
            return nodesByBucket;
        }

    }

    /**
     * When this action is called its guaranteed that either {@link Consumer#tree} returned
     * {@code true} (i.e. its a pair of trees pointed out by a Node), or {@link Consumer#bucket}
     * returned {@code true} (i.e. they are trees pointed out by buckets).
     * 
     * @param consumer the callback object
     * @param left the tree at the left side of the comparison
     * @param right the tree at the right side of the comparison
     * @param bucketDepth the depth of bucket traversal (only non zero if comparing two bucket
     *        trees, as when called from {@link #handleBucketBucket})
     * @precondition {@code left != null && right != null}
     */
    @SuppressWarnings("serial")
    private static class TraverseTree extends WalkAction {

        private final NodeRef leftNode;

        private final NodeRef rightNode;

        public TraverseTree(CancellableConsumer consumer, ObjectStore ls, ObjectStore rs,
                NodeRef leftRef, NodeRef rightRef) {
            super(consumer, ls, rs, leftRef, rightRef);
            this.leftNode = leftRef;
            this.rightNode = rightRef;
        }

        @Override
        protected void compute() {
            if (Objects.equal(leftNode, rightNode)) {
                return;
            }
            if (consumer.isCancelled()) {
                return;
            }
            if (consumer.tree(leftNode, rightNode)) {
                RevTree left;
                RevTree right;
                left = leftNode == null || EMPTY_TREE_ID.equals(leftNode.getObjectId()) ? EMPTY
                        : leftSource.getTree(leftNode.getObjectId());
                right = rightNode == null || EMPTY_TREE_ID.equals(rightNode.getObjectId()) ? EMPTY
                        : rightSource.getTree(rightNode.getObjectId());

                TraverseTreeContents traverseTreeContents = new TraverseTreeContents(consumer,
                        leftSource, rightSource, leftNode, rightNode, left, right, 0);

                traverseTreeContents.compute();

            }
            consumer.endTree(leftNode, rightNode);
        }
    }

    @SuppressWarnings("serial")
    private static class TraverseTreeContents extends WalkAction {

        private final RevTree left, right;

        public TraverseTreeContents(CancellableConsumer consumer, ObjectStore ls, ObjectStore rs,
                NodeRef leftParent, NodeRef rightParent, RevTree left, RevTree right,
                int bucketDepth) {

            super(consumer, ls, rs, leftParent, rightParent, bucketDepth);

            checkArgument(left != null && right != null);
            this.left = left;
            this.right = right;
        }

        @Override
        protected void compute() {
            if (Objects.equal(left, right)) {
                return;
            }
            if (consumer.isCancelled()) {
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

            WalkAction task;
            if (leftIsLeaf && rightIsLeaf) {// 1-

                task = leafLeaf(leftParent, rightParent, leftc, rightc);

            } else if (!(leftIsLeaf || rightIsLeaf)) {// 2-
                task = bucketBucket(left, right, bucketDepth);
            } else if (leftIsLeaf) {// 3-

                task = leafBucket(leftc, right, bucketDepth);

            } else {// 4-

                task = bucketLeaf(left, rightc, bucketDepth);
            }

            invokeAll(task);
        }
    }

    @SuppressWarnings("serial")
    private static class TraverseLeafLeaf extends WalkAction {

        private Iterator<Node> left;

        private Iterator<Node> right;

        TraverseLeafLeaf(CancellableConsumer consumer, ObjectStore ls, ObjectStore rs,
                NodeRef leftParent, NodeRef rightParent, Iterator<Node> leftChildren,
                Iterator<Node> rightChildren) {
            super(consumer, ls, rs, leftParent, rightParent);
            this.left = leftChildren;
            this.right = rightChildren;
        }

        /**
         * Traverse and compare the {@link RevTree#children() children} nodes of two leaf trees,
         * calling {@link #node(Consumer, Node, Node)} for each diff.
         */
        @Override
        protected void compute() {
            if (consumer.isCancelled()) {
                return;
            }
            PeekingIterator<Node> li = Iterators.peekingIterator(left);
            PeekingIterator<Node> ri = Iterators.peekingIterator(right);

            List<WalkAction> tasks = new ArrayList<>();

            while (li.hasNext() && ri.hasNext() && !consumer.isCancelled()) {
                Node lpeek = li.peek();
                Node rpeek = ri.peek();
                int order = ORDER.compare(lpeek, rpeek);
                @Nullable
                WalkAction action = null;
                if (order < 0) {
                    NodeRef lref = newRef(leftParent, li.next());
                    action = node(lref, null);// removal
                } else if (order == 0) {// change
                    // same feature at both sides of the traversal, consume them and check if its
                    // changed it or not
                    Node l = li.next();
                    Node r = ri.next();
                    if (!Objects.equal(l, r)) {
                        NodeRef lref = newRef(leftParent, l);
                        NodeRef rref = newRef(rightParent, r);
                        if (!l.equals(r)) {
                            action = node(lref, rref);
                        }
                    }
                } else {
                    NodeRef rref = newRef(rightParent, ri.next());
                    action = node(null, rref);// addition
                }
                if (action != null) {
                    tasks.add(action);
                }
            }

            checkState(consumer.isCancelled() || !li.hasNext() || !ri.hasNext(),
                    "either the left or the right iterator should have been fully consumed");

            // right fully consumed, any remaining node in left is a removal
            while (!consumer.isCancelled() && li.hasNext()) {
                WalkAction action = node(newRef(leftParent, li.next()), null);
                if (action != null) {
                    tasks.add(action);
                }
            }

            // left fully consumed, any remaining node in right is an add
            while (!consumer.isCancelled() && ri.hasNext()) {
                WalkAction action = node(null, newRef(rightParent, ri.next()));
                if (action != null) {
                    tasks.add(action);
                }
            }

            invokeAll(tasks);
        }

        private NodeRef newRef(NodeRef parent, Node lnode) {
            return NodeRef.create(parent.path(), lnode, parent.getMetadataId());
        }

    }

    @SuppressWarnings("serial")
    private static class TraverseBucketBucket extends WalkAction {

        private final RevTree left, right;

        TraverseBucketBucket(CancellableConsumer consumer, ObjectStore ls, ObjectStore rs,
                NodeRef leftParent, NodeRef rightParent, RevTree left, RevTree right,
                int bucketDepth) {

            super(consumer, ls, rs, leftParent, rightParent, bucketDepth);
            checkArgument(left != null && right != null);
            checkArgument(left.isEmpty() || left.buckets().isPresent());
            checkArgument(right.isEmpty() || right.buckets().isPresent());

            this.left = left;
            this.right = right;
        }

        /**
         * Traverse two bucket trees and notify their differences to the {@code consumer}.
         * <p>
         * If this method is called than its guaranteed that the two bucket trees are note equal
         * (one of them may be empty though), and that {@link Consumer#bucket} returned {@code true}
         * <p>
         * For each bucket index present in the joint set of the two trees buckets,
         * {@link #traverseTree(Consumer, RevTree, RevTree, int)} will be called for the bucket
         * trees that are not equal with {@code bucketDepth} incremented by one.
         * 
         * @param consumer the callback object to receive diff events from the comparison of the two
         *        trees
         * @param left the bucket tree at the left side of the comparison
         * @param right the bucket tree at the right side of the comparison
         * @param bucketDepth the current depth at which the comparison is evaluating these two
         *        bucket trees
         * @see #traverseTree(Consumer, RevTree, RevTree, int)
         * @precondition {@code !left.equals(right)}
         * @precondition {@code left.isEmpty() || left.buckets().isPresent()}
         * @precondition {@code right.isEmpty() || right.buckets().isPresent()}
         */
        @Override
        protected void compute() {
            if (consumer.isCancelled()) {
                return;
            }
            final ImmutableSortedMap<Integer, Bucket> lb = left.buckets().get();
            final ImmutableSortedMap<Integer, Bucket> rb = right.buckets().get();
            final TreeSet<Integer> availableIndexes = newTreeSet(union(lb.keySet(), rb.keySet()));

            final Map<ObjectId, RevObject> trees = loadTrees(lb, rb);

            @Nullable
            Bucket lbucket, rbucket;
            RevTree ltree, rtree;

            List<TraverseTreeContents> tasks = new ArrayList<>();

            for (Integer index : availableIndexes) {
                lbucket = lb.get(index);
                rbucket = rb.get(index);
                if (Objects.equal(lbucket, rbucket)) {
                    continue;
                }

                if (consumer.bucket(leftParent, rightParent, index.intValue(), bucketDepth,
                        lbucket, rbucket)) {

                    ltree = lbucket == null ? EMPTY : (RevTree) trees.get(lbucket.getObjectId());
                    rtree = rbucket == null ? EMPTY : (RevTree) trees.get(rbucket.getObjectId());

                    TraverseTreeContents task = traverseTreeContents(leftParent, rightParent,
                            ltree, rtree, bucketDepth + 1);
                    tasks.add(task);
                }
            }

            // fork()
            invokeAll(tasks);
            // after all tasks join()
            for (Integer index : availableIndexes) {
                lbucket = lb.get(index);
                rbucket = rb.get(index);
                if (Objects.equal(lbucket, rbucket)) {
                    continue;
                }
                consumer.endBucket(leftParent, rightParent, index, bucketDepth, lbucket, rbucket);
            }
        }

        private Map<ObjectId, RevObject> loadTrees(final ImmutableSortedMap<Integer, Bucket> lb,
                final ImmutableSortedMap<Integer, Bucket> rb) {
            final Map<ObjectId, RevObject> trees;
            final Set<ObjectId> lbucketIds = Sets.newHashSet(transform(lb.values(), BUCKET_ID));
            final Set<ObjectId> rbucketIds = Sets.newHashSet(transform(rb.values(), BUCKET_ID));

            // get all buckets at once, to leverage ObjectStore optimizations
            if (leftSource == rightSource) {
                trees = uniqueIndex(leftSource.getAll(Sets.union(lbucketIds, rbucketIds)),
                        OBJECT_ID);
            } else {
                trees = Maps.newHashMap(uniqueIndex(
                        leftSource.getAll(transform(lb.values(), BUCKET_ID)), OBJECT_ID));

                final Map<ObjectId, RevObject> rightBucketTrees;
                rightBucketTrees = uniqueIndex(
                        rightSource.getAll(transform(rb.values(), BUCKET_ID)), OBJECT_ID);
                trees.putAll(rightBucketTrees);
            }
            return trees;
        }
    }

    @SuppressWarnings("serial")
    private static class TraverseBucketLeaf extends WalkAction {

        private RevTree bucket;

        private Iterator<Node> leaf;

        TraverseBucketLeaf(CancellableConsumer consumer, ObjectStore ls, ObjectStore rs,
                NodeRef leftParent, NodeRef rightParent, RevTree bucket,
                Iterator<Node> rightcChildren, int bucketDepth) {
            super(consumer, ls, rs, leftParent, rightParent, bucketDepth);
            checkArgument(bucket.buckets().isPresent());
            this.bucket = bucket;
            this.leaf = rightcChildren;
        }

        /**
         * Compares a bucket tree at the left side of the comparison, and a the
         * {@link RevTree#children() children} nodes of a leaf tree at the right side of the
         * comparison.
         * <p>
         * This happens when the left tree is much larger than the right tree
         * <p>
         * This traversal is symmetric to {@link #traverseLeafBucket} so be careful that any change
         * made to this method shall have a matching change at {@link #traverseLeafBucket}
         * 
         * @precondition {@code left.buckets().isPresent()}
         */
        @Override
        protected void compute() {
            if (consumer.isCancelled()) {
                return;
            }
            final SortedMap<Integer, Bucket> leftBuckets = bucket.buckets().get();
            final ListMultimap<Integer, Node> nodesByBucket = splitNodesToBucketsAtDepth(leaf,
                    bucketDepth);

            final SortedSet<Integer> bucketIndexes = Sets.newTreeSet(Sets.union(
                    leftBuckets.keySet(), nodesByBucket.keySet()));

            // get all buckets at once, to leverage ObjectStore optimizations
            final Map<ObjectId, RevObject> bucketTrees;
            bucketTrees = uniqueIndex(
                    leftSource.getAll(transform(leftBuckets.values(), BUCKET_ID)), OBJECT_ID);

            List<WalkAction> tasks = new ArrayList<>();

            Map<Integer, Bucket> pendingEndBucketNotifications = new TreeMap<>();
            for (Integer bucketIndex : bucketIndexes) {
                Bucket leftBucket = leftBuckets.get(bucketIndex);
                List<Node> rightNodes = nodesByBucket.get(bucketIndex);// never returns null, but
                                                                       // empty
                if (null == leftBucket) {
                    tasks.add(leafLeaf(leftParent, rightParent, Iterators.<Node> emptyIterator(),
                            rightNodes.iterator()));
                } else if (rightNodes.isEmpty()) {

                    RevTree leftTree = (RevTree) bucketTrees.get(leftBucket.getObjectId());
                    if (consumer.bucket(leftParent, rightParent, bucketIndex, bucketDepth,
                            leftBucket, null)) {

                        TraverseTreeContents task = traverseTreeContents(leftParent, rightParent,
                                leftTree, EMPTY, bucketDepth + 1);
                        tasks.add(task);
                        pendingEndBucketNotifications.put(bucketIndex, leftBucket);
                    }
                } else {
                    RevTree leftTree = (RevTree) bucketTrees.get(leftBucket.getObjectId());
                    if (leftTree.buckets().isPresent()) {
                        tasks.add(bucketLeaf(leftTree, rightNodes.iterator(), bucketDepth + 1));
                    } else {
                        tasks.add(leafLeaf(leftParent, rightParent, leftTree.children(),
                                rightNodes.iterator()));
                    }
                }
            }
            if (!consumer.isCancelled()) {
                invokeAll(tasks);
            }
            for (Entry<Integer, Bucket> e : pendingEndBucketNotifications.entrySet()) {
                int bucketIndex = e.getKey().intValue();
                Bucket leftBucket = e.getValue();
                consumer.endBucket(leftParent, rightParent, bucketIndex, bucketDepth, leftBucket,
                        null);
            }
        }
    }

    @SuppressWarnings("serial")
    private static class TraverseLeafBucket extends WalkAction {

        private Iterator<Node> leaf;

        private RevTree bucket;

        TraverseLeafBucket(CancellableConsumer consumer, ObjectStore ls, ObjectStore rs,
                NodeRef leftParent, NodeRef rightParent, Iterator<Node> leftcChildren,
                RevTree bucket, int bucketDepth) {
            super(consumer, ls, rs, leftParent, rightParent, bucketDepth);
            checkArgument(bucket.buckets().isPresent());
            this.leaf = leftcChildren;
            this.bucket = bucket;
        }

        /**
         * Compares a bucket tree at the right side of the comparison, and a the
         * {@link RevTree#children() children} nodes of a leaf tree at the left side of the
         * comparison.
         * <p>
         * This happens when the right tree is much larger than the left tree
         * <p>
         * This traversal is symmetric to {@link #traverseBucketLeaf} so be careful that any change
         * made to this method shall have a matching change at {@link #traverseBucketLeaf}
         * 
         * @precondition {@code right.buckets().isPresent()}
         */
        @Override
        protected void compute() {
            if (consumer.isCancelled()) {
                return;
            }
            final SortedMap<Integer, Bucket> rightBuckets = bucket.buckets().get();
            final ListMultimap<Integer, Node> nodesByBucket = splitNodesToBucketsAtDepth(leaf,
                    bucketDepth);

            final SortedSet<Integer> bucketIndexes = Sets.newTreeSet(Sets.union(
                    rightBuckets.keySet(), nodesByBucket.keySet()));

            // get all buckets at once, to leverage ObjectStore optimizations
            final Map<ObjectId, RevObject> bucketTrees;
            bucketTrees = uniqueIndex(
                    rightSource.getAll(transform(rightBuckets.values(), BUCKET_ID)), OBJECT_ID);

            List<WalkAction> tasks = new ArrayList<>();

            Map<Integer, Bucket> pendingEndBucketNotifications = new TreeMap<>();

            for (Integer bucketIndex : bucketIndexes) {
                Bucket rightBucket = rightBuckets.get(bucketIndex);
                List<Node> leftNodes = nodesByBucket.get(bucketIndex);// never returns null, but
                                                                      // empty
                if (null == rightBucket) {
                    tasks.add(leafLeaf(leftParent, rightParent, leftNodes.iterator(),
                            Iterators.<Node> emptyIterator()));
                } else if (leftNodes.isEmpty()) {
                    RevTree rightTree = (RevTree) bucketTrees.get(rightBucket.getObjectId());
                    if (consumer.bucket(leftParent, rightParent, bucketIndex, bucketDepth, null,
                            rightBucket)) {

                        TraverseTreeContents task = traverseTreeContents(leftParent, rightParent,
                                EMPTY, rightTree, bucketDepth + 1);
                        tasks.add(task);
                        pendingEndBucketNotifications.put(bucketIndex, rightBucket);

                    }
                } else {
                    RevTree rightTree = (RevTree) bucketTrees.get(rightBucket.getObjectId());
                    if (rightTree.buckets().isPresent()) {
                        tasks.add(leafBucket(leftNodes.iterator(), rightTree, bucketDepth + 1));
                    } else {
                        tasks.add(leafLeaf(leftParent, rightParent, leftNodes.iterator(),
                                rightTree.children()));
                    }
                }
            }
            if (!consumer.isCancelled()) {
                invokeAll(tasks);
            }
            for (Entry<Integer, Bucket> e : pendingEndBucketNotifications.entrySet()) {
                int bucketIndex = e.getKey().intValue();
                Bucket rightBucket = e.getValue();
                consumer.endBucket(leftParent, rightParent, bucketIndex, bucketDepth, null,
                        rightBucket);
            }
        }
    }

    private static final Function<Bucket, ObjectId> BUCKET_ID = new Function<Bucket, ObjectId>() {
        @Override
        public ObjectId apply(Bucket b) {
            return b.getObjectId();
        }
    };

    private static final Function<RevObject, ObjectId> OBJECT_ID = new Function<RevObject, ObjectId>() {
        @Override
        public ObjectId apply(RevObject o) {
            return o.getId();
        }
    };

    /**
     * Defines an interface to consume the events emitted by a diff-tree "depth first" traversal,
     * with the ability to be notified of changes to feature and tree nodes, as well as to buckets,
     * and to skip the further traversal of whole trees (either as pointed out by "name tree" nodes,
     * or internal tree buckets).
     * <p>
     * NOTE implementations are mandated to be thread safe.
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
         * @return {@code false} if the WHOLE traversal shall be aborted, {@true} to continue
         *         traversing other features and trees. Note this differs from the return value of
         *         {@link #tree()} and {@link #bucket()} in that they only avoid the traversal of
         *         the indicated tree or bucket, not the whole traversal.
         * @precondition {@code left != null || right != null}
         * @precondition {@code if(left != null && right != null) then left.name() == right.name()}
         */
        public abstract boolean feature(@Nullable final NodeRef left, @Nullable final NodeRef right);

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
         * @return {@code true} if the traversal of the contents of this pair of trees should come
         *         right after this method returns, {@code false} if this consumer does not want to
         *         continue traversing the trees pointed out by these nodes. Note this differs from
         *         the return value of {@link #feature()} in that {@code false} only avoids going
         *         deeper into this tree, instead of aborting the whole traversal
         * @precondition {@code left != null || right != null}
         */
        public abstract boolean tree(@Nullable final NodeRef left, @Nullable final NodeRef right);

        /**
         * Called once done with a {@link #tree}, regardless of the returned value
         */
        public abstract void endTree(@Nullable final NodeRef left, @Nullable final NodeRef right);

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
        public abstract boolean bucket(final NodeRef leftParent, final NodeRef rightParent,
                final int bucketIndex, final int bucketDepth, @Nullable final Bucket left,
                @Nullable final Bucket right);

        /**
         * Called once done with a {@link #bucket}, regardless of the returned value
         */
        public abstract void endBucket(NodeRef leftParent, NodeRef rightParent,
                final int bucketIndex, final int bucketDepth, @Nullable final Bucket left,
                @Nullable final Bucket right);
    }

    /**
     * Template class for consumer decorators, forwards all event calls to the provided consumer;
     * concrete subclasses shall override the event methods of their interest.
     */
    public static abstract class ForwardingConsumer implements Consumer {

        protected final Consumer delegate;

        public ForwardingConsumer(final Consumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean feature(NodeRef left, NodeRef right) {
            return delegate.feature(left, right);
        }

        @Override
        public boolean tree(NodeRef left, NodeRef right) {
            return delegate.tree(left, right);
        }

        @Override
        public void endTree(NodeRef left, NodeRef right) {
            delegate.endTree(left, right);
        }

        @Override
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, int bucketIndex,
                int bucketDepth, Bucket left, Bucket right) {
            return delegate.bucket(leftParent, rightParent, bucketIndex, bucketDepth, left, right);
        }

        @Override
        public void endBucket(NodeRef leftParent, NodeRef rightParent, int bucketIndex,
                int bucketDepth, Bucket left, Bucket right) {
            delegate.endBucket(leftParent, rightParent, bucketIndex, bucketDepth, left, right);
        }
    }

    public static class MaxFeatureDiffsLimiter extends ForwardingConsumer {

        private final AtomicLong count;

        private final long limit;

        public MaxFeatureDiffsLimiter(final Consumer delegate, final long limit) {
            super(delegate);
            this.limit = limit;
            this.count = new AtomicLong();
        }

        @Override
        public boolean feature(NodeRef left, NodeRef right) {
            if (count.incrementAndGet() > limit) {
                return false;
            }
            return super.feature(left, right);
        }
    }

    public static class FilteringConsumer extends ForwardingConsumer {

        private final Predicate<Bounded> predicate;

        public FilteringConsumer(final Consumer delegate, final Predicate<Bounded> predicate) {
            super(delegate);
            this.predicate = predicate;
        }

        @Override
        public boolean feature(NodeRef left, NodeRef right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                super.feature(left, right);
            }
            return true;
        }

        @Override
        public boolean tree(NodeRef left, NodeRef right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                return super.tree(left, right);
            }
            return false;
        }

        @Override
        public void endTree(NodeRef left, NodeRef right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                super.endTree(left, right);
            }
        }

        @Override
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, int bucketIndex,
                int bucketDepth, Bucket left, Bucket right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                return super.bucket(leftParent, rightParent, bucketIndex, bucketDepth, left, right);
            }
            return false;
        }

        @Override
        public void endBucket(NodeRef leftParent, NodeRef rightParent, int bucketIndex,
                int bucketDepth, Bucket left, Bucket right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                super.endBucket(leftParent, rightParent, bucketIndex, bucketDepth, left, right);
            }
        }
    }

    private static final class CancellableConsumer extends ForwardingConsumer {

        private final AtomicBoolean cancel = new AtomicBoolean();

        public CancellableConsumer(Consumer delegate) {
            super(delegate);
        }

        private void abortTraversal() {
            this.cancel.set(true);
        }

        public boolean isCancelled() {
            return this.cancel.get();
        }

        @Override
        public boolean feature(NodeRef left, NodeRef right) {
            boolean continuteTraversal = !isCancelled() && delegate.feature(left, right);
            if (!continuteTraversal) {
                abortTraversal();
            }
            return continuteTraversal;
        }

        @Override
        public boolean tree(NodeRef left, NodeRef right) {
            return !isCancelled() && delegate.tree(left, right);
        }

        @Override
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, int bucketIndex,
                int bucketDepth, Bucket left, Bucket right) {
            return !isCancelled()
                    && delegate.bucket(leftParent, rightParent, bucketIndex, bucketDepth, left,
                            right);
        }
    }
}
