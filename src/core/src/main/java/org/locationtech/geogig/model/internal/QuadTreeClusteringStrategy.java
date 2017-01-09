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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;

/**
 * This class determines how the quadtree clustering strategy puts features in the tree.
 *
 * NOTE: This also supports polygons/lines (i.e. non-point objects). Each feature is only put in ONE
 * location in the tree - the quad that it is fully enclosed by (which might be the root node).
 *
 * NOTE: if the feature doesn't have a bounds (i.e. null or empty), then it is NOT put in the tree
 * (null QuadTreeNodeId).
 *
 * The main entry point is computeId(), which returns a QuadTreeNodeId that defines where that Node
 * should be in the QuadTree. The QuadTreeNodeId contains a list of Quandrants defining which
 * quadrant the feature lies completely inside for EACH level of the quadtree.
 *
 * If QuadTreeNodeId contains quadrants NE, then SE, then NW then it means that:
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
class QuadTreeClusteringStrategy extends ClusteringStrategy {

    private final Envelope maxBounds;

    private final int maxDepth;

    public QuadTreeClusteringStrategy(RevTree original, DAGStorageProvider storageProvider,
            Envelope maxBounds, int maxDepth) {
        super(original, storageProvider);
        this.maxBounds = maxBounds;
        this.maxDepth = maxDepth;
    }

    @Override
    protected int maxBuckets(final int depthIndex) {
        return 4;
    }

    @Override
    public int normalizedSizeLimit(final int depthIndex) {
        return 128;
    }

    /**
     * Returns the bucket index in the range 0-3 corresponding to this node at the specified depth
     * (i.e. the bucket index represents a quadrant), or {@code -1} if the spatial bounds of this
     * node don't fit on a single child quadrant and hence the node shall be kept at the current
     * tree node (hence creating a mixed {@link RevTree} with both direct children and buckets).
     */
    @Override
    public int bucket(final NodeId nodeId, final int depthIndex) {
        Quadrant[] quadrantsByDepth = ((QuadTreeNodeId) nodeId).quadrantsByDepth();
        if (depthIndex < quadrantsByDepth.length) {
            Quadrant quadrant = quadrantsByDepth[depthIndex];
            return quadrant.ordinal();
        }
        return -1;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public Envelope getMaxBound() {
        return maxBounds;
    }

    @Override
    public @Nullable QuadTreeNodeId computeId(final Node node) {
        Optional<Envelope> bounds = node.bounds();
        if (!bounds.isPresent() || bounds.get().isNull()) {
            return null; // no bounds -> not in tree
        }
        return computeIdInternal(node, bounds.get());
    }

    private QuadTreeNodeId computeIdInternal(Node node, Envelope nodeBounds) {

        final int maxDepth = this.maxDepth;
        List<Quadrant> quadrantsByDepth = new ArrayList<>(maxDepth);

        final Quadrant[] quadrants = QuadTreeNodeId.QUADRANTS;

        Envelope parentQuadrantBounds = this.maxBounds;

        for (int depth = 0; depth < maxDepth; depth++) {
            for (int q = 0; q < 4; q++) {
                Quadrant quadrant = quadrants[q];
                Envelope qBounds = quadrant.slice(parentQuadrantBounds);
                if (qBounds.contains(nodeBounds)) {
                    quadrantsByDepth.add(quadrant);
                    parentQuadrantBounds = qBounds;
                    break;
                }
            }
        }

        Quadrant[] nodeQuadrants = quadrantsByDepth.toArray(new Quadrant[quadrantsByDepth.size()]);
        return new QuadTreeNodeId(node.getName(), nodeQuadrants);
    }

}