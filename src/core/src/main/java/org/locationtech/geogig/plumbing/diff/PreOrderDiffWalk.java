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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newTreeSet;
import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeOrdering;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.primitives.Ints;

/**
 * Provides a means to "walk" the differences between two {@link RevTree trees} in in-order order
 * and emit diff events to a {@link Consumer}, which can choose to skip parts of the walk when it
 * had collected enough information for its purpose and don't need to go further down a given pair
 * of trees (either named or bucket).
 */
@NonNullByDefault
public class PreOrderDiffWalk {

    private static final Logger log = LoggerFactory.getLogger(PreOrderDiffWalk.class);

    // this is the same as the defaultForkJoinWorkerThreadFactory but gives the threads a
    // different name (easier to see in debugger)
    static ForkJoinPool.ForkJoinWorkerThreadFactory threadFactoryPrivate = pool -> {
        final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory
                .newThread(pool);
        worker.setName("PreOrderDiffWalk-private-" + worker.getPoolIndex());
        return worker;
    };

    // this is the same as the defaultForkJoinWorkerThreadFactory but gives the threads a
    // different name (easier to see in debugger)
    static ForkJoinPool.ForkJoinWorkerThreadFactory threadFactoryShared = pool -> {
        final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory
                .newThread(pool);
        worker.setName("PreOrderDiffWalk-shared-" + worker.getPoolIndex());
        return worker;
    };

    private static final ForkJoinPool SHARED_FORK_JOIN_POOL;

    static {
        final int parallelism = Math.max(2,
                Math.min(16, Runtime.getRuntime().availableProcessors()));
        // establishes local first-in-first-out scheduling mode for forked
        // more appropriate than default locally stack-based mode when
        // worker threads only process event-style asynchronous tasks
        final boolean asyncMode = true;
        SHARED_FORK_JOIN_POOL = new ForkJoinPool(parallelism, threadFactoryShared, null, asyncMode);
    }

    public NodeOrdering ORDER = CanonicalNodeOrder.INSTANCE;

    /**
     * Contains the full path to the bucket as an array of integers where the array length
     * determines the bucket depth and each array element its index at the depth defined by the
     * element index.
     *
     */
    public static final class BucketIndex implements Comparable<BucketIndex> {

        public static final BucketIndex EMPTY = new BucketIndex(RevTree.EMPTY, RevTree.EMPTY);

        private final int[] indexPath;

        private RevTree left;

        private RevTree right;

        /**
         * "Null object" for the root tree.
         */
        // static final BucketIndex ROOT = new BucketIndex();

        /**
         * Creates a root bucket index
         */
        private BucketIndex(RevTree left, RevTree right) {
            checkNotNull(left);
            checkNotNull(right);
            this.indexPath = new int[0];
            this.left = left;
            this.right = right;
        }

        private BucketIndex(final int[] parentPath, final int index, RevTree left, RevTree right) {
            checkNotNull(parentPath);
            checkNotNull(left);
            checkNotNull(right);
            int[] path = new int[parentPath.length + 1];
            System.arraycopy(parentPath, 0, path, 0, parentPath.length);
            path[parentPath.length] = index;
            this.indexPath = path;
            this.left = left;
            this.right = right;
        }

        public int[] getIndexPath() {
            return this.indexPath.clone();
        }

        /**
         * The tree at the left side of the comparison when calling {@link Consumer#bucket}. It
         * could be the bucket tree itself or the leaf tree if we're comparing a revtree that's
         * expanded (i.e. the left side of the comparison is a leaf tree and the right side a
         * buckets tree)
         * 
         * @return the left tree, non null, possibly {@link RevTree#EMPTY}
         */
        public RevTree left() {
            return left;
        }

        /**
         * The tree at the right side of the comparison when calling {@link Consumer#bucket}. It
         * could be the bucket tree itself or the leaf tree if we're comparing a revtree that's
         * collapsed (i.e. the left side of the comparison is a buckets tree and the right side a
         * leaf tree)
         * 
         * @return the right tree, non null, possibly {@link RevTree#EMPTY}
         */
        public RevTree right() {
            return right;
        }

        public static BucketIndex root(RevTree left, RevTree right) {
            return new BucketIndex(left, right);
        }

        public BucketIndex append(final Integer index, RevTree left, RevTree right) {
            return append(index.intValue(), left, right);
        }

        public BucketIndex append(final int index, RevTree left, RevTree right) {
            return new BucketIndex(this.indexPath, index, left, right);
        }

        /**
         * @return the zero-based depth index for the bucket addressed by this bucket index
         */
        public int depthIndex() {
            return indexPath.length - 1;
        }

