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
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;

import com.vividsolutions.jts.geom.Envelope;

class QuadTreeClusteringStrategy extends ClusteringStrategy {

    private final Envelope maxBounds;

    private final int maxDepth;

    public QuadTreeClusteringStrategy(RevTree original, DAGStorageProvider storageProvider,
            Envelope maxBounds, int maxDepth) {
        super(original, storageProvider);
        this.maxBounds = maxBounds;
        this.maxDepth = maxDepth;
    }

    // @Override
    // protected int maxDepth() {
    // return 12;
    // }
    //
    @Override
    protected int maxBuckets(final int depthIndex) {
        return 4;
    }

    @Override
    public int normalizedSizeLimit(final int depthIndex) {
        return 128;
    }

    @Override
    public @Nullable QuadTreeNodeId computeId(final Node node) {
        QuadTreeNodeId nodeId = null;
        if (node.bounds().isPresent()) {
            nodeId = computeIdInternal(node);
        }
        return nodeId;
    }

    private QuadTreeNodeId computeIdInternal(Node node) {

        final int maxDepth = this.maxDepth;

        Envelope nodeBounds = node.bounds().get();
        List<Quadrant> quadrantsByDepth = new ArrayList<>(maxDepth);

        final Quadrant[] quadrants = Quadrant.values();

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