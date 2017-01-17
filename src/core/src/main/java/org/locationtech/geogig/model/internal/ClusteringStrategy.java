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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevTreeBuilder;
import org.locationtech.geogig.model.internal.DAG.STATE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;

/**
 * Base class for strategy objects that define the internal structure of a {@link RevTree}.
 * 
 * @apiNote instances of this class might hold references to temporary resources that need to be
 *          cleaned up after usage, hence the use of the {@link #dispose()} method is mandatory. In
 *          general, the {@link RevTreeBuilder} that's using this object will do so as part of its
 *          own clean up phase before returning from its own {@code build()} method.
 */
public abstract class ClusteringStrategy {

    private DAGStorageProvider storageProvider;

    private static final TreeId ROOT_ID = new TreeId(new byte[0]);

    protected DAG root;

    private TreeId rootId = ROOT_ID;

    @VisibleForTesting
    final DAGCache dagCache;

    protected ClusteringStrategy(RevTree original, DAGStorageProvider storageProvider) {
        checkNotNull(original);
        checkNotNull(storageProvider);
        this.storageProvider = storageProvider;
        this.root = new DAG(original.getId());
        this.root.setChildCount(original.size());
        this.dagCache = new DAGCache(storageProvider);
    }

    abstract int normalizedSizeLimit(final int depthIndex);

    /**
     * @return the {@link NodeId} that matches the given node
     */
    public @Nullable abstract NodeId computeId(Node node);

    /**
     * Computes the bucket a given {@link NodeId} lays into for a given tree depth.
     * 
     * @param depthIndex the tree depth for which to return the bucket index for this node
     * @return a positive integer (in the range of an unsigned byte value) or {@code -1} if this
     *         node can't be added at the specified depth, and hence the node shall be kept at the
     *         current tree node (hence creating a mixed {@link RevTree} with both direct children
     *         and buckets).
     */
    public abstract int bucket(NodeId nodeId, int depthIndex);

    /**
     * @return the bucket corresponding to {@code nodeId} at depth {@code depthIndex} as mandated by
     *         {@link CanonicalNodeNameOrder}
     */
    public final int canonicalBucket(final NodeId nodeId, final int depthIndex) {
        int bucket = CanonicalNodeNameOrder.bucket(nodeId.name(), depthIndex);
        return bucket;
    }

    DAG getOrCreateDAG(TreeId treeId) {
        return getOrCreateDAG(treeId, RevTree.EMPTY_TREE_ID);
    }

    private DAG getOrCreateDAG(TreeId treeId, ObjectId originalTreeId) {
        DAG dag = dagCache.getOrCreate(treeId, originalTreeId);
        return dag;
    }

    public Map<TreeId, DAG> getDagTrees(Set<TreeId> ids) {
        Map<TreeId, DAG> trees = dagCache.getAll(ids);
        return trees;
    }

    @VisibleForTesting
    Node getNode(NodeId nodeId) {
        SortedMap<NodeId, Node> nodes = getNodes(ImmutableSet.of(nodeId));
        return nodes.get(nodeId);
    }

    /**
     * Returns a set of {@link Node} objects by their {@link NodeId}s in the order imposed by the
     * clustering strategy, to be held as direct children of a leaf {@link RevTree}.
     * <p>
     * For a canonical tree, the order must follow the one imposed by
     * {@link CanonicalNodeNameOrder}.
     * <p>
     * For an index tree, the order is first mandated by the natural order of the index attribute
     * values, followed by canonical order.
     */
    public SortedMap<NodeId, Node> getNodes(Set<NodeId> nodeIds) {
        Map<NodeId, Node> nodes = storageProvider.getNodes(nodeIds);
        Comparator<NodeId> nodeOrdering = getNodeOrdering();
        TreeMap<NodeId, Node> sorted = new TreeMap<>(nodeOrdering);
        sorted.putAll(nodes);
        return sorted;
    }

    protected abstract Comparator<NodeId> getNodeOrdering();

