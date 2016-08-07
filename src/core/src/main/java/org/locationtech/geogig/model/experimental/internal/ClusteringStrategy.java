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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.experimental.internal.DAG.STATE;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.vividsolutions.jts.geom.Envelope;

public abstract class ClusteringStrategy {

    private DAGStorageProvider storageProvider;

    private static final TreeId ROOT_ID = new TreeId(new byte[0]);

    private DAG root;

    public ClusteringStrategy(RevTree original, DAGStorageProvider storageProvider) {
        checkNotNull(original);
        checkNotNull(storageProvider);
        this.storageProvider = storageProvider;
        this.root = new DAG(original.getId());
        this.root.childCount = original.size();
    }

    public static ClusteringStrategy canonical(ObjectStore store, RevTree original) {
        DAGStorageProviderFactory dagStorageFactory;
        dagStorageFactory = new HeapDAGStorageProviderFactory(store);
        return ClusteringStrategyFactory.canonical().create(original, dagStorageFactory);
    }

    public static ClusteringStrategy quadTree(ObjectStore store, RevTree original,
            Envelope maxBounds, int maxDepth) {

        DAGStorageProviderFactory dagStorageFactory;
        dagStorageFactory = new HeapDAGStorageProviderFactory(store);

        ClusteringStrategyFactory quadtree = ClusteringStrategyFactory.quadtree(maxBounds,
                maxDepth);
        return null;
    }

    public DAG getRoot() {
        return root;
    }

    // protected abstract int maxDepth();
    //
    abstract int maxBuckets(final int depthIndex);

    abstract int normalizedSizeLimit(final int depthIndex);

    /**
     * @return {@code null} if the node shall not be added to the tree (e.g. a non spatial node on a
     *         quadtree)
     */
    public abstract @Nullable NodeId computeId(Node node);

    public void remove(final String featureId) {
        Node removeNode = Node.create(featureId, ObjectId.NULL, ObjectId.NULL, TYPE.FEATURE, null);
        put(ROOT_ID, this.root, removeNode);
    }

    public void put(final Node node) {
        put(ROOT_ID, this.root, node);
    }

    public DAG getTree(TreeId treeId) {
        return storageProvider.getOrCreateTree(treeId);
    }

    public Node getNode(NodeId nodeId) {
        return storageProvider.getNode(nodeId);
    }

    public void dispose() {
        this.storageProvider.dispose();
    }

    int depth() {
        return depth(getRoot());
    }

    int depth(DAG root) {
        if (root.buckets() == null || root.buckets().isEmpty()) {
            return 0;
        }

        int maxDepth = 0;
        for (TreeId bucketId : root.buckets()) {
            DAG bucket = getTree(bucketId);
            int bucketDepth = depth(bucket);
            maxDepth = Math.max(maxDepth, bucketDepth);
        }
        return 1 + maxDepth;
    }

    private void put(TreeId rootId, DAG root, final Node node) {

        final @Nullable NodeId nodeId = computeId(node);
        if (nodeId != null) {
            final int depth = 0;
            put(rootId, root, nodeId, node, depth);
            // save after mergeRoot in case we need to override node
            if (!node.getObjectId().isNull()) {
                storageProvider.saveNode(nodeId, node);
            }
        }
    }

    private synchronized int put(final TreeId dagId, final DAG dag, final NodeId nodeId,
            final Node node, final int dagDepth) {
        checkNotNull(dag);
        checkNotNull(nodeId);
        checkArgument(dagDepth > -1);

        mergetRoot(dag, nodeId, dagDepth);

        boolean changed = false;
        final int deltaSize;

        @Nullable
        Set<NodeId> children = dag.children();

        if (children == null) {
            @Nullable
            final TreeId bucketId = computeBucketId(nodeId, dagDepth);
            // handle the case where null is returned and hence a mixed tree shall be created
            if (bucketId == null) {
                // node can't be promoted to depth + 1, must stay in children's list
                dag.addNonPromotable(nodeId);
                deltaSize = 1;
            } else {
                DAG bucketDAG = storageProvider.getOrCreateTree(bucketId);
                Set<TreeId> buckets = dag.buckets();
                buckets.add(bucketId);
                deltaSize = put(bucketId, bucketDAG, nodeId, node, dagDepth + 1);
                changed = bucketDAG.getState() == STATE.CHANGED;
            }

        } else {
            if (node.getObjectId().isNull()) {
                deltaSize = children.remove(nodeId) ? -1 : 0;
            } else {
                changed = true;// contents changed, independently of children.add return code
                deltaSize = children.add(nodeId) ? +1 : 0;
            }

            final int size = children.size();
            final int normalizedSizeLimit = normalizedSizeLimit(dagDepth);

            if (size > normalizedSizeLimit) {
                Set<NodeId> nodeIds = dag.switchToBuckets();

                for (NodeId childId : nodeIds) {
                    // NodeId childId = nodeIds.get(i);
                    put(dagId, dag, childId, node, dagDepth);
                }
            }
        }

        if (deltaSize != 0) {
            changed = true;
            dag.childCount += deltaSize;
            shrinkIfUnderflow(dag, nodeId, dagDepth);
        }
        if (changed) {
            dag.setChanged();
            if (!ROOT_ID.equals(dagId)) {
                storageProvider.save(dagId, dag);
            }
        }
        return deltaSize;
    }

