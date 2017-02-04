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

    @Override
    public QuadTreeBuilder put(Node node) {
        super.put(node);
        return this;
    }

    @Override
    public QuadTreeBuilder remove(Node node) {
        super.remove(node);
        return this;
    }

    @Override
    public QuadTreeBuilder update(Node oldNode, Node newNode) {
        super.update(oldNode, newNode);
        return this;
    }

    @Override
    protected final ClusteringStrategy clusteringStrategy() {
        return clusteringStrategy;
    }

    public static QuadTreeBuilder create(final ObjectStore source, final ObjectStore target) {
        return QuadTreeBuilder.create(source, target, RevTree.EMPTY);
    }

    public static QuadTreeBuilder create(final ObjectStore source, final ObjectStore target,
            final RevTree original) {
        Preconditions.checkNotNull(source);
        Preconditions.checkNotNull(target);
        Preconditions.checkNotNull(original);
        final Envelope MAX_BOUNDS_WGS84 = new Envelope(-180, 180, -90, 90);
        return QuadTreeBuilder.create(source, target, original, MAX_BOUNDS_WGS84);
    }

    public static QuadTreeBuilder create(final ObjectStore source, final ObjectStore target,
            final RevTree original, final Envelope maxBounds) {
        Preconditions.checkNotNull(source);
        Preconditions.checkNotNull(target);
        Preconditions.checkNotNull(maxBounds);

        ClusteringStrategy strategy = ClusteringStrategyBuilder.quadTree(source).original(original)
                .maxBounds(maxBounds).build();
        QuadTreeBuilder builder = new QuadTreeBuilder(target, RevTree.EMPTY, strategy);
        return builder;
    }
}
