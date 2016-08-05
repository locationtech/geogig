/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.experimental.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.experimental.internal.DAG.STATE;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

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

    private static final ExecutorService BUILDER_POOL;

    static final ListeningExecutorService STORAGE_EXECUTOR;

    static {
        final int parallelism = 2;// Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

        ThreadFactory builderFactory = new ThreadFactoryBuilder().setNameFormat("DAGTreeBuilder-%d")
                .setDaemon(true).build();

        BUILDER_POOL = MoreExecutors.sameThreadExecutor();
        // BUILDER_POOL = new ThreadPoolExecutor(parallelism,//
        // parallelism,//
        // 0L,//
        // TimeUnit.MILLISECONDS,//
        // new ArrayBlockingQueue<Runnable>(parallelism),//
        // builderFactory,//
        // new ThreadPoolExecutor.CallerRunsPolicy());

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("DAGTreeBuilder-Storage-%d").setDaemon(true).build();
        STORAGE_EXECUTOR = MoreExecutors
                .listeningDecorator(Executors.newFixedThreadPool(parallelism, threadFactory));
    }

    private static class SharedState {

        public final ExecutorService executor;

        public final ProgressListener listener;

        public final ObjectStore targetStore;

        public final ConcurrentMap<ObjectId, RevTree> newTrees;

        public final ClusteringStrategy clusteringStrategy;

        public final List<Future<?>> futures = new ArrayList<>();// new CopyOnWriteArrayList<>();

        SharedState(final ObjectStore targetStore, final ClusteringStrategy clusteringStrategy,
                final ExecutorService executor, final ProgressListener listener) {
            this.executor = executor;
            this.listener = listener;
            this.newTrees = new ConcurrentSkipListMap<ObjectId, RevTree>(ObjectId.NATURAL_ORDER);
            this.targetStore = targetStore;
            this.clusteringStrategy = clusteringStrategy;
        }

        public @Nullable NodeId computeId(Node node) {
            return clusteringStrategy.computeId(node);
        }

        public void addNewTree(RevTree tree) {
            // leaf tree nodes tend to be much bigger than non leaves, save them directly
            // if (tree.features().isPresent()) {
            // targetStore.put(tree);
            // } else {
            newTrees.put(tree.getId(), tree);
            saveTrees(1_000);
            // }
        }

        public SharedState saveTrees(final int minSize) {
            synchronized (newTrees) {
                if (newTrees.size() < minSize) {
                    return this;
                }
                return saveTrees();
            }
        }

        public RevTree getTree(ObjectId treeId) {
            if (RevTreeBuilder.EMPTY_TREE_ID.equals(treeId)) {
                return RevTreeBuilder.EMPTY;
            }
            RevTree tree = newTrees.get(treeId);
            if (tree == null) {
                tree = targetStore.getTree(treeId);
            }
            return tree;
        }

        public SharedState saveTrees() {
            final List<RevTree> toSave = ImmutableList.copyOf(newTrees.values());
            newTrees.clear();
            // targetStore.putAll(toSave.iterator());
            // System.err.printf("Saved %,d new trees\n", toSave.size());
            if (!toSave.isEmpty()) {
                // System.err.printf(" %s --> Saving %,d new trees...\n", Thread.currentThread()
                // .getName(), toSave.size());
                ListenableFuture<?> future = STORAGE_EXECUTOR
                        .submit(() -> targetStore.putAll(toSave.iterator()));
                futures.add(future);

            }
            return this;
        }

        public void awaitTermination() {
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static RevTree build(final ClusteringStrategy clusteringStrategy,
            final ObjectStore targetStore) {

        ExecutorService executor = BUILDER_POOL;
        ProgressListener listener = new DefaultProgressListener();

        SharedState state = new SharedState(targetStore, clusteringStrategy, executor, listener);

        TreeBuildTask task = new TreeBuildTask(state, clusteringStrategy.getRoot(), 0);

        // Future<RevTree> futureTree = FORK_JOIN_POOL.submit(task);

        RevTree tree;
        try {
            // tree = futureTree.get();
            tree = task.call();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            state.saveTrees().awaitTermination();
        }

        return tree;
    }

    private static class TreeBuildTask implements Callable<RevTree> {

        private final DAG root;

        private final SharedState state;

        private final int depth;

        public TreeBuildTask(final SharedState state, final DAG root, final int depth) {
            this.state = state;
            this.root = root;
            this.depth = depth;
        }

        @Override
        public RevTree call() {

            final RevTree result;

            try {
                final DAG root = this.root;
                if (root.getState().equals(STATE.CHANGED)) {

                    final @Nullable Set<TreeId> dagBuckets = root.buckets();

                    if (dagBuckets == null || dagBuckets.isEmpty()) {
                        result = buildLeafTree(root);
                    } else {
                        result = buildBucketsTree(root);
                    }
                } else {
                    ObjectId treeId = root.treeId;
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

            final Set<TreeId> dagBuckets = root.buckets();
            Preconditions.checkNotNull(dagBuckets);

            Map<Integer, Future<RevTree>> subtasks = new HashMap<>();

            for (TreeId dagBucketId : dagBuckets) {
                Integer bucketIndex = dagBucketId.bucketIndex(depth);
                DAG bucketDAG = this.state.clusteringStrategy.getTree(dagBucketId);
                TreeBuildTask subtask = new TreeBuildTask(state, bucketDAG, this.depth + 1);

                Future<RevTree> future;
                try {
                    future = state.executor.submit(subtask);
                } catch (RejectedExecutionException rejected) {
                    RevTree tree = subtask.call();
                    future = Futures.immediateFuture(tree);
                }
                subtasks.put(bucketIndex, future);
            }

            long size = 0;
            int childTreeCount = 0;

            ImmutableSortedMap.Builder<Integer, Bucket> bucketsByIndex;
            bucketsByIndex = ImmutableSortedMap.naturalOrder();

            for (Map.Entry<Integer, Future<RevTree>> e : subtasks.entrySet()) {
                Integer bucketIndex = e.getKey();
                Future<RevTree> task = e.getValue();
                RevTree bucketTree;
                try {
                    bucketTree = task.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw Throwables.propagate(ex);
                }

                if (!bucketTree.isEmpty()) {

                    size += bucketTree.size();
                    childTreeCount += bucketTree.numTrees();

                    Bucket bucket = Bucket.create(bucketTree.getId(),
                            SpatialOps.boundsOf(bucketTree));

                    bucketsByIndex.put(bucketIndex, bucket);
                }
            }

            final ImmutableList<Node> unpromotable = toNodes(root.unpromotable());

            ImmutableSortedMap<Integer, Bucket> buckets = bucketsByIndex.build();
            ImmutableList<Node> treeNodes = null;
            ImmutableList<Node> featureNodes = unpromotable;

            RevTree result = RevTreeBuilder.build(size, childTreeCount, treeNodes, featureNodes,
                    buckets);
            return result;
        }

        private RevTree buildLeafTree(DAG root) {
            final Set<NodeId> unpromotable = root.unpromotable();
            Preconditions.checkState(null == unpromotable || unpromotable.isEmpty());

            final ImmutableList<Node> children = toNodes(root.children());

            // filter out removed nodes

            return buildLeafTree(children);
        }

        private RevTree buildLeafTree(ImmutableList<Node> promotable) {

            ImmutableList<Node> treesList = ImmutableList.copyOf(Iterables.filter(promotable,
                    n -> n.getType().equals(TYPE.TREE) && !n.getObjectId().isNull()));

            ImmutableList<Node> featuresList = ImmutableList.copyOf(Iterables.filter(promotable,
                    n -> n.getType().equals(TYPE.FEATURE) && !n.getObjectId().isNull()));

            final long size = sumTreeSizes(treesList) + featuresList.size();

            final int childTreeCount = treesList.size();

            ImmutableSortedMap<Integer, Bucket> buckets = null;

            RevTree tree = RevTreeBuilder.build(size, childTreeCount, treesList, featuresList,
                    buckets);

            return tree;

        }

        private ImmutableList<Node> toNodes(final @Nullable Set<NodeId> nodeIds) {
            if (null == nodeIds) {
                return ImmutableList.of();
            }

            SortedSet<NodeId> sorted = ImmutableSortedSet.copyOf(nodeIds);

            ImmutableList<Node> nodes = ImmutableList.copyOf(
                    Iterables.transform(sorted, (id) -> state.clusteringStrategy.getNode(id)));

            return nodes;
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