    public void dispose() {
        this.dagCache.dispose();
        this.storageProvider.dispose();
    }

    public int depth() {
        return depth(buildRoot());
    }

    int depth(DAG root) {
        if (0 == root.numBuckets()) {
            return 0;
        }

        final AtomicInteger maxDepth = new AtomicInteger();// cause an int can't be used from inside
                                                           // the lambda
        root.forEachBucket((bucketId) -> {
            DAG bucket = getOrCreateDAG(bucketId);
            int bucketDepth = depth(bucket);
            maxDepth.set(Math.max(maxDepth.get(), bucketDepth));
        });
        return 1 + maxDepth.get();
    }

    public void remove(final String featureId) {
        Node removeNode = Node.create(featureId, ObjectId.NULL, ObjectId.NULL, TYPE.FEATURE, null);
        // put(ROOT_ID, this.root, removeNode);
        put(removeNode);
    }

    private Lock writeLock = new ReentrantLock();

    public void put(final Node node) {
        @Nullable
        final NodeId nodeId = computeId(node);
        // nodeId can be null if it's not to be added to the tree at all (e.g. a non spatial
        // feature in a spatial index)
        if (nodeId != null) {
            boolean remove = node.getObjectId().isNull();
            int rootDepth = rootId.depthLength();// usually zero, unless buildRoot() has already
                                                 // been called and it reset the root DAG to be
                                                 // _the_ single bucket root had (in case it had
                                                 // only one)
            writeLock.lock();
            try {
                put(rootId, root, nodeId, remove, rootDepth);
            } finally {
                writeLock.unlock();
            }
            if (!remove) {
                storageProvider.saveNode(nodeId, node);
            }
            // if (treeBuff.size() >= 100_000) {
            // writeLock.lock();
            // try {
            // System.err.printf("Saving %,d DAG's.....\n", treeBuff.size());
            // storageProvider.save(treeBuff);
            // treeBuff.clear();
            // } finally {
            // writeLock.unlock();
            // }
            // }
        }

    }

    public TreeId getRootId() {
        return rootId;
    }

    public DAG buildRoot() {
        while (1 == root.numBuckets()) {
            root.forEachBucket((treeId) -> {
                DAG actual = getOrCreateDAG(treeId);
                root = actual;
                rootId = treeId;
            });
        }

        return root;
    }

    private int put(final TreeId dagId, final DAG dag, final NodeId nodeId, final boolean remove,
            final int dagDepth) {
        checkNotNull(dag);
        checkNotNull(nodeId);
        checkArgument(dagDepth > -1);

        mergeRoot(dag, nodeId, dagDepth);

        boolean changed = false;
        final int deltaSize;
        final int normalizedSizeLimit = normalizedSizeLimit(dagDepth);

        if (dag.numBuckets() > 0) {
            final @Nullable TreeId bucketId = computeBucketId(nodeId, dagDepth);
            if (bucketId != null) {
                DAG bucketDAG = getOrCreateDAG(bucketId);
                dag.addBucket(bucketId);
                deltaSize = put(bucketId, bucketDAG, nodeId, remove, dagDepth + 1);
                changed = bucketDAG.getState() == STATE.CHANGED;
            } else {
                deltaSize = 0;
            }
        } else {
            if (remove) {
                deltaSize = dag.removeChild(nodeId) ? -1 : 0;
            } else {
                changed = true;// contents changed, independently of children.add return code
                deltaSize = dag.addChild(nodeId) ? +1 : 0;
            }

            final int size = dag.numChildren();

            if (size > normalizedSizeLimit) {
                dag.forEachChild((childId) -> {

                    int childDepthIndex = dagDepth + 1;
                    TreeId bucketId = computeBucketId(childId, dagDepth);
                    checkNotNull(bucketId);
                    DAG bucketDAG = getOrCreateDAG(bucketId);
                    dag.addBucket(bucketId);
                    put(bucketId, bucketDAG, childId, remove, childDepthIndex);
                    /// changed = bucketDAG.getState() == STATE.CHANGED;
                });

                dag.clearChildren();
            }
        }

        if (deltaSize != 0) {
            changed = true;
            dag.setChildCount(dag.getChildCount() + deltaSize);
            shrinkIfUnderflow(dag, nodeId, dagDepth);
        }
        if (changed) {
            dag.setChanged();
        }
        return deltaSize;
    }

