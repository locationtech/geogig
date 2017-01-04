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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.internal.DAG.STATE;
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

    private TreeId rootId = ROOT_ID;

    protected ClusteringStrategy(RevTree original, DAGStorageProvider storageProvider) {
        checkNotNull(original);
        checkNotNull(storageProvider);
        this.storageProvider = storageProvider;
        this.root = new DAG(original.getId());
        this.root.setChildCount(original.size());
    }

    public static ClusteringStrategy canonical(final ObjectStore store, final RevTree original) {
        checkNotNull(store);
        checkNotNull(original);

        final DAGStorageProviderFactory dagStorageFactory;
        // dagStorageFactory = new RocksdbDAGStorageProviderFactory(store);
        dagStorageFactory = new CachingDAGStorageProviderFactory(store);
        // dagStorageFactory = new HeapDAGStorageProviderFactory(store);
        return ClusteringStrategyFactory.canonical().create(original, dagStorageFactory);
    }

    public static ClusteringStrategy quadTree(ObjectStore store, RevTree original,
            Envelope maxBounds, int maxDepth) {
        checkNotNull(store);
        checkNotNull(original);

        final DAGStorageProviderFactory dagStorageFactory;
        // dagStorageFactory = new RocksdbDAGStorageProviderFactory(store);
        // dagStorageFactory = new CachingDAGStorageProviderFactory(store);
        dagStorageFactory = new HeapDAGStorageProviderFactory(store);
        ClusteringStrategyFactory quadtree = ClusteringStrategyFactory.quadtree(maxBounds,
                maxDepth);
        return quadtree.create(original, dagStorageFactory);
    }

    abstract int maxBuckets(final int depthIndex);

    abstract int normalizedSizeLimit(final int depthIndex);

    /**
     * @return {@code null} if the node shall not be added to the tree (e.g. a non spatial node on a
     *         quadtree)
     */
    public abstract @Nullable NodeId computeId(Node node);

    DAG getOrCreateDAG(TreeId treeId) {
        return getOrCreateDAG(treeId, RevTree.EMPTY_TREE_ID);
    }

    private Map<TreeId, DAG> treeBuff = new HashMap<>();

    private synchronized DAG getOrCreateDAG(TreeId treeId, ObjectId originalTreeId) {
        DAG dag = treeBuff.get(treeId);
        if (dag == null) {
            dag = storageProvider.getOrCreateTree(treeId, originalTreeId);
            treeBuff.put(treeId, dag);
        }
        return dag;
    }

    public Map<TreeId, DAG> getDagTrees(Set<TreeId> ids) {
        if (!treeBuff.isEmpty()) {
            storageProvider.save(treeBuff);
            treeBuff.clear();
        }
        Map<TreeId, DAG> trees = storageProvider.getTrees(ids);
        return trees;
    }

    public Node getNode(NodeId nodeId) {

        Node node = storageProvider.getNode(nodeId);
        return node;
    }

    public SortedMap<NodeId, Node> getNodes(Set<NodeId> nodeIds) {

        return storageProvider.getNodes(nodeIds);
    }

    public void dispose() {
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
            put(rootId, root, nodeId, remove, rootDepth);
            if (!remove) {
                storageProvider.saveNode(nodeId, node);
            }
            if (treeBuff.size() >= 10_000) {
                storageProvider.save(treeBuff);
                treeBuff.clear();
            }
        }
    }

    public TreeId getRootId() {
        return rootId;
    }

    public DAG buildRoot() {
        if (!treeBuff.isEmpty()) {
            storageProvider.save(treeBuff);
            treeBuff.clear();
        }
        ///
        while (1 == root.numBuckets() && 0 == root.numUnpromotable()) {
            root.forEachBucket((treeId) -> {
                DAG actual = getOrCreateDAG(treeId);
                root = actual;
                rootId = treeId;
            });
        }

        return root;
    }

    private synchronized int put(final TreeId dagId, final DAG dag, final NodeId nodeId,
            final boolean remove, final int dagDepth) {
        checkNotNull(dag);
        checkNotNull(nodeId);
        checkArgument(dagDepth > -1);

        mergeRoot(dag, nodeId, dagDepth);

        boolean changed = false;
        final int deltaSize;
        final int normalizedSizeLimit = normalizedSizeLimit(dagDepth);

        if (dag.numBuckets() > 0) {
            @Nullable
            final TreeId bucketId = computeBucketId(nodeId, dagDepth);
            // handle the case where null is returned and hence a mixed tree shall be created
            if (bucketId == null) {
                // node can't be promoted to depth + 1, must stay in children's list
                dag.addNonPromotable(nodeId);
                deltaSize = 1;
            } else {
                DAG bucketDAG = getOrCreateDAG(bucketId);
                dag.addBucket(bucketId);
                deltaSize = put(bucketId, bucketDAG, nodeId, remove, dagDepth + 1);
                changed = bucketDAG.getState() == STATE.CHANGED;
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
                    @Nullable
                    final TreeId bucketId = computeBucketId(childId, dagDepth);
                    // handle the case where null is returned and hence a mixed tree shall be
                    // created
                    if (bucketId == null) {
                        // node can't be promoted to depth + 1, must stay in children's list
                        dag.addNonPromotable(childId);
                    } else {
                        DAG bucketDAG = getOrCreateDAG(bucketId);
                        dag.addBucket(bucketId);
                        put(bucketId, bucketDAG, childId, remove, dagDepth + 1);
                        /// changed = bucketDAG.getState() == STATE.CHANGED;
                    }
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

        dag.forEachUnpromotableChild((id) -> children.add(id));

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

                @Nullable
                final TreeId nodeBucketId = computeBucketId(nodeId, depthIndex);
                final ImmutableSortedMap<Integer, Bucket> buckets = original.buckets();

                if (root.getState() == STATE.INITIALIZED) {
                    { // make DAG a bucket tree
                        checkState(root.numChildren() == 0);
                    }
                    {// keep any non promotable node (i.e. if RevTree has both leaf nodes and
                     // buckets, the leaf nodes _are_ non promotable)
                        final Map<NodeId, DAGNode> unprommottable = lazyNodes(original);
                        if (!unprommottable.isEmpty()) {
                            storageProvider.saveNodes(unprommottable);
                            unprommottable.keySet().forEach((id) -> root.addNonPromotable(id));
                        }
                    }

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

    @Nullable
    TreeId computeBucketId(final NodeId nodeId, final int childDepthIndex) {
        byte[] treeId = new byte[childDepthIndex + 1];
        for (int i = 0; i <= childDepthIndex; i++) {
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