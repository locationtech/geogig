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

public class CanonicalTreeBuilder extends AbstractTreeBuilder implements RevTreeBuilder {

    private final ClusteringStrategy clusteringStrategy;

    /**
     * Copy constructor
     * 
     * @param store {@link org.locationtech.geogig.storage.ObjectStore ObjectStore} with which to
     *        initialize this RevTreeBuilder.
     * @param original {@link org.locationtech.geogig.api.RevTree RevTree} to copy.
     */
    public CanonicalTreeBuilder(final ObjectStore store, final RevTree original) {
        super(store, original);

        ClusteringStrategy canonical = ClusteringStrategyBuilder.canonical(store).original(original)
                .build();

        this.clusteringStrategy = canonical;
    }

    @Override
    protected final ClusteringStrategy clusteringStrategy() {
        return clusteringStrategy;
    }

}