    private void shrinkIfUnderflow(final DAG bucketsDAG, NodeId nodeId, int depth) {
        final long childCount = bucketsDAG.getChildCount();
        final int normalizedSizeLimit = normalizedSizeLimit(depth);

        if (childCount <= normalizedSizeLimit && bucketsDAG.numBuckets() > 0) {
            Set<NodeId> childrenRecursive = getChildrenRecursive(bucketsDAG, nodeId, depth);
            checkState(childrenRecursive.size() == childCount, "expected %s, got %s", childCount,
                    childrenRecursive.size());

            bucketsDAG.clearBuckets();
            childrenRecursive.forEach((id) -> bucketsDAG.addChild(id));
        }
    }

    private Set<NodeId> getChildrenRecursive(final DAG dag, final NodeId nodeId, final int depth) {

        Set<NodeId> children = new HashSet<>();
        dag.forEachChild((id) -> children.add(id));

        if (!children.isEmpty()) {
            return children;
        }

        dag.forEachBucket((bucketId) -> {
            DAG bucket = getOrCreateDAG(bucketId);
            mergeRoot(bucket, nodeId, depth);
            Set<NodeId> bucketChildren = getChildrenRecursive(bucket, nodeId, depth + 1);
            children.addAll(bucketChildren);
        });

        return children;
    }

    /**
     * Makes sure the DAG has the same structure than the original tree following the path to the
     * node (i.e.) loading only the {@link RevTree trees} necessary to reach the node being added.
     * 
     * @param root
     * @param nodeId
     * @param depthIndex
     * @return
     * @return
     */
    private void mergeRoot(DAG root, final NodeId nodeId, final int depthIndex) {
        checkNotNull(root);
        checkNotNull(nodeId);

        if (root.getState() == STATE.INITIALIZED) {

            final RevTree original = getOriginalTree(root.originalTreeId);
            root.setChildCount(original.size());

            final boolean originalIsLeaf = original.buckets().isEmpty();

            if (originalIsLeaf) {
                final Map<NodeId, DAGNode> origNodes = lazyNodes(original);
                if (!origNodes.isEmpty()) {
                    // TODO: avoid saving nodes already in RevTrees
                    storageProvider.saveNodes(origNodes);
                    origNodes.keySet().forEach((id) -> root.addChild(id));
                }

            } else {

                final TreeId nodeBucketId = computeBucketId(nodeId, depthIndex);
                final ImmutableSortedMap<Integer, Bucket> buckets = original.buckets();

                if (root.getState() == STATE.INITIALIZED) {
                    // make DAG a bucket tree
                    checkState(root.numChildren() == 0);

                    // initialize buckets
                    preload(buckets.values());
                    for (Entry<Integer, Bucket> e : buckets.entrySet()) {
                        Integer bucketIndex = e.getKey();
                        TreeId dagBucketId = computeBucketId(nodeBucketId, bucketIndex);
                        ObjectId bucketId = e.getValue().getObjectId();
                        getOrCreateDAG(dagBucketId, bucketId);// make sure the DAG exists and is
                        // initialized
                        root.addBucket(dagBucketId);
                    }
                }
            }
            root.setMirrored();
        }

    }

    private void preload(ImmutableCollection<Bucket> values) {
        this.storageProvider.getTreeCache()
                .preload(Iterables.transform(values, (b) -> b.getObjectId()));
    }

    private RevTree getOriginalTree(@Nullable ObjectId originalId) {
        final RevTree original;
        if (originalId == null || RevTree.EMPTY_TREE_ID.equals(originalId)) {
            original = RevTree.EMPTY;
        } else {
            // System.err.println("Loading tree " + originalId);
            original = storageProvider.getTree(originalId);
        }
        return original;
    }

