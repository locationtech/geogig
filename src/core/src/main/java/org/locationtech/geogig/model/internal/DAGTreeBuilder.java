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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevTreeBuilder;
import org.locationtech.geogig.model.internal.DAG.STATE;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;
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
        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors());
        FORK_JOIN_POOL = new ForkJoinPool(parallelism);
    }

    private static class SharedState {

        public final ProgressListener listener;

        public final ObjectStore targetStore;

        private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

        private final Map<ObjectId, RevTree> newTrees;

        public final ClusteringStrategy clusteringStrategy;

        SharedState(final ObjectStore targetStore, final ClusteringStrategy clusteringStrategy,
                final ProgressListener listener) {
            this.listener = listener;
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
                if (newTrees.size() >= minSize) {
                    targetStore.putAll(newTrees.values().iterator());
                    newTrees.clear();
                }
            } finally {
                cacheLock.writeLock().unlock();
            }
            return this;
        }

        public RevTree getTree(ObjectId treeId) {
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
                tree = targetStore.getTree(treeId);
            }
            return tree;
        }
    }

    public static RevTree build(final ClusteringStrategy clusteringStrategy,
            final ObjectStore targetStore) {

        // ExecutorService executor = BUILDER_POOL;
        ProgressListener listener = new DefaultProgressListener();

        SharedState state = new SharedState(targetStore, clusteringStrategy, listener);

        DAG root = clusteringStrategy.buildRoot();
        TreeId rootId = clusteringStrategy.getRootId();
        final int baseDepth = rootId.depthLength();
        TreeBuildTask task = new TreeBuildTask(state, root, baseDepth);

        RevTree tree;
        try {
            ForkJoinPool forkJoinPool = FORK_JOIN_POOL;
            tree = forkJoinPool.invoke(task);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            state.saveTrees();
        }

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

            try {
                final DAG root = this.root;
                if (root.getState().equals(STATE.CHANGED)) {
                    if (0 == root.numBuckets()) {
                        result = buildLeafTree(root);
                    } else {
                        result = buildBucketsTree(root);
                    }
                } else {
                    ObjectId treeId = root.originalTreeId;
                    result = state.getTree(treeId);
                }
            } catch (RuntimeException e) {
                state.listener.cancel();// let any other running task abort asap
                throw e;
            }

            state.addNewTree(result);
            return result;
        }

        private RevTree buildBucketsTree(final DAG root) {

            final Map<TreeId, DAG> mutableBuckets;
            {
                final Set<TreeId> dagBuckets = new HashSet<>();
                root.forEachBucket((b) -> dagBuckets.add(b));
                Preconditions.checkNotNull(dagBuckets);
                mutableBuckets = this.state.clusteringStrategy.getDagTrees(dagBuckets);
                Preconditions.checkState(dagBuckets.size() == mutableBuckets.size());
            }

            Map<Integer, ForkJoinTask<RevTree>> subtasks = new HashMap<>();

            for (Map.Entry<TreeId, DAG> e : mutableBuckets.entrySet()) {
                final TreeId dagBucketId = e.getKey();
                final DAG bucketDAG = e.getValue();

                final Integer bucketIndex = dagBucketId.bucketIndex(depth);
                TreeBuildTask subtask = new TreeBuildTask(state, bucketDAG, this.depth + 1);
                ForkJoinTask<RevTree> fork = subtask.fork();
                subtasks.put(bucketIndex, fork);
            }

            long size = 0;
            int childTreeCount = 0;

            ImmutableSortedMap.Builder<Integer, Bucket> bucketsByIndex;
            bucketsByIndex = ImmutableSortedMap.naturalOrder();

            for (Entry<Integer, ForkJoinTask<RevTree>> e : subtasks.entrySet()) {

                Integer bucketIndex = e.getKey();
                ForkJoinTask<RevTree> task = e.getValue();
                RevTree bucketTree = task.join();

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
            return result;
        }

        private RevTree buildLeafTree(DAG root) {
            Preconditions.checkState(root.numBuckets() == 0);

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
                size += tree.size();
            }
            return size;
        }
    }
}
