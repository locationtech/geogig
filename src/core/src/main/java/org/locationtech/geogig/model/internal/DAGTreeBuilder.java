/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevTreeBuilder;
import org.locationtech.geogig.model.internal.DAG.STATE;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;

/**
 * Builds a {@link RevTree} (immutable data structure) out of a {@link DAG} (mutable data
 * structure).
 * <p>
 * This class doesn't make structural modifications on the DAG, simply creates the immutable
 * {@link RevTree} out of the {@link DAG} structure in a bottom-up (depth-first) way.
 * <p>
 * {@link ClusteringStrategy} is responsible of creating the appropriate structure.
 */
public class DAGTreeBuilder {

    private static final ForkJoinPool FORK_JOIN_POOL;

    static {
        ForkJoinPool.ForkJoinWorkerThreadFactory threadFactoryShared = pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory
                    .newThread(pool);
            worker.setName("DAGTreeBuilder-shared-" + worker.getPoolIndex());
            return worker;
        };

        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors());
        UncaughtExceptionHandler eh = (t, e) -> {
            System.err.println("Uncaught ForkJoinPool exception at thread " + t.getName());
            e.printStackTrace();
        };
        FORK_JOIN_POOL = new ForkJoinPool(parallelism, threadFactoryShared, eh, false);
    }

    private static class SharedState {
        public final ObjectStore targetStore;

        private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

        private final Map<ObjectId, RevTree> newTrees;

        public final ClusteringStrategy clusteringStrategy;

        private final BooleanSupplier externalCancelFlag;

        private final AtomicBoolean internalAbortFlag = new AtomicBoolean();

        SharedState(final ObjectStore targetStore, final ClusteringStrategy clusteringStrategy,
                BooleanSupplier abortFlag) {
            this.externalCancelFlag = abortFlag;
            this.newTrees = new HashMap<>();
            this.targetStore = targetStore;
            this.clusteringStrategy = clusteringStrategy;
        }

        public @Nullable NodeId computeId(Node node) {
            return clusteringStrategy.computeId(node);
        }

        public void addNewTree(RevTree tree) {
            cacheLock.writeLock().lock();
            try {
                newTrees.put(tree.getId(), tree);
                saveTrees(1_000);
            } finally {
                cacheLock.writeLock().unlock();
            }
        }

        public SharedState saveTrees() {
            return saveTrees(0);
        }

        public SharedState saveTrees(final int minSize) {
            cacheLock.writeLock().lock();
            try {
                if (!isCancelled()) {
                    if (newTrees.size() >= minSize) {
                        targetStore.putAll(newTrees.values().iterator());
                        newTrees.clear();
                    }
                }
            } finally {
                cacheLock.writeLock().unlock();
            }
            return this;
        }

        public RevTree getTree(ObjectId treeId) {
            if (isCancelled()) {
                return null;
            }
            if (RevTree.EMPTY_TREE_ID.equals(treeId)) {
                return RevTree.EMPTY;
            }

            RevTree tree;
            cacheLock.readLock().lock();
            try {
                tree = newTrees.get(treeId);
            } finally {
                cacheLock.readLock().unlock();
            }
            if (tree == null) {
                try {
                    tree = targetStore.getTree(treeId);
                } catch (RuntimeException e) {
                    if (!isCancelled()) {
                        throw e;
                    }
                }
            }
            return tree;
        }

        public boolean isCancelled() {
            boolean externalCancelRequest = externalCancelFlag.getAsBoolean();
            boolean internalAbortRequest = internalAbortFlag.get();
            return externalCancelRequest || internalAbortRequest;
        }

        public void abort() {
            internalAbortFlag.set(true);
        }
    }

    public static RevTree build(final ClusteringStrategy clusteringStrategy,
            final ObjectStore targetStore) {
        return build(clusteringStrategy, targetStore, () -> false);
    }

    public static @Nullable RevTree build(final ClusteringStrategy clusteringStrategy,
            final ObjectStore targetStore, final BooleanSupplier abortFlag) {
        checkNotNull(clusteringStrategy);
        checkNotNull(targetStore);
        checkNotNull(abortFlag);

        SharedState state = new SharedState(targetStore, clusteringStrategy, abortFlag);

        final DAG root = clusteringStrategy.buildRoot();

        final TreeId rootId = root.getId();
        final int baseDepth = rootId.depthLength();
        TreeBuildTask task = new TreeBuildTask(state, root, baseDepth);

        @Nullable
        RevTree tree;
        try {
            ForkJoinPool forkJoinPool = FORK_JOIN_POOL;
            tree = forkJoinPool.invoke(task);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        if (state.isCancelled()) {
            return null;
        }
        state.saveTrees();

        return tree;
    }

    @SuppressWarnings("serial")
    private static class TreeBuildTask extends RecursiveTask<RevTree> {

        private final DAG root;

        private final SharedState state;

        private final int depth;

        public TreeBuildTask(final SharedState state, final DAG root, final int depth) {
            this.state = state;
            this.root = root;
            this.depth = depth;
        }

        @Override
        protected RevTree compute() {
            final RevTree result;
            if (state.isCancelled()) {
                return null;
            }
            try {
                final DAG root = this.root;
                final STATE rootState = root.getState();
                if (rootState.equals(STATE.CHANGED)) {
                    if (0 == root.numBuckets()) {
                        result = buildLeafTree(root);
                    } else {
                        result = buildBucketsTree(root);
                    }
                } else {
                    checkState(rootState == STATE.INITIALIZED || rootState == STATE.MIRRORED);
                    ObjectId treeId = root.originalTreeId();
                    if (state.isCancelled()) {
                        result = null;
                    } else {
                        result = state.getTree(treeId);
                    }
                }
            } catch (RuntimeException | InterruptedException | ExecutionException e) {
                state.abort();// let any other running task abort asap
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
            if (!state.isCancelled()) {
                state.addNewTree(result);
            }
            return result;
        }

        private RevTree buildBucketsTree(final DAG root)
                throws InterruptedException, ExecutionException {

            final List<DAG> mutableBuckets;
            {
                final Set<TreeId> dagBuckets = new HashSet<>();
                root.forEachBucket((b) -> dagBuckets.add(b));
                checkNotNull(dagBuckets);
                mutableBuckets = this.state.clusteringStrategy.getDagTrees(dagBuckets);
                checkState(dagBuckets.size() == mutableBuckets.size());
            }
            if (state.isCancelled()) {
                return null;
            }
            Map<Integer, ForkJoinTask<RevTree>> subtasks = new HashMap<>();

            for (DAG bucketDAG : mutableBuckets) {
                final TreeId dagBucketId = bucketDAG.getId();

                final Integer bucketIndex = dagBucketId.bucketIndex(depth);
                TreeBuildTask subtask = new TreeBuildTask(state, bucketDAG, this.depth + 1);
                subtasks.put(bucketIndex, subtask);
            }

            if (state.isCancelled()) {
                return null;
            }

            // forks all subtasks and return when they're all done
            invokeAll(subtasks.values());

            long size = 0;
            int childTreeCount = 0;

            ImmutableSortedMap.Builder<Integer, Bucket> bucketsByIndex;
            bucketsByIndex = ImmutableSortedMap.naturalOrder();

            for (Entry<Integer, ForkJoinTask<RevTree>> e : subtasks.entrySet()) {

                Integer bucketIndex = e.getKey();
                ForkJoinTask<RevTree> task = e.getValue();

                RevTree bucketTree = task.get();

                if (state.isCancelled()) {
                    return null;
                }

                if (!bucketTree.isEmpty()) {

                    size += bucketTree.size();
                    childTreeCount += bucketTree.numTrees();

                    Bucket bucket = Bucket.create(bucketTree.getId(),
                            SpatialOps.boundsOf(bucketTree));

                    bucketsByIndex.put(bucketIndex, bucket);
                }
            }

            ImmutableSortedMap<Integer, Bucket> buckets = bucketsByIndex.build();
            ImmutableList<Node> treeNodes = null;
            ImmutableList<Node> featureNodes = null;

            RevTree result = RevTreeBuilder.build(size, childTreeCount, treeNodes, featureNodes,
                    buckets);
            return state.isCancelled() ? null : result;
        }

        private RevTree buildLeafTree(DAG root) {
            if (state.isCancelled()) {
                return null;
            }
            checkState(root.numBuckets() == 0);

            final ImmutableList<Node> children;
            {
                Set<NodeId> childrenIds = new HashSet<>();
                root.forEachChild((id) -> childrenIds.add(id));
                children = toNodes(childrenIds);
            }

            ImmutableList<Node> treesList = ImmutableList.copyOf(Iterables.filter(children,
                    n -> n.getType().equals(TYPE.TREE) && !n.getObjectId().isNull()));

            ImmutableList<Node> featuresList = ImmutableList.copyOf(Iterables.filter(children,
                    n -> n.getType().equals(TYPE.FEATURE) && !n.getObjectId().isNull()));

            final long size = sumTreeSizes(treesList) + featuresList.size();

            final int childTreeCount = treesList.size();

            ImmutableSortedMap<Integer, Bucket> buckets = null;

            if (state.isCancelled()) {
                return null;
            }
            RevTree tree = RevTreeBuilder.build(size, childTreeCount, treesList, featuresList,
                    buckets);

            return tree;

        }

        private ImmutableList<Node> toNodes(Set<NodeId> nodeIds) {
            if (null == nodeIds) {
                return ImmutableList.of();
            }

            SortedMap<NodeId, Node> nodes = state.clusteringStrategy.getNodes(nodeIds);
            ImmutableList<Node> list = ImmutableList.copyOf(nodes.values());
            return list;
        }

        private long sumTreeSizes(Iterable<Node> trees) {
            long size = 0;
            for (Node n : trees) {
                RevTree tree = state.getTree(n.getObjectId());
                if (state.isCancelled()) {
                    return -1L;
                }
                size += tree.size();
            }
            return size;
        }
    }
}
