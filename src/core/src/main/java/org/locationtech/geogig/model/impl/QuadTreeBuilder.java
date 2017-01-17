/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.internal.ClusteringStrategy;
import org.locationtech.geogig.model.internal.ClusteringStrategyBuilder;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Envelope;

public class QuadTreeBuilder extends AbstractTreeBuilder implements RevTreeBuilder {

    private final ClusteringStrategy clusteringStrategy;

    protected QuadTreeBuilder(ObjectStore store, RevTree original, ClusteringStrategy strategy) {
        super(store, original);
        clusteringStrategy = strategy;
    }

    public static RevTreeBuilder quadTree(final ObjectStore store) {
        return QuadTreeBuilder.quadTree(store, RevTree.EMPTY);
    }

    @Override
    protected final ClusteringStrategy clusteringStrategy() {
        return clusteringStrategy;
    }

    public static QuadTreeBuilder quadTree(final ObjectStore store, final RevTree original) {
        Preconditions.checkNotNull(store);
        Preconditions.checkNotNull(original);
        final Envelope MAX_BOUNDS_WGS84 = new Envelope(-180, 180, -90, 90);
        final int DEFAULT_MAX_DEPTH = 12;
        return QuadTreeBuilder.quadTree(store, original, MAX_BOUNDS_WGS84, DEFAULT_MAX_DEPTH);
    }

    public static QuadTreeBuilder quadTree(final ObjectStore store, final RevTree original,
            final Envelope maxBounds, final int maxDepth) {
        Preconditions.checkNotNull(store);
        Preconditions.checkNotNull(maxBounds);
        Preconditions.checkArgument(maxDepth > 0);

        ClusteringStrategy strategy = ClusteringStrategyBuilder.quadTree(store).original(original)
                .maxBounds(maxBounds).maxDepth(maxDepth).build();
        QuadTreeBuilder builder = new QuadTreeBuilder(store, RevTree.EMPTY, strategy);
        return builder;
    }
}
