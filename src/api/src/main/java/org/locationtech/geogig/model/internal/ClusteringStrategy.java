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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeOrdering;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.internal.DAG.STATE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Base class for strategy objects that define the internal structure of a {@link RevTree}.
 * 
 * @apiNote instances of this class might hold references to temporary resources that need to be
 *          cleaned up after usage, hence the use of the {@link #dispose()} method is mandatory. In
 *          general, the {@link RevTreeBuilder} that's using this object will do so as part of its
 *          own clean up phase before returning from its own {@code build()} method.
 */
public abstract class ClusteringStrategy extends NodeOrdering {

    private static final long serialVersionUID = 1L;

    final DAGStorageProvider storageProvider;

    protected static final TreeId ROOT_ID = new TreeId(new byte[0]);

    protected final DAG root;

    @VisibleForTesting
    final RevTree original;

    @VisibleForTesting
    final DAGCache dagCache;

    protected ClusteringStrategy(RevTree original, DAGStorageProvider storageProvider) {
        checkNotNull(original);
        checkNotNull(storageProvider);
        this.original = original;
        this.storageProvider = storageProvider;
        this.dagCache = new DAGCache(storageProvider);
        this.root = new DAG(ROOT_ID, original.getId());
        mergeRoot(root);
    }

    public @Override int compare(Node left, Node right) {
        return getNodeOrdering().compare(computeId(left), computeId(right));
    }

    public @Override int bucket(Node node, int depth) {
        NodeId nodeId = computeId(node);
        TreeId bucketId = computeBucketId(nodeId, depth);
        int leafBucket = bucketId.leafBucket();
        return leafBucket;
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

    /**
     * @see #getOrCreateDAG(TreeId, ObjectId)
     */
    DAG getOrCreateDAG(TreeId treeId) {
        return getOrCreateDAG(treeId, RevTree.EMPTY_TREE_ID);
    }

    /**
     * Returns the mutable tree (DAG) associated to the given {@link TreeId}, creating it if it
     * doesn't exist and setting it's original {@link RevTree} identifier as {@code originalTreeId}
     */
    protected DAG getOrCreateDAG(TreeId treeId, ObjectId originalTreeId) {
        return dagCache.getOrCreate(treeId, originalTreeId);
    }

    public List<DAG> getDagTrees(Set<TreeId> ids) {
        return dagCache.getAll(ids);
    }

    @VisibleForTesting
    Node getNode(NodeId nodeId) {
        SortedMap<NodeId, Node> nodes = getNodes(Collections.singleton(nodeId));
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
        TreeMap<NodeId, Node> sorted = new TreeMap<>(getNodeOrdering());
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

    public boolean remove(Node node) {
        if (!node.getObjectId().isNull()) {
            node = node.update(ObjectId.NULL);
        }
        int delta = put(node);
        return -1 == delta;
    }

    /**
     * Replaces {@code oldNode} by {@code newNode}
     * <p>
     * This default implemetation just calls {@link #remove(Node) remove(oldNode)} and then
     * {@link #put(Node) put(newNode)}. Subclasses are encouraged to override with optimized
     * versions whenever possible.
     * 
     * @return {@code 0} if the operation resulted in no change, {@code 1} if the node was
     *         inserted/updated, {@code -1} if the node was deleted
     */
    public int update(Node oldNode, Node newNode) {
        Preconditions.checkArgument(oldNode.getName().equals(newNode.getName()));
        if (remove(oldNode)) {
            return put(newNode);
        }
        return 0;
    }

    private Lock writeLock = new ReentrantLock();

    /**
     * @param
     * @return {@code 0} if the operation resulted in no change, {@code 1} if the node was
     *         inserted/updated, {@code -1} if the node was deleted
     */
    public int put(final Node node) {
        @Nullable
        final NodeId nodeId = computeId(node);
        if (null == nodeId) {
            return 0;
        }
        // nodeId can be null if it's not to be added to the tree at all (e.g. a non spatial
        // feature in a spatial index)

        boolean remove = node.getObjectId().isNull();
        int delta;
        writeLock.lock();
        try {
            delta = put(root, nodeId, remove);
            dagCache.prune();
        } finally {
            writeLock.unlock();
        }
        if (!remove) {
            storageProvider.saveNode(nodeId, node);
        }
        return delta;
    }

    public DAG buildRoot() {
        return root;
    }

    /**
     * @param dagId
     * @param dag
     * @param nodeId
     * @param remove
     * @param dagDepth zero based depth of {@code dag} (not a depth index, which is
     *        {@code depth - 1}
     * @return
     */
    protected int put(final DAG dag, final NodeId nodeId, final boolean remove) {
        checkNotNull(dag);
        checkNotNull(nodeId);

        final int dagDepth = dag.getId().depthLength();

        mergeRoot(dag);

        boolean changed = false;
        final int deltaSize;
        final int normalizedSizeLimit = normalizedSizeLimit(dagDepth);

        if (dag.numBuckets() > 0) {
            final @Nullable TreeId bucketId = computeBucketId(nodeId, dagDepth + 1);
            if (bucketId != null) {
                final DAG bucketDAG = getOrCreateDAG(bucketId);
                dag.addBucket(bucketId);
                deltaSize = put(bucketDAG, nodeId, remove);
                changed = bucketDAG.getState() == STATE.CHANGED;
                if (bucketDAG.getTotalChildCount() == 0) {
                    dag.removeBucket(bucketId);
                }
            } else {
                deltaSize = 0;
            }
        } else {
            if (remove) {
                final boolean removed = dag.removeChild(nodeId);
                deltaSize = removed ? -1 : 0;
            } else {
                changed = true;// contents changed, independently of children.add return code
                deltaSize = dag.addChild(nodeId) ? +1 : 0;
            }

            final int size = dag.numChildren();

            if (size > normalizedSizeLimit) {
                ListMultimap<TreeId, NodeId> promotions = ArrayListMultimap.create();
                dag.forEachChild((childId) -> {
                    TreeId bucketId = computeBucketId(childId, dagDepth + 1);
                    checkNotNull(bucketId);
                    promotions.put(bucketId, childId);
                });

                promotions.asMap().forEach((bucketId, childIds) -> {
                    DAG bucketDAG = getOrCreateDAG(bucketId);
                    dag.addBucket(bucketId);
                    for (NodeId childId : childIds) {
                        put(bucketDAG, childId, remove);
                    }
                });

                dag.clearChildren();
            }
        }

        if (deltaSize != 0) {
            changed = true;
            long pre = dag.getTotalChildCount();
            dag.setTotalChildCount(dag.getTotalChildCount() + deltaSize);
            long post = dag.getTotalChildCount();
            Preconditions.checkState(pre + deltaSize == post);
            try {
                shrinkIfUnderflow(dag);
            } catch (IllegalStateException e) {
                if (remove) {
                    System.out.printf(
                            "!!! Error removing %s\t from %s. pre: %,d, post: %,d, delta: %d, thread: %s\n",
                            nodeId.name(), dag.getId(), pre, post, deltaSize,
                            Thread.currentThread().getName());
                }
                throw e;
            }
        }
        if (changed) {
            dag.setChanged();
        }
        return deltaSize;
    }

    static final TreeId failingDag = TreeId.fromString("[1, 0, 2, 2, 3, 2, 0, 0, 2, 2, 3, 1]");

    private void shrinkIfUnderflow(final DAG dag) {
        if (dag.numBuckets() == 0) {
            return;
        }

        final long childCount = dag.getTotalChildCount();
        // TODO: in the case of quadtrees would need to check if it's an unpromotables bucket and
        // use canonical's normalized size limit instead?
        final int depth = dag.getId().depthLength();
        final int normalizedSizeLimit = normalizedSizeLimit(depth);
        if (childCount > normalizedSizeLimit) {
            return;
        }
        Set<NodeId> childrenRecursive = getChildrenRecursiveAndClearBuckets(dag);
        int collectedSize = childrenRecursive.size();

        if (dag.getId().equals(failingDag)) {
            System.err.printf("expected: %d, collected: %d\n", childCount, collectedSize);
        }

        if (collectedSize != childCount) {
            throw new IllegalStateException(String.format("expected %s, got %s, at: %s", childCount,
                    childrenRecursive.size(), dag));
        }
        dag.clearBuckets();
        childrenRecursive.forEach((id) -> dag.addChild(id));

    }

    /**
     * To be called by {@link #shrinkIfUnderflow(DAG, NodeId, int)}
     */
    private Set<NodeId> getChildrenRecursiveAndClearBuckets(final DAG dag) {

        Set<NodeId> children = new HashSet<>();
        dag.forEachChild((id) -> children.add(id));

        if (!children.isEmpty()) {
            return children;
        }
        final List<TreeId> bucketIds = dag.bucketList();
        for (TreeId bucketId : bucketIds) {
            DAG bucket = getOrCreateDAG(bucketId);
            if (bucket.getState() == STATE.INITIALIZED) {
                mergeRoot(bucket);
            }
            Set<NodeId> bucketChildren = getChildrenRecursiveAndClearBuckets(bucket);
            int pre = children.size();
            children.addAll(bucketChildren);
            int post = children.size();
            Preconditions.checkState(pre + bucketChildren.size() == post);
            bucket.reset(RevTree.EMPTY_TREE_ID);
        }

        return children;
    }

    /**
     * Makes sure the DAG has the same structure than the original tree following the path to the
     * node (i.e.) loading only the {@link RevTree trees} necessary to reach the node being added.
     * 
     * @param root
     * @param nodeDepth depth, not depth index
     * @return
     * @return
     */
    protected void mergeRoot(DAG root) {
        checkNotNull(root);

        if (root.getState() == STATE.INITIALIZED) {

            final RevTree original = getOriginalTree(root.originalTreeId());

            root.setTotalChildCount(original.size() + original.numTrees());

            final boolean originalIsLeaf = 0 == original.bucketsSize();

            if (originalIsLeaf) {
                final Map<NodeId, DAGNode> origNodes = lazyNodes(original);
                if (!origNodes.isEmpty()) {
                    // TODO: avoid saving nodes already in RevTrees
                    storageProvider.saveNodes(origNodes);
                    origNodes.keySet().forEach((id) -> root.addChild(id));
                }

            } else {
                if (root.getState() == STATE.INITIALIZED) {
                    // make DAG a bucket tree
                    checkState(root.numChildren() == 0);

                    // initialize buckets
                    preloadBuckets(original);
                    original.forEachBucket(bucket -> {
                        TreeId dagBucketId = root.getId().newChild(bucket.getIndex());
                        ObjectId bucketId = bucket.getObjectId();
                        // make sure the DAG exists and is initialized
                        getOrCreateDAG(dagBucketId, bucketId);
                        root.addBucket(dagBucketId);
                    });
                }
            }
            root.setMirrored();
        }

    }

    private void preloadBuckets(RevTree tree) {
        if (tree.bucketsSize() > 0) {
            List<ObjectId> ids = new ArrayList<>(tree.bucketsSize());
            tree.forEachBucket(bucket -> ids.add(bucket.getObjectId()));
            this.storageProvider.getTreeCache().preload(ids);
        }
    }

    protected RevTree getOriginalTree(@Nullable ObjectId originalId) {
        final RevTree original;
        if (originalId == null || RevTree.EMPTY_TREE_ID.equals(originalId)) {
            original = RevTree.EMPTY;
        } else {
            original = storageProvider.getTree(originalId);
        }
        return original;
    }

    TreeId computeBucketId(final NodeId nodeId, final int childDepth) {
        byte[] treeId = new byte[childDepth];

        int unpromotableDepthIndex = -1;

        for (int depthIndex = 0; depthIndex < childDepth; depthIndex++) {
            int bucketIndex = bucket(nodeId, depthIndex);
            if (bucketIndex == -1) {
                unpromotableDepthIndex = depthIndex;
                break;
            }
            treeId[depthIndex] = (byte) bucketIndex;
        }

        if (unpromotableDepthIndex > -1) {
            final int extraBucketIndex = unpromotableBucketIndex(unpromotableDepthIndex);
            treeId[unpromotableDepthIndex] = (byte) extraBucketIndex;
            unpromotableDepthIndex++;
            final int missingDepthCount = childDepth - unpromotableDepthIndex;
            for (int i = 0; i < missingDepthCount; i++, unpromotableDepthIndex++) {
                int bucketIndex = canonicalBucket(nodeId, i);
                treeId[unpromotableDepthIndex] = (byte) bucketIndex;
            }
        }

        return new TreeId(treeId);
    }

    protected int unpromotableBucketIndex(final int depthIndex) {
        throw new UnsupportedOperationException();
    }

    private Map<NodeId, DAGNode> lazyNodes(final RevTree tree) {
        if (tree.isEmpty()) {
            return Collections.emptyMap();
        }

        final TreeCache treeCache = storageProvider.getTreeCache();
        final int cacheTreeId = treeCache.getTreeId(tree).intValue();

        Map<NodeId, DAGNode> dagNodes = new HashMap<>();

        final int treesSize = tree.treesSize();
        for (int i = 0; i < treesSize; i++) {
            NodeId nodeId = computeId(tree.getTree(i));
            DAGNode dagNode = DAGNode.treeNode(cacheTreeId, i);
            dagNodes.put(nodeId, dagNode);
        }

        final int featuresSize = tree.featuresSize();
        for (int i = 0; i < featuresSize; i++) {
            NodeId nodeId = computeId(tree.getFeature(i));
            DAGNode dagNode = DAGNode.featureNode(cacheTreeId, i);
            dagNodes.put(nodeId, dagNode);
        }

        return dagNodes;
    }

    @VisibleForTesting
    class DAGCache {
        private DAGStorageProvider store;

        @VisibleForTesting
        final Map<TreeId, DAG> treeBuff = new ConcurrentHashMap<>();

        private Set<TreeId> dirty = new HashSet<>();

        DAGCache(DAGStorageProvider store) {
            this.store = store;
        }

        public void dispose() {
            treeBuff.clear();
        }

        /**
         * Returns all the DAG's for the argument ids, which must already exist
         */
        public List<DAG> getAll(Set<TreeId> ids) {
            List<DAG> all = new ArrayList<>();
            Set<TreeId> missing = new HashSet<>();
            for (TreeId id : ids) {
                DAG dag;
                if (ROOT_ID.equals(id)) {
                    dag = ClusteringStrategy.this.root;
                } else {
                    dag = treeBuff.get(id);
                }
                if (dag == null) {
                    missing.add(id);
                } else {
                    all.add(dag);
                }
            }
            List<DAG> uncached = store.getTrees(missing);
            all.addAll(uncached);
            return all;
        }

        public DAG getOrCreate(TreeId treeId, ObjectId originalTreeId) {
            DAG dag = treeBuff.get(treeId);
            if (dag == null) {
                dag = store.getOrCreateTree(treeId, originalTreeId);
                dag.changeListener = this::changed;
                DAG existing = treeBuff.putIfAbsent(treeId, dag);
                if (existing != null) {
                    dag = existing;
                }
            }
            return dag;
        }

        /**
         * Decides whether to mark a changed DAG as "dirty" (candidate to be returned to the DAG
         * store)
         */
        private void changed(DAG dag) {
            // currently keeps all bucket DAGs in the cache, and flushes leaf DAGs to the underlying
            // temporary store
            if (dag.numChildren() > 64) {
                dirty.add(dag.getId());
            }
        }

        public void prune() {
            final int dirtySize = dirty.size();
            if (dirtySize < 10_000) {
                return;
            }

            Map<TreeId, DAG> toSave = new HashMap<>();
            for (TreeId id : dirty) {
                DAG saveme = treeBuff.remove(id);
                checkNotNull(saveme);
                toSave.put(id, saveme);
            }
            dirty.clear();
            store.save(toSave);
        }

        @VisibleForTesting
        public void add(DAG dag) {
            treeBuff.put(dag.getId(), dag);
        }
    }
}