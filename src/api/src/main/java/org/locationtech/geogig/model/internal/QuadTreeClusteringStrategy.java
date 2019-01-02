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
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.internal.DAG.STATE;
import org.locationtech.jts.geom.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * This class determines how the quadtree clustering strategy puts features in the tree.
 *
 * NOTE: This also supports polygons/lines (i.e. non-point objects). Each feature is only put in ONE
 * location in the tree - the quad that it is fully enclosed by (which might be the root node).
 *
 * NOTE: if the feature doesn't have a bounds (i.e. null or empty), then it is NOT put in the tree
 * (null NodeId).
 *
 * The main entry point is computeId(), which returns a NodeId that defines where that Node should
 * be in the QuadTree. The NodeId contains a list of Quandrants defining which quadrant the feature
 * lies completely inside for EACH level of the quadtree.
 *
 * If NodeId contains quadrants NE, then SE, then NW then it means that:
 *
 * The feature is completely in the NE quadrant for the "world" (level 0).
 *
 * Looking at the world's NE quadrant and subdividing it into 4 quads, the feature would be fully in
 * the SE quadrant (level 1).
 *
 * The world's NE quadrant (level 0), then that quad's sub SE quadrant (level 1), the feature will
 * be contains in that quad's NW quad (level 2).
 *
 * This continues until; + the max depth of the tree is reached + the feature in not fully contained
 * in a single quad
 *
 * Typically, large features will need to be contained "higher" up in the tree. However, this can
 * happen with very small features that are "unlucky" enough to cross ANY quad boundary. If this
 * happens a lot, then the tree could become more degenerative and have large numbers of features
 * associated with a single node that can NOT be moved into "lower" level nodes in the hierarchy.
 *
 *
 */
final class QuadTreeClusteringStrategy extends ClusteringStrategy {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(QuadTreeClusteringStrategy.class);

    private final Envelope maxBounds;

    private final int maxDepth;

    /**
     * Enable/disable the experimental feature to collapse and expand DAGs
     */
    public static final boolean ENABLE_EXPAND_COLLAPSE = false;

    QuadTreeClusteringStrategy(RevTree original, DAGStorageProvider storageProvider,
            Envelope maxBounds, int maxDepth) {
        super(original, storageProvider);
        this.maxBounds = maxBounds;
        this.maxDepth = maxDepth;
    }

    public Envelope getMaxBounds() {
        return new Envelope(maxBounds);
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    @Override
    protected void mergeRoot(final DAG root) {
        if (!ENABLE_EXPAND_COLLAPSE) {
            super.mergeRoot(root);
            return;
        }
        checkNotNull(root);

        if (root.getState() == STATE.INITIALIZED) {
            final ObjectId originalTreeId = root.originalTreeId();
            final RevTree originalTree = getOriginalTree(originalTreeId);

            final TreeId rootId = root.getId();
            final TreeId expandedTreeId = computeExpandedChildId(originalTree, rootId);
            if (rootId.equals(expandedTreeId)) {
                super.mergeRoot(root);
            } else {
                // expand(root, expandedTreeId, originalTree);
                // System.err.println(rootId + " expanded to " + expandedTreeId);
            }
        }
    }

    // private void expand(DAG parent, TreeId expandToChild, RevTree originalTree, NodeId nodeId) {
    // Preconditions.checkArgument(parent.getId().depthLength() < expandToChild.depthLength());
    //
    // // initialize leaf to match originalTree
    // final DAG child = getOrCreateDAG(expandToChild, originalTree.getId());
    // child.reset(originalTree.getId());
    // super.mergeRoot(child, nodeId);
    // child.setInitialized();
    //
    // // add parents up to root
    // parent.reset(RevTree.EMPTY_TREE_ID);
    // parent.addBucket(expandToChild);
    // parent.setTotalChildCount(child.getTotalChildCount());
    // parent.setMirrored();
    // }

    /**
     * Override to collapse root DAG's that are one single bucket to the first DAG that has more
     * than one bucket
     */
    @Override
    public DAG buildRoot() {
        if (ENABLE_EXPAND_COLLAPSE) {
            final long size = root.getTotalChildCount();
            collapse(root);
            final long resultSize = root.getTotalChildCount();
            checkState(size == resultSize, "expected size of %s, but got %s after collapse()", size,
                    resultSize);
        }
        return root;
    }

    public void collapse(final DAG dag) {
        if (dag.getState() != STATE.CHANGED) {
            return;
        }

        final int numBuckets = dag.numBuckets();
        if (numBuckets == 0) {
            return;
        }

        final List<TreeId> bucketIds = dag.bucketList();
        if (numBuckets == 1) {
            final TreeId replaceId = bucketIds.get(0);
            DAG child = getOrCreateDAG(replaceId);
            setParent(child, dag);
            // for (TreeId childId : dag.bucketList()) {
            // child = getOrCreateDAG(childId);
            // collapse(dag, child);
            // }
        } else {
            for (TreeId bucketId : bucketIds) {
                DAG child = getOrCreateDAG(bucketId);
                collapse(child);
            }
        }
    }

    /**
     * Parent becomes child
     */
    private void setParent(DAG child, DAG parent) {
        // find deepest 1-bucket child
        while (child.numBuckets() == 1) {
            child = getOrCreateDAG(child.bucketList().get(0));
        }
        // replace contents of parent with the contents of child
        parent.init(child);
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} collapsed to {}", child.getId(), parent.getId());
        }
        // System.err.println(child.getId() + " collapsed to " + parent.getId());
        List<TreeId> buckets = parent.bucketList();
        parent.clearBuckets();

