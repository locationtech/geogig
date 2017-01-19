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
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
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

    @Override
    public CanonicalTreeBuilder remove(Node node) {
        checkNotNull(node, "Argument node is null");
        checkState(!disposed.get(), "TreeBuilder is already disposed");
        clusteringStrategy().remove(node);
        return this;
    }

    /**
     * Removes a node from the builder given its {@link Node#getName() name}
     * <p>
     * Since a canonical tree builder uses only the node names to arrange the nodes on the internal
     * tree structure, it is safe to overload {@link #remove(Node)} with a String argument version.
     * 
     * @param featureId the name of the node to remove
     * @return {@code this}
     */
    public final CanonicalTreeBuilder remove(final String featureId) {
        checkNotNull(featureId, "Argument featureId is null");
        checkState(!disposed.get(), "TreeBuilder is already disposed");
        Node removeNode = Node.create(featureId, ObjectId.NULL, ObjectId.NULL, TYPE.FEATURE, null);
        clusteringStrategy().remove(removeNode);
        return this;
    }
}
