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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.model.Node;
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

    public static RevTreeBuilder quadTree(final ObjectStore source, final ObjectStore target) {
        return QuadTreeBuilder.quadTree(source, target, RevTree.EMPTY);
    }

    @Override
    protected final ClusteringStrategy clusteringStrategy() {
        return clusteringStrategy;
    }

    public static QuadTreeBuilder quadTree(final ObjectStore source, final ObjectStore target,
            final RevTree original) {
        Preconditions.checkNotNull(source);
        Preconditions.checkNotNull(target);
        Preconditions.checkNotNull(original);
        final Envelope MAX_BOUNDS_WGS84 = new Envelope(-180, 180, -90, 90);
        return QuadTreeBuilder.quadTree(source, target, original, MAX_BOUNDS_WGS84);
    }

    public static QuadTreeBuilder quadTree(final ObjectStore source, final ObjectStore target,
            final RevTree original, final Envelope maxBounds) {
        Preconditions.checkNotNull(source);
        Preconditions.checkNotNull(target);
        Preconditions.checkNotNull(maxBounds);

        ClusteringStrategy strategy = ClusteringStrategyBuilder.quadTree(source).original(original)
                .maxBounds(maxBounds).build();
        QuadTreeBuilder builder = new QuadTreeBuilder(target, RevTree.EMPTY, strategy);
        return builder;
    }

    @Override
    public QuadTreeBuilder remove(Node node) {
        checkNotNull(node, "Argument node is null");
        checkState(!disposed.get(), "TreeBuilder is already disposed");
        clusteringStrategy().remove(node);
        return this;
    }
}