        /**
         * @return the bucket index at the last depth level
         */
        public Integer lastIndex() {
            return Integer.valueOf(indexPath[indexPath.length - 1]);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BucketIndex)) {
                return false;
            }
            return Arrays.equals(indexPath, ((BucketIndex) o).indexPath);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(indexPath);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < indexPath.length - 1; i++) {
                sb.append(indexPath[i]).append('/');
            }
            if (indexPath.length > 0) {
                sb.append(indexPath[indexPath.length - 1]);
            }
            return sb.toString();
        }

        @Override
        public int compareTo(BucketIndex o) {
            return Ints.lexicographicalComparator().compare(indexPath, o.indexPath);
        }
    }

    private final RevTree left;

    private final RevTree right;

    private final ObjectStore leftSource;

    private final ObjectStore rightSource;

    private ObjectId metadataId;

    private ForkJoinPool forkJoinPool;

    private CancellableConsumer walkConsumer = null;

    private AtomicBoolean finished = new AtomicBoolean(false);

    private boolean reportFeatures = true;

    public PreOrderDiffWalk(RevTree left, RevTree right, ObjectStore leftSource,
            ObjectStore rightSource) {
        this(left, right, leftSource, rightSource, false);
    }

    public PreOrderDiffWalk(RevTree left, RevTree right, ObjectStore leftSource,
            ObjectStore rightSource, boolean preserveIterationOrder) {

        checkNotNull(left, "left");
        checkNotNull(right, "right");
        checkNotNull(leftSource, "leftSource");
        checkNotNull(rightSource, "rightSource");

        this.left = left;
        this.right = right;
        this.leftSource = leftSource;
        this.rightSource = rightSource;
        if (preserveIterationOrder) {
            forkJoinPool = new ForkJoinPool(1, threadFactoryPrivate, null, false);
        } else {
            forkJoinPool = SHARED_FORK_JOIN_POOL;
        }
    }

    public void setDefaultMetadataId(ObjectId metadataId) {
        this.metadataId = metadataId;
    }

    private static final class SideInfo {

        final ObjectStore source;

        @Nullable
        final NodeRef parentRef;

        SideInfo(ObjectStore source, NodeRef parentRef) {
            this.source = source;
            this.parentRef = parentRef;
        }
    }

    private static final class WalkInfo {

        final CancellableConsumer consumer;

        final SideInfo left;

        final SideInfo right;

        final NodeOrdering nodeOrder;

        final boolean reportFeatures;

        WalkInfo(CancellableConsumer consumer, SideInfo left, SideInfo right,
                NodeOrdering nodeOrder, boolean reportFeatures) {
            this.consumer = consumer;
            this.left = left;
            this.right = right;
            this.nodeOrder = nodeOrder;
            this.reportFeatures = reportFeatures;
        }

        public WalkInfo child(NodeRef leftChild, NodeRef rightChild) {
            SideInfo leftInfo = new SideInfo(left.source, leftChild);
            SideInfo rightInfo = new SideInfo(right.source, rightChild);
            return new WalkInfo(consumer, leftInfo, rightInfo, nodeOrder, reportFeatures);
        }
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

        Node lnode = RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT, left.getId(),
                metadataId, TYPE.TREE, lbounds, null);

        Envelope rbounds = SpatialOps.boundsOf(right);
        Node rnode = RevObjectFactory.defaultInstance().createNode(NodeRef.ROOT, right.getId(),
                metadataId, TYPE.TREE, rbounds, null);

        NodeRef leftRef = NodeRef.createRoot(lnode);
        NodeRef rightRef = NodeRef.createRoot(rnode);

        this.walkConsumer = new CancellableConsumer(consumer);

        SideInfo leftInfo = new SideInfo(leftSource, leftRef);
        SideInfo rightInfo = new SideInfo(rightSource, rightRef);

        WalkInfo walkInfo = new WalkInfo(walkConsumer, leftInfo, rightInfo, ORDER, reportFeatures);

        TraverseTree task = new TraverseTree(walkInfo);

        try {
            forkJoinPool.invoke(task);
        } catch (Exception e) {
            if (!(leftSource.isOpen() && rightSource.isOpen())) {
                // someone closed the repo, we're ok.
            } else {
                log.error("Exception caught executing task", e);
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
        } finally {
            finished.set(true);
            cleanupForkJoinPool();
        }
    }

    public PreOrderDiffWalk reportFeatures(boolean report) {
        this.reportFeatures = report;
        return this;
    }

    private void cleanupForkJoinPool() {
        if (forkJoinPool == SHARED_FORK_JOIN_POOL)
            return; // no need to clean up
        else
            forkJoinPool.shutdown(); // private pool needs cleaning
    }

    public void nodeOrder(NodeOrdering nodeOrder) {
        checkNotNull(nodeOrder);
        this.ORDER = nodeOrder;
    }

    /**
     * Abort the traversal in the consumer.
     */
    public void abortTraversal() {
        if (walkConsumer != null) {
            walkConsumer.abortTraversal();
        }
    }

    public void awaitTermination() {
        while (!finished.get()) {
            // wait.
        }
    }

    @SuppressWarnings("serial")
    private static abstract class WalkAction extends RecursiveAction {

        protected final WalkInfo info;

        protected final BucketIndex bucketIndex;

        WalkAction(WalkInfo walkInfo) {
            this(walkInfo, BucketIndex.EMPTY);
        }

        WalkAction(WalkInfo info, final BucketIndex bucketIndex) {
            checkArgument(info.left.parentRef != null || info.right.parentRef != null,
                    "leftParent and rightParent can't be null at the same time");
            checkNotNull(bucketIndex);
            this.info = info;
            this.bucketIndex = bucketIndex;
        }

        TraverseTree traverseTree(@Nullable NodeRef left, @Nullable NodeRef right) {
            checkArgument(left != null || right != null);
            WalkInfo treeInfo = info.child(left, right);
            return new TraverseTree(treeInfo);
        }

        TraverseTreeContents traverseTreeContents(RevTree left, RevTree right) {
            return new TraverseTreeContents(info, left, right, bucketIndex);
        }

        TraverseLeafLeaf leafLeaf(Iterator<Node> leftChildren, Iterator<Node> rightChildren) {
            return new TraverseLeafLeaf(info, leftChildren, rightChildren);
        }

        List<WalkAction> bucketBucket(RevTree left, RevTree right) {
            checkArgument(left.bucketsSize() > 0);
            checkArgument(right.bucketsSize() > 0);
            if (info.consumer.isCancelled()) {
                return Collections.emptyList();
            }
            // final ImmutableSortedMap<Integer, Bucket> lb = left.buckets();
            // final ImmutableSortedMap<Integer, Bucket> rb = right.buckets();
            final TreeSet<BucketIndex> childBucketIndexes;
            final Map<ObjectId, RevTree> trees;
            {
                TreeMap<Integer, BucketIndex> indices = new TreeMap<>();
                Set<ObjectId> lbucketIds = new HashSet<>();
                Set<ObjectId> rbucketIds = new HashSet<>();

                // index -> this.bucketIndex.append(index, left, right)
                Function<Integer, BucketIndex> fn =  new Function<Integer, BucketIndex>() {
                    @Override
                    public BucketIndex apply(Integer index) {
                        return bucketIndex.append(index, left, right);
                    }};


                left.forEachBucket(bucket -> {
                    lbucketIds.add(bucket.getObjectId());
                    indices.computeIfAbsent(Integer.valueOf(bucket.getIndex()),
                            fn);
                });
                right.forEachBucket(bucket -> {
                    rbucketIds.add(bucket.getObjectId());
                    indices.computeIfAbsent(Integer.valueOf(bucket.getIndex()),
                            fn);
                });
                childBucketIndexes = newTreeSet(indices.values());

                try {
                    trees = loadTrees(lbucketIds, rbucketIds);
                } catch (RuntimeException e) {
                    info.consumer.abortTraversal();
                    return Collections.emptyList();
                }
            }

            Optional<Bucket> lbucket;
            Optional<Bucket> rbucket;
            RevTree ltree, rtree;

            final List<WalkAction> tasks = new ArrayList<>();

            for (BucketIndex index : childBucketIndexes) {
                lbucket = left.getBucket(index.lastIndex().intValue());
                rbucket = right.getBucket(index.lastIndex().intValue());
                Preconditions.checkState(lbucket.isPresent() || rbucket.isPresent());

                if (!lbucket.equals(rbucket)) {
                    ltree = lbucket.isPresent() ? trees.get(lbucket.get().getObjectId()) : EMPTY;
                    rtree = rbucket.isPresent() ? trees.get(rbucket.get().getObjectId()) : EMPTY;
                    checkNotNull(ltree, "tree of %s not found ", lbucket);
                    checkNotNull(rtree, "tree of %s not found ", rbucket);

                    WalkAction task;
                    task = new TraverseBucketBucket(info, ltree, rtree, lbucket, rbucket, index);
                    tasks.add(task);
                }
            }

            if (info.consumer.isCancelled()) {
                return Collections.emptyList();
            }

            return tasks;
        }

        private Map<ObjectId, RevTree> loadTrees(final Set<ObjectId> lbucketIds,
                final Set<ObjectId> rbucketIds) {

            final Map<ObjectId, RevTree> trees;

            // get all buckets at once, to leverage ObjectStore optimizations
            if (info.left.source == info.right.source) {
                Set<ObjectId> ids = Sets.union(lbucketIds, rbucketIds);
                Iterator<RevTree> titer = info.left.source.getAll(ids, NOOP_LISTENER,
                        RevTree.class);
                trees = Streams.stream(titer).collect(Collectors.toMap(t -> t.getId(), t -> t));
            } else {
                trees = Streams
                        .stream(info.left.source.getAll(lbucketIds, NOOP_LISTENER, RevTree.class))
                        .collect(Collectors.toMap(t -> t.getId(), t -> t));

                // avoid re-fetching objects at both sides
                Set<ObjectId> missingAtRight = Sets.difference(rbucketIds, lbucketIds);
                trees.putAll(Streams.stream(
                        info.right.source.getAll(missingAtRight, NOOP_LISTENER, RevTree.class))
                        .collect(Collectors.toMap(t -> t.getId(), t -> t)));

            }
            return trees;
        }

        /**
         * Compares a bucket tree at the right side of the comparison, and a the
         * {@link RevObjects#children() children} nodes of a leaf tree at the left side of the
         * comparison.
         * <p>
         * This happens when the right tree is much larger than the left tree
         * <p>
         * This traversal is symmetric to {@link #bucketLeaf} so be careful that any change made to
         * this method shall have a matching change at {@link #bucketLeaf}
         * 
         * @precondition {@code !right.buckets().isEmpty()}
         */
        protected List<WalkAction> leafBucket(Iterator<Node> leftc, RevTree left, RevTree right) {
            checkArgument(right.bucketsSize() > 0);

            final SortedMap<Integer, Bucket> rightBuckets = new TreeMap<>();
            right.forEachBucket(b -> rightBuckets.put(Integer.valueOf(b.getIndex()), b));

            final ListMultimap<Integer, Node> nodesByBucket = splitNodesToBucketsAtDepth(leftc,
                    bucketIndex);

            final SortedSet<BucketIndex> bucketIndexes = getChildBucketIndexes(rightBuckets,
                    nodesByBucket, left, right);

            if (info.consumer.isCancelled()) {
                return Collections.emptyList();
            }
            // get all buckets at once, to leverage ObjectStore optimizations
            final ObjectStore source = info.right.source;
            final Map<ObjectId, RevTree> bucketTrees = loadBucketTrees(source, rightBuckets);
            List<WalkAction> tasks = new ArrayList<>();

            for (BucketIndex childIndex : bucketIndexes) {
                Bucket rightBucket = rightBuckets.get(childIndex.lastIndex());

                // never returns null, but empty
                List<Node> leftNodes = nodesByBucket.get(childIndex.lastIndex());
                if (null == rightBucket) {
                    tasks.add(leafLeaf(leftNodes.iterator(), Collections.emptyIterator()));
                } else {
                    RevTree rightTree = bucketTrees.get(rightBucket.getObjectId());
                    TraverseLeafBucket task;
                    task = new TraverseLeafBucket(info, leftNodes.iterator(), left, rightBucket,
                            rightTree, childIndex);
                    tasks.add(task);
                }
            }

            if (info.consumer.isCancelled()) {
                return Collections.emptyList();
            }
            return tasks;
        }

        /**
         * Compares a bucket tree at the left side of the comparison, and a the
         * {@link RevObjects#children() children} nodes of a leaf tree at the right side of the
         * comparison.
         * <p>
         * This happens when the left tree is much larger than the right tree
         * <p>
         * This traversal is symmetric to {@link #leafBucket} so be careful that any change made to
         * this method shall have a matching change at {@link #leafBucket}
         * 
         * @precondition {@code !left.buckets().isEmpty()}
         */
        protected List<WalkAction> bucketLeaf(RevTree left, RevTree rightLeaf,
                Iterator<Node> rightc) {
            checkArgument(left.bucketsSize() > 0);

            final SortedMap<Integer, Bucket> leftBuckets = new TreeMap<>();
            left.forEachBucket(b -> leftBuckets.put(Integer.valueOf(b.getIndex()), b));

            final ListMultimap<Integer, Node> nodesByBucket = splitNodesToBucketsAtDepth(rightc,
                    bucketIndex);

            final SortedSet<BucketIndex> bucketIndexes = getChildBucketIndexes(leftBuckets,
                    nodesByBucket, left, rightLeaf);

            if (info.consumer.isCancelled()) {
                return Collections.emptyList();
            }
            // get all buckets at once, to leverage ObjectStore optimizations
            final ObjectStore source = info.left.source;
            final Map<ObjectId, RevTree> bucketTrees = loadBucketTrees(source, leftBuckets);
            List<WalkAction> tasks = new ArrayList<>();

            for (BucketIndex childIndex : bucketIndexes) {
                Bucket leftBucket = leftBuckets.get(childIndex.lastIndex());

                // never returns null, but empty
                List<Node> rightNodes = nodesByBucket.get(childIndex.lastIndex());
                if (null == leftBucket) {
                    tasks.add(leafLeaf(Collections.emptyIterator(), rightNodes.iterator()));
                } else {
                    RevTree leftTree = bucketTrees.get(leftBucket.getObjectId());
                    TraverseBucketLeaf task = new TraverseBucketLeaf(info, leftBucket, leftTree,
                            rightNodes.iterator(), rightLeaf, childIndex);
                    tasks.add(task);
                }
            }

            if (info.consumer.isCancelled()) {
                return Collections.emptyList();
            }
            return tasks;
        }

        private Map<ObjectId, RevTree> loadBucketTrees(final ObjectStore source,
                final SortedMap<Integer, Bucket> buckets) {
            final Map<ObjectId, RevTree> bucketTrees;
            {

                //Bucket::getObjectId, but friendly for Fortify
                Function<Bucket, ObjectId> fn_bucket_getObjectId =  new Function<Bucket, ObjectId>() {
                    @Override
                    public ObjectId apply(Bucket bucket) {
                        return bucket.getObjectId();
                    }};

                Iterable<ObjectId> ids = transform(buckets.values(), fn_bucket_getObjectId);
                bucketTrees = Streams.stream(source.getAll(ids, NOOP_LISTENER, RevTree.class))
                        .collect(Collectors.toMap(t -> t.getId(), t -> t));
            }
            return bucketTrees;
        }

        private SortedSet<BucketIndex> getChildBucketIndexes(
                final SortedMap<Integer, Bucket> treeBuckets,
                final ListMultimap<Integer, Node> leafTreeNodesByBucket, RevTree left,
                RevTree right) {

            final SortedSet<BucketIndex> bucketIndexes;

            Set<Integer> childIndexes = Sets.union(treeBuckets.keySet(),
                    leafTreeNodesByBucket.keySet());

            // (i) -> this.bucketIndex.append(i, left, right)
            Function<Integer, BucketIndex> fn_append =  new Function<Integer, BucketIndex>() {
                @Override
                public BucketIndex apply(Integer i) {
                    return bucketIndex.append(i, left, right);
                }};

            Iterable<BucketIndex> childPaths = Iterables.transform(childIndexes,
                    fn_append);
            bucketIndexes = Sets.newTreeSet(childPaths);

            return bucketIndexes;
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
        protected final WalkAction node(@Nullable final NodeRef left,
                @Nullable final NodeRef right) {
            if (info.consumer.isCancelled()) {
                return null;
            }
            checkState(left != null || right != null, "both nodes can't be null");
            checkArgument(!Objects.equal(left, right));

            final TYPE type = left == null ? right.getType() : left.getType();

            if (TYPE.FEATURE.equals(type)) {
                if (info.reportFeatures) {
                    info.consumer.feature(left, right);
                }
                return null;
            }

            checkState(TYPE.TREE.equals(type));
            return traverseTree(left, right);
        }

        /**
         * Split the given nodes into lists keyed by the bucket index they would belong if they were
         * part of a tree bucket at the given {@code bucketDepth}
         */
        protected final ListMultimap<Integer, Node> splitNodesToBucketsAtDepth(Iterator<Node> nodes,
                final BucketIndex parentIndex) {

            Function<Node, Integer> keyFunction = node -> Integer
                    .valueOf(info.nodeOrder.bucket(node, parentIndex.depthIndex() + 1));

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
     * @param leftBucketTree the tree at the left side of the comparison
     * @param rightBucketTree the tree at the right side of the comparison
     * @param bucketDepth the depth of bucket traversal (only non zero if comparing two bucket
     *        trees, as when called from {@link #handleBucketBucket})
     * @precondition {@code left != null && right != null}
     */
    @SuppressWarnings("serial")
    private static class TraverseTree extends WalkAction {

        public TraverseTree(WalkInfo walkInfo) {
            super(walkInfo);
        }

        @Override
        protected void compute() {
            final @Nullable NodeRef leftNode = info.left.parentRef;
            final @Nullable NodeRef rightNode = info.right.parentRef;
            if (Objects.equal(leftNode, rightNode)) {
                return;
            }
            if (info.consumer.isCancelled()) {
                return;
            }
            if (info.consumer.tree(leftNode, rightNode)) {
                RevTree left;
                RevTree right;
                left = leftNode == null || RevTree.EMPTY_TREE_ID.equals(leftNode.getObjectId())
                        ? RevTree.EMPTY
                        : info.left.source.getTree(leftNode.getObjectId());
                right = rightNode == null || RevTree.EMPTY_TREE_ID.equals(rightNode.getObjectId())
                        ? RevTree.EMPTY
                        : info.right.source.getTree(rightNode.getObjectId());

                if (info.reportFeatures || (left.numTrees() > 0 || right.numTrees() > 0)) {
                    TraverseTreeContents traverseTreeContents = new TraverseTreeContents(info, left,
                            right, BucketIndex.root(left, right));

                    traverseTreeContents.compute();
                }
            }
            info.consumer.endTree(leftNode, rightNode);
        }
    }

    @SuppressWarnings("serial")
    private static class TraverseTreeContents extends WalkAction {

        private final RevTree left, right;

        public TraverseTreeContents(WalkInfo info, RevTree left, RevTree right,
                BucketIndex bucketIndex) {
            super(info, bucketIndex);
            checkNotNull(left);
            checkNotNull(right);
            this.left = left;
            this.right = right;
        }

        @Override
        protected void compute() {
            if (Objects.equal(left, right)) {
                return;
            }
            if (info.consumer.isCancelled()) {
                return;
            }
            // Possible cases:
            // 1- left and right are leaf trees
            // 2- left and right are bucket trees
            // 3- left is leaf and right is bucketed
            // 4- left is bucketed and right is leaf
            final boolean leftIsLeaf = left.bucketsSize() == 0;
            final boolean rightIsLeaf = right.bucketsSize() == 0;
            Iterator<Node> leftc = leftIsLeaf ? RevObjects.children(left, info.nodeOrder) : null;
            Iterator<Node> rightc = rightIsLeaf ? RevObjects.children(right, info.nodeOrder) : null;

            List<WalkAction> tasks = new ArrayList<>();
            if (leftIsLeaf && rightIsLeaf) {// 1-

                leafLeaf(leftc, rightc).compute();

            } else if (!(leftIsLeaf || rightIsLeaf)) {// 2-

                tasks.addAll(bucketBucket(left, right));

            } else if (leftIsLeaf) {// 3-

                tasks.addAll(leafBucket(leftc, left, right));

            } else {// 4-

                tasks.addAll(bucketLeaf(left, right, rightc));
            }

            if (!info.consumer.isCancelled()) {
                invokeAll(tasks);
            }
        }

    }

    @SuppressWarnings("serial")
    private static class TraverseLeafLeaf extends WalkAction {

        private Iterator<Node> left;

        private Iterator<Node> right;

        TraverseLeafLeaf(WalkInfo info, Iterator<Node> leftChildren, Iterator<Node> rightChildren) {
            super(info);
            this.left = leftChildren;
            this.right = rightChildren;
        }

        /**
         * Traverse and compare the {@link RevObjects#children() children} nodes of two leaf trees,
         * calling {@link #node(Consumer, Node, Node)} for each diff.
         */
        @Override
        protected void compute() {
            if (info.consumer.isCancelled()) {
                return;
            }
            PeekingIterator<Node> li = Iterators.peekingIterator(left);
            PeekingIterator<Node> ri = Iterators.peekingIterator(right);

            List<WalkAction> tasks = new ArrayList<>();

            final NodeRef leftParent = info.left.parentRef;
            final NodeRef rightParent = info.right.parentRef;
            while (li.hasNext() && ri.hasNext() && !info.consumer.isCancelled()) {
                final Node lpeek = li.peek();
                final Node rpeek = ri.peek();
                final int order = info.nodeOrder.compare(lpeek, rpeek);
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

            checkState(info.consumer.isCancelled() || !li.hasNext() || !ri.hasNext(),
                    "either the left or the right iterator should have been fully consumed");

            // right fully consumed, any remaining node in left is a removal
            while (!info.consumer.isCancelled() && li.hasNext()) {
                WalkAction action = node(newRef(leftParent, li.next()), null);
                if (action != null) {
                    tasks.add(action);
                }
            }

            // left fully consumed, any remaining node in right is an add
            while (!info.consumer.isCancelled() && ri.hasNext()) {
                WalkAction action = node(null, newRef(rightParent, ri.next()));
                if (action != null) {
                    tasks.add(action);
                }
            }

            if (!info.consumer.isCancelled()) {
                invokeAll(tasks);
            }
        }

        private NodeRef newRef(NodeRef parent, Node lnode) {
            return NodeRef.create(parent.path(), lnode, parent.getMetadataId());
        }

    }

    @SuppressWarnings("serial")
    private static class TraverseBucketBucket extends TraverseTreeContents {

        private final Bucket leftBucket, rightBucket;

        /**
         * @param info
         * @param leftTree
         * @param rightTree
         * @param bucketIndex if {@code null}, the trees are top level, otherwise they're the inner
         *        trees at the specified bucket index
         */
        TraverseBucketBucket(WalkInfo info, RevTree leftTree, RevTree rightTree,
                Optional<Bucket> leftBucket, Optional<Bucket> rightBucket,
                final BucketIndex bucketIndex) {
            super(info, leftTree, rightTree, bucketIndex);
            checkArgument(leftBucket.isPresent() || rightBucket.isPresent());
            checkArgument(!leftBucket.equals(rightBucket));

            this.leftBucket = leftBucket.orElse(null);
            this.rightBucket = rightBucket.orElse(null);
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
         * @param leftBucketTree the bucket tree at the left side of the comparison
         * @param rightBucketTree the bucket tree at the right side of the comparison
         * @param bucketDepth the current depth at which the comparison is evaluating these two
         *        bucket trees
         * @see #traverseTree(Consumer, RevTree, RevTree, int)
         * @precondition {@code !left.equals(right)}
         * @precondition {@code left.isEmpty() || left.buckets().isPresent()}
         * @precondition {@code right.isEmpty() || right.buckets().isPresent()}
         */
        @Override
        protected void compute() {
            if (info.consumer.isCancelled()) {
                return;
            }
            if (!info.reportFeatures && super.left.numTrees() == 0 && super.right.numTrees() == 0) {
                return;
            }
            final NodeRef leftParent = info.left.parentRef;
            final NodeRef rightParent = info.right.parentRef;
            final BucketIndex index = super.bucketIndex;

            if (info.consumer.bucket(leftParent, rightParent, index, leftBucket, rightBucket)) {
                super.compute();
            }
            info.consumer.endBucket(leftParent, rightParent, index, leftBucket, rightBucket);
        }
    }

    @SuppressWarnings("serial")
    private static class TraverseBucketLeaf extends WalkAction {

        private RevTree leftTree;

        private Bucket leftBucket;

        private Iterator<Node> rightNodes;

        private RevTree rightLeafTree;

        TraverseBucketLeaf(WalkInfo info, Bucket leftBucket, RevTree bucketTree,
                Iterator<Node> rightcChildren, RevTree rightLeafTree, BucketIndex bucketIndex) {
            super(info, bucketIndex);
            this.leftBucket = leftBucket;
            this.leftTree = bucketTree;
            this.rightNodes = rightcChildren;
            this.rightLeafTree = rightLeafTree;
        }

        /**
         * Compares a bucket tree at the left side of the comparison, and a the
         * {@link RevObjects#children() children} nodes of a leaf tree at the right side of the
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
            final CancellableConsumer consumer = info.consumer;
            if (consumer.isCancelled()) {
                return;
            }

            final NodeRef leftParent = info.left.parentRef;
            final NodeRef rightParent = info.right.parentRef;
            final BucketIndex index = super.bucketIndex;
            if (consumer.bucket(leftParent, rightParent, index, leftBucket, null)) {
                if (rightNodes.hasNext()) {
                    if (leftTree.bucketsSize() == 0) {
                        Iterator<Node> children;
                        children = RevObjects.children(leftTree, info.nodeOrder);
                        TraverseLeafLeaf task = leafLeaf(children, rightNodes);
                        task.compute();
                    } else {
                        List<WalkAction> tasks = bucketLeaf(leftTree, rightLeafTree, rightNodes);
                        invokeAll(tasks);
                    }
                } else {
                    TraverseTreeContents task = traverseTreeContents(leftTree, RevTree.EMPTY);
                    task.compute();
                }
            }
            consumer.endBucket(leftParent, rightParent, index, leftBucket, null);
        }
    }

    @SuppressWarnings("serial")
    private static class TraverseLeafBucket extends WalkAction {

        private Iterator<Node> leftNodes;

        private RevTree rightTree;

        private Bucket rightBucket;

        private RevTree leftTree;

        TraverseLeafBucket(WalkInfo info, Iterator<Node> leftChildren, RevTree leftLeafTree,
                Bucket rightBucket, RevTree rightTree, BucketIndex bucketIndex) {
            super(info, bucketIndex);
            this.leftNodes = leftChildren;
            this.leftTree = leftLeafTree;
            this.rightBucket = rightBucket;
            this.rightTree = rightTree;
        }

        /**
         * Compares a bucket tree at the right side of the comparison, and a the
         * {@link RevObjects#children() children} nodes of a leaf tree at the left side of the
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
            final CancellableConsumer consumer = info.consumer;
            if (consumer.isCancelled()) {
                return;
            }

            final NodeRef leftParent = info.left.parentRef;
            final NodeRef rightParent = info.right.parentRef;
            final BucketIndex index = super.bucketIndex;
            if (consumer.bucket(leftParent, rightParent, index, null, rightBucket)) {
                if (leftNodes.hasNext()) {
                    if (rightTree.bucketsSize() == 0) {
                        Iterator<Node> children;
                        children = RevObjects.children(rightTree, info.nodeOrder);
                        TraverseLeafLeaf task = leafLeaf(leftNodes, children);
                        task.compute();
                    } else {
                        List<WalkAction> tasks = leafBucket(leftNodes, leftTree, rightTree);
                        invokeAll(tasks);
                    }
                } else {
                    TraverseTreeContents task = traverseTreeContents(RevTree.EMPTY, rightTree);
                    task.compute();
                }
            }
            consumer.endBucket(leftParent, rightParent, index, null, rightBucket);
        }
    }

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
     * the tree comparison in "prescribed storage order" as defined by {@link CanonicalNodeOrder}.
     */
    public static interface Consumer {

        /**
         * Called when either two leaf trees are being compared and a feature node have changed (i.e
         * neither {@code left} nor {@code right} is null, or a feature has been deleted (
         * {@code left} is null), or added ({@code right} is null).
         * 
         * @param left the feature node at the left side of the traversal; may be {@code null) in
         *        which case {@code right} has been added. @param right the feature node at the
         *        right side of the traversal; may be {@code null} in which case {@code left} has
         *        been removed. @return {@code false} if the WHOLE traversal shall be aborted,
         *        {@true} to continue traversing other features and trees. Note this differs from
         *        the return value of {@link #tree()} and {@link #bucket()} in that they only avoid
         *        the traversal of the indicated tree or bucket, not the whole
         *        traversal. @precondition {@code left != null || right != null} @precondition
         *        {@code if(left != null && right != null) then left.name() == right.name()}
         */
        public abstract boolean feature(@Nullable final NodeRef left,
                @Nullable final NodeRef right);

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
         * with either both buckets or one depending on the existence of buckets at the given index
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
                final BucketIndex bucketIndex, @Nullable final Bucket left,
                @Nullable final Bucket right);

        /**
         * Called once done with a {@link #bucket}, regardless of the returned value
         */
        public abstract void endBucket(NodeRef leftParent, NodeRef rightParent,
                final BucketIndex bucketIndex, @Nullable final Bucket left,
                @Nullable final Bucket right);
    }

    public static abstract class AbstractConsumer implements Consumer {
        @Override
        public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
            return true;
        }

        @Override
        public boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
            return true;
        }

        @Override
        public void endTree(@Nullable NodeRef left, @Nullable NodeRef right) {
            //
        }

        @Override
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                @Nullable Bucket left, @Nullable Bucket right) {
            return true;
        }

        @Override
        public void endBucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                @Nullable Bucket left, @Nullable Bucket right) {
            //
        }

    }

    /**
     * Template class for consumer decorators, forwards all event calls to the provided consumer;
     * concrete subclasses shall override the event methods of their interest.
     */
    public static abstract class ForwardingConsumer implements Consumer {

        protected Consumer delegate;

        public ForwardingConsumer() {
            this.delegate = new AbstractConsumer() {
            };
        }

        public ForwardingConsumer(final Consumer delegate) {
            this.delegate = delegate;
        }

        public void setDelegate(Consumer delegate) {
            Preconditions.checkNotNull(delegate);
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
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                Bucket left, Bucket right) {
            return delegate.bucket(leftParent, rightParent, bucketIndex, left, right);
        }

        @Override
        public void endBucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                Bucket left, Bucket right) {
            delegate.endBucket(leftParent, rightParent, bucketIndex, left, right);
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
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                Bucket left, Bucket right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                return super.bucket(leftParent, rightParent, bucketIndex, left, right);
            }
            return false;
        }

        @Override
        public void endBucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                Bucket left, Bucket right) {
            if (predicate.apply(left) || predicate.apply(right)) {
                super.endBucket(leftParent, rightParent, bucketIndex, left, right);
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
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                Bucket left, Bucket right) {
            return !isCancelled()
                    && delegate.bucket(leftParent, rightParent, bucketIndex, left, right);
        }
    }
}
