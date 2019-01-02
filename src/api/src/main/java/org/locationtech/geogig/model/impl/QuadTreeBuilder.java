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

import org.locationtech.geogig.model.NodeOrdering;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.internal.ClusteringStrategy;
import org.locationtech.geogig.model.internal.ClusteringStrategyBuilder;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;

import lombok.NonNull;

public class QuadTreeBuilder extends AbstractTreeBuilder implements RevTreeBuilder {

    private final ClusteringStrategy clusteringStrategy;

    protected QuadTreeBuilder(ObjectStore store, RevTree original, ClusteringStrategy strategy) {
        super(store, original);
        clusteringStrategy = strategy;
    }

    protected final @Override ClusteringStrategy clusteringStrategy() {
        return clusteringStrategy;
    }

    public static QuadTreeBuilder create(final @NonNull ObjectStore source,
            final @NonNull ObjectStore target, final @NonNull RevTree original,
            final @NonNull Envelope maxBounds) {

        ClusteringStrategy strategy = ClusteringStrategyBuilder//
                .quadTree(source)//
                .original(original)//
                .maxBounds(maxBounds)//
                .build();
        return new QuadTreeBuilder(target, RevTree.EMPTY, strategy);
    }

    public static NodeOrdering nodeOrdering(Envelope maxBounds) {
        return ClusteringStrategyBuilder.quadTreeOrdering(maxBounds);
    }
}