        for (TreeId id : buckets) {
            DAG deepChild = getOrCreateDAG(id);
            TreeId newChildId = parent.getId().newChild(id.leafBucket());
            DAG newChild = getOrCreateDAG(newChildId);
            parent.addBucket(newChildId);
            setParent(deepChild, newChild);
        }
    }

    private TreeId computeExpandedChildId(RevTree originalTree, TreeId rootId) {
        final long size = originalTree.size();
        final Envelope treeBounds = RevObjects.boundsOf(original);

        final int childDepthIndex = rootId.depthLength();

        final TreeId expandsTo;
        @Nullable
        Quadrant quadrant = computeQuadrant(treeBounds, childDepthIndex);
        if (quadrant == null) {
            final int unpromotableBucketIndex = unpromotableBucketIndex();
            final boolean rootIsCanonical = rootId.contains(unpromotableBucketIndex);
            final int normalizedSizeLimit;
            if (rootIsCanonical) {
                // already know we're inside a canonical (unpromotables) tree
                normalizedSizeLimit = 512;
            } else {
                normalizedSizeLimit = normalizedSizeLimit();
                // it may be a quad wit sub-quads instead of an unpromotables tree
                boolean isValidQuad = originalTree.bucketsSize() > 0;
                for (Quadrant q : Quadrant.VALUES) {
                    java.util.Optional<Bucket> treeBucket = originalTree
                            .getBucket(q.getBucketNumber());
                    if (treeBucket.isPresent()) {
                        Envelope bucketBounds = treeBucket.get().bounds().orNull();
                        Quadrant bucketQuad = computeQuadrant(bucketBounds, childDepthIndex);
                        if (bucketQuad == null) {
                            isValidQuad = false;
                            break;
                        }
                    }
                }
                if (isValidQuad) {
                    return rootId;
                }
            }
            boolean overflowed = size > normalizedSizeLimit;
            if (overflowed) {
                expandsTo = rootId.newChild(unpromotableBucketIndex);
            } else {
                expandsTo = rootId;
            }
        } else {
            expandsTo = rootId.newChild(quadrant.getBucketNumber());
        }
        return expandsTo;
    }

    /**
     * To be called by {@link #mergeRoot}, figures out if the {@link TreeId} for a DAG whose
     * {@link DAG#originalTreeId() RevTree} is {@code original} was {@link #collapse collapsed} and
     * hence needs to be expanded, returning the {@link TreeId} the DAG should have instead.
     * 
     * @param original
     * @param dagId
     * @return
     */
    public TreeId computeExpandedTreeId(RevTree original, TreeId dagId) {
        final long size = original.size();
        final Envelope treeBounds = RevObjects.boundsOf(original);
        final List<Integer> quadrantsByDepth = bucketsByDepth(treeBounds, maxDepth);
        TreeId targetId;
        if (quadrantsByDepth.size() <= dagId.depthLength()) {
            targetId = dagId;
        } else {
            targetId = TreeId.valueOf(quadrantsByDepth);
            boolean overflown = size > normalizedSizeLimit();
            if (overflown) {
                int unpromotableBucketIndex = unpromotableBucketIndex();
                if (targetId.leafBucket() != unpromotableBucketIndex) {
                    targetId = targetId.newChild(unpromotableBucketIndex);
                }
            }
        }
        return targetId;
        //@formatter:off
        /* the following code block is commented out cause tree shrinking needs to be revisited
        if (original.buckets().isEmpty()) {
            return dagId;
        }
        if (treeBounds.isNull()) {
            return dagId;
        }

        TreeId originalPathToRoot = dagId;
        final int startDepthIndex = originalPathToRoot.depthIndex() + 1;

        for (int depthIndex = startDepthIndex; depthIndex < maxDepth; depthIndex++) {
            Quadrant quadrant = computeQuadrant(treeBounds, depthIndex);
            if (quadrant == null) {
                break;
            }
            int bucketId = quadrant.getBucketNumber();
            originalPathToRoot = originalPathToRoot.newChild(bucketId);
        }
        int depthLength = originalPathToRoot.depthLength();
        if (depthLength == maxDepth) {
            int normalizedSizeLimit = normalizedSizeLimit(maxDepth - 1);
            if (size > normalizedSizeLimit) {
                // couldn't fit all nodes in the DAG at maxDepth depth, so they shall have been
                // moved to unpromotable
                originalPathToRoot = originalPathToRoot.newChild(unpromotableBucketIndex(maxDepth));
            }
        }
        return originalPathToRoot;
        */
        //@formatter:on
    }

    /**
     * The fixed maximun size of a leaf {@link RevTree}, at any depth, when built as a quad-tree.
     * 
     * @return {@code 128}
     */
    public int normalizedSizeLimit() {
        return 128;
    }

    /**
     * @see #normalizedSizeLimit()
     */
    @Override
    public int normalizedSizeLimit(final int depthIndex) {
        return normalizedSizeLimit();
    }

    @Override
    protected Comparator<NodeId> getNodeOrdering() {
        return CanonicalClusteringStrategy.CANONICAL_ORDER;
    }

    /**
     * Returns the bucket index in the range 0-3 corresponding to this node at the specified depth
     * (i.e. the bucket index represents a quadrant), or {@code -1} if the spatial bounds of this
     * node don't fit on a single child quadrant and hence the node shall be kept at the current
     * tree node (hence creating a mixed {@link RevTree} with both direct children and buckets).
     */
    public @Override int bucket(final NodeId nodeId, final int depthIndex) {
        final Envelope nodeBounds = nodeId.value();
        final Quadrant quadrantAtDepth = computeQuadrant(nodeBounds, depthIndex);

        if (quadrantAtDepth == null) {
            return -1;
        }
        return quadrantAtDepth.ordinal();
    }

    @Override
    protected int unpromotableBucketIndex(final int depthIndex) {
        return unpromotableBucketIndex();
    }

    public int unpromotableBucketIndex() {
        return Quadrant.VALUES.length;
    }

    /**
     * @return a {@link NodeId} whose {@link NodeId#value() value} is the node's
     *         {@link Node#bounds() bounds} {@link Envelope} or {@code null}
     */
    @Override
    public NodeId computeId(final Node node) {
        @Nullable
        Envelope bounds = node.bounds().orNull();
        return new NodeId(node.getName(), bounds);
    }

    @Override
    public int put(final Node node) {
        Preconditions.checkArgument(TYPE.FEATURE == node.getType(),
                "Can't add non feature nodes to quad-tree: %s", node);
        return super.put(node);
    }

    /**
     * Overrides to implement a simple optimization for the case where the node bounds haven't
     * changed and hence avoid calling remove and then put, but call put only, since the
     * {@code NodeId} is guaranteed to lay on the same bucket at any depth.
     */
    @Override
    public int update(Node oldNode, Node newNode) {
        Optional<Envelope> oldBounds = oldNode.bounds();
        Optional<Envelope> newBounds = newNode.bounds();
        if (oldBounds.equals(newBounds)) {
            // in case the bounds didn't change, put will override the old value,
            // otherwise need to remove old and add new separately
            Preconditions.checkArgument(oldNode.getName().equals(newNode.getName()));
            int delta = put(newNode);
            if (delta == 0 && !oldNode.equals(newNode)) {
                delta = 1;
            }
        }
        return super.update(oldNode, newNode);
    }

    /**
     * Computes the quadrant {@code nodeBounds} fall into at the given {@code depthIndex}.
     * 
     * @return the quadrant the bounds fall into, or {@code null} if bounds can't be fully contained
     *         by a quadrant at the given depth, or {@link #maxDepth} has been reached.
     */
    @Nullable
    Quadrant computeQuadrant(@Nullable Envelope nodeBounds, final int depthIndex) {
        if (depthIndex >= maxDepth || nodeBounds == null || nodeBounds.isNull()) {
            return null;
        }

        final Envelope parentQuadrantBounds = new Envelope(this.maxBounds);

        Envelope qBounds = new Envelope();

        for (int depth = 0; depth <= depthIndex; depth++) {
            for (int q = 0; q < Quadrant.VALUES.length; q++) {
                Quadrant quadrant = Quadrant.VALUES[q];
                quadrant.slice(parentQuadrantBounds, qBounds);
                if (qBounds.contains(nodeBounds)) {
                    if (depth == depthIndex) {
                        return quadrant;
                    }
                    parentQuadrantBounds.init(qBounds);
                    break;
                }
            }
        }

        return null;
    }

    @VisibleForTesting
    List<Quadrant> quadrantsByDepth(NodeId node, final int maxDepth) {
        final Envelope nodeBounds = node.value();
        List<Integer> bucketsByDepth = bucketsByDepth(nodeBounds, maxDepth);
        List<Quadrant> quads = new ArrayList<>(bucketsByDepth.size());
        for (int depthIndex = 0; depthIndex < bucketsByDepth.size(); depthIndex++) {
            int bucket = bucketsByDepth.get(depthIndex).intValue();
            if (bucket == unpromotableBucketIndex(depthIndex)) {
                break;
            }
            Quadrant quad = Quadrant.VALUES[bucket];
            quads.add(quad);
        }
        return quads;
    }

    /**
     * Computes the bucket numbers (range {@code [0-4]} that correspond to the given bounds,
     * stopping at the depth index where a {@code 4} (unpromotable) is found, so the result has at
     * least one element (in case the bounds are unpromotable at the root), and at most
     * {@code maxDepth} element.
     * 
     * @param nodeBounds
     * @param maxDepth
     * @return
     */
    @VisibleForTesting
    List<Integer> bucketsByDepth(final Envelope nodeBounds, final int maxDepth) {
        if (nodeBounds == null || nodeBounds.isNull()) {
            return Collections.emptyList();
        }

        List<Integer> bucketsByDepth = new ArrayList<>(maxDepth);

        final Quadrant[] quadrants = Quadrant.VALUES;

        final Envelope parentQuadrantBounds = new Envelope(this.maxBounds);

        Envelope qBounds = new Envelope();

        for (int depthIndex = 0; depthIndex < maxDepth; depthIndex++) {
            boolean unpromotable = true;
            for (int quadIndex = 0; quadIndex < 4; quadIndex++) {
                Quadrant quadrant = quadrants[quadIndex];
                quadrant.slice(parentQuadrantBounds, qBounds);
                if (qBounds.contains(nodeBounds)) {
                    unpromotable = false;
                    bucketsByDepth.add(Integer.valueOf(quadrant.getBucketNumber()));
                    parentQuadrantBounds.init(qBounds);
                    break;
                }
            }
            if (unpromotable) {
                bucketsByDepth.add(Integer.valueOf(unpromotableBucketIndex(depthIndex)));
                break;
            }
        }
        return bucketsByDepth;
    }

    static final TreeId failingDag = TreeId.fromString("[1, 0, 2, 2, 3, 2, 0, 0, 2, 2, 3, 1]");

    boolean startedRemoving = false;

    // protected @Override synchronized int put(final DAG dag, final NodeId nodeId,
    // final boolean remove) {
    // mergeRoot(dag);
    //
    // long prechildcount = dag.getTotalChildCount();
    // if (remove && 129 == prechildcount && dag.getId().equals(failingDag)) {
    // System.out.printf("### Removing %s\t from %s. pre: %,d, thread: %s\n", nodeId.name,
    // dag.getId(), prechildcount, Thread.currentThread().getName());
    // }
    // int delta = super.put(dag, nodeId, remove);
    // if (dag.getId().equals(failingDag)) {
    // if (remove) {
    // startedRemoving = true;
    // long childcount = dag.getTotalChildCount();
    // System.out.printf(
    // "<<< Removed %s\t from %s. pre: %,d, post: %,d, delta: %d, thread: %s\n",
    // nodeId.name, dag.getId(), prechildcount, childcount, delta,
    // Thread.currentThread().getName());
    // } else if (startedRemoving) {
    // long childcount = dag.getTotalChildCount();
    // System.out.printf(
    // ">>> Added %s\t to %s. pre: %,d, post: %,d, delta: %d, thread: %s\n",
    // nodeId.name, dag.getId(), prechildcount, childcount, delta,
    // Thread.currentThread().getName());
    // }
    // }
    // return delta;
    // }
}