    TreeId computeBucketId(final NodeId nodeId, final int childDepthIndex) {
        byte[] treeId = new byte[childDepthIndex + 1];

        int unpromotableDepth = -1;

        for (int depthIndex = 0; depthIndex <= childDepthIndex; depthIndex++) {
            int bucketIndex = bucket(nodeId, depthIndex);
            if (bucketIndex == -1) {
                unpromotableDepth = depthIndex;
                break;
            }
            treeId[depthIndex] = (byte) bucketIndex;
        }

        if (unpromotableDepth > -1) {
            final int extraBucketIndex = unpromotableBucketIndex(unpromotableDepth);
            treeId[unpromotableDepth] = (byte) extraBucketIndex;
            unpromotableDepth++;
            final int missingDepthCount = 1 + childDepthIndex - unpromotableDepth;
            for (int i = 0; i < missingDepthCount; i++, unpromotableDepth++) {
                int bucketIndex = canonicalBucket(nodeId, i);
                treeId[unpromotableDepth] = (byte) bucketIndex;
            }
        }

        return new TreeId(treeId);
    }

    protected int unpromotableBucketIndex(final int depthIndex) {
        throw new UnsupportedOperationException();
    }

    private TreeId computeBucketId(final TreeId treeId, final Integer leafOverride) {
        byte[] bucketIndicesByDepth = treeId.bucketIndicesByDepth.clone();
        bucketIndicesByDepth[bucketIndicesByDepth.length - 1] = leafOverride.byteValue();
        return new TreeId(bucketIndicesByDepth);
    }

    private Map<NodeId, DAGNode> lazyNodes(final RevTree tree) {
        if (tree.isEmpty()) {
            return ImmutableMap.of();
        }

        final TreeCache treeCache = storageProvider.getTreeCache();
        final int cacheTreeId = treeCache.getTreeId(tree).intValue();

        Map<NodeId, DAGNode> dagNodes = new HashMap<>();

        List<Node> treeNodes = tree.trees();
        for (int i = 0; i < treeNodes.size(); i++) {
            NodeId nodeId = computeId(treeNodes.get(i));
            DAGNode dagNode = DAGNode.treeNode(cacheTreeId, i);
            dagNodes.put(nodeId, dagNode);
        }

        ImmutableList<Node> featureNodes = tree.features();
        for (int i = 0; i < featureNodes.size(); i++) {
            NodeId nodeId = computeId(featureNodes.get(i));
            DAGNode dagNode = DAGNode.featureNode(cacheTreeId, i);
            dagNodes.put(nodeId, dagNode);
        }
        return dagNodes;
    }

    @VisibleForTesting
    static class DAGCache {
        private DAGStorageProvider store;

        @VisibleForTesting
        final Map<TreeId, DAG> treeBuff = new ConcurrentHashMap<>();

        DAGCache(DAGStorageProvider store) {
            this.store = store;
        }

        public void dispose() {
            treeBuff.clear();
        }

        /**
         * Returns all the DAG's for the argument ids, which must already exist
         */
        public Map<TreeId, DAG> getAll(Set<TreeId> ids) {
            Map<TreeId, DAG> map = new HashMap<>();
            Set<TreeId> missing = new HashSet<>();
            for (TreeId id : ids) {
                DAG dag = treeBuff.get(id);
                if (dag == null) {
                    missing.add(id);
                } else {
                    map.put(id, dag);
                }
            }
            Map<TreeId, DAG> uncached = store.getTrees(missing);
            map.putAll(uncached);
            return map;
        }

        public DAG getOrCreate(TreeId treeId, ObjectId originalTreeId) {
            DAG dag = treeBuff.get(treeId);
            if (dag == null) {
                dag = store.getOrCreateTree(treeId, originalTreeId);
                DAG existing = treeBuff.putIfAbsent(treeId, dag);
                if (existing != null) {
                    dag = existing;
                }
            }
            return dag;
        }
    }

}