    private void shrinkIfUnderflow(final DAG bucketsDAG, NodeId nodeId, int depth) {
        final long childCount = bucketsDAG.childCount;
        final int normalizedSizeLimit = normalizedSizeLimit(depth);

        if (childCount <= normalizedSizeLimit && bucketsDAG.buckets() != null) {
            Set<NodeId> childrenRecursive = getChildrenRecursive(bucketsDAG, nodeId, depth);
            checkState(childrenRecursive.size() == childCount, "expected %s, got %s", childCount,
                    childrenRecursive.size());
            final Set<NodeId> children = bucketsDAG.switchToLeaf();
            children.addAll(childrenRecursive);
        }
    }

    private Set<NodeId> getChildrenRecursive(final DAG dag, final NodeId nodeId, final int depth) {

        Set<NodeId> children = dag.children();
        if (children != null) {
            return children;
        }
        children = new HashSet<>();
        if (dag.unpromotable() != null) {
            children.addAll(dag.unpromotable());
        }
        if (dag.buckets() != null) {
            for (TreeId bucketId : dag.buckets()) {
                DAG bucket = getTree(bucketId);
                mergetRoot(bucket, nodeId, depth);
                Set<NodeId> bucketChildren = getChildrenRecursive(bucket, nodeId, depth + 1);
                children.addAll(bucketChildren);
            }
        }
        return children;
    }

    /**
     * Makes sure the DAG has the same structure than the original tree following the path to the
     * node (i.e.) loading only the {@link RevTree trees} necessary to reach the node being added.
     * 
     * @param root
     * @param nodeId
     * @param depth
     * @return
     * @return
     */
    private void mergetRoot(DAG root, final NodeId nodeId, final int depth) {
        checkNotNull(root);
        checkNotNull(nodeId);

        if (root.getState() == STATE.INITIALIZED) {

            final RevTree original = getOriginalTree(root.treeId);
            root.childCount = original.size();

            final boolean originalIsLeaf = original.buckets().isEmpty();

            if (originalIsLeaf) {
                final Map<NodeId, DAGNode> origNodes = lazyNodes(original);
                if (!origNodes.isEmpty()) {
                    // TODO: avoid saving nodes already in RevTrees
                    storageProvider.saveNodes(origNodes);
                    Set<NodeId> children = root.children();
                    if (children != null) {
                        children.addAll(origNodes.keySet());
                    }
                }
            } else {

                @Nullable
                final TreeId nodeBucketId = computeBucketId(nodeId, depth);
                final ImmutableSortedMap<Integer, Bucket> buckets = original.buckets();

                if (root.getState() == STATE.INITIALIZED) {
                    { // make DAG a bucket tree
                        Set<NodeId> promotable = root.switchToBuckets();
                        checkState(promotable == null || promotable.isEmpty());
                    }
                    {// keep any non promotable node (i.e. if RevTree has both leaf nodes and
                     // buckets, the leaf nodes _are_ non promotable)
                        final Map<NodeId, DAGNode> unprommottable = lazyNodes(original);
                        if (!unprommottable.isEmpty()) {
                            storageProvider.saveNodes(unprommottable);
                            root.addNonPromotable(unprommottable.keySet());
                        }
                    }

                    // initialize buckets
                    preload(buckets.values());
                    for (Entry<Integer, Bucket> e : buckets.entrySet()) {
                        Integer bucketIndex = e.getKey();
                        TreeId dagBucketId = computeBucketId(nodeBucketId, bucketIndex);
                        ObjectId bucketId = e.getValue().getObjectId();
                        DAG bucketDAG = storageProvider.getOrCreateTree(dagBucketId, bucketId);
                        root.buckets().add(dagBucketId);
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

    @Nullable
    TreeId computeBucketId(final NodeId nodeId, final int childDepth) {
        byte[] treeId = new byte[childDepth + 1];
        for (int i = 0; i <= childDepth; i++) {
            final int bucketIndex = nodeId.bucket(i);
            if (-1 == bucketIndex) {
                return null;
            }
            treeId[i] = (byte) bucketIndex;
        }
        return new TreeId(treeId);
    }

    private @Nullable TreeId computeBucketId(final TreeId treeId, final Integer leafOverride) {
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
}