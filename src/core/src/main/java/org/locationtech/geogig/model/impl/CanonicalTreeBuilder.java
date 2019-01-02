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

import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
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
    CanonicalTreeBuilder(final ObjectStore store, final RevTree original) {
        super(store, original);

        ClusteringStrategy canonical = ClusteringStrategyBuilder.canonical(store).original(original)
                .build();

        this.clusteringStrategy = canonical;
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
        Node removeNode = RevObjectFactory.defaultInstance().createNode(featureId, ObjectId.NULL,
                ObjectId.NULL, TYPE.FEATURE, null, null);
        clusteringStrategy().remove(removeNode);
        return this;
    }

    @Override
    protected final ClusteringStrategy clusteringStrategy() {
        return clusteringStrategy;
    }

    /**
     * Factory method to create a tree builder that clusters subtrees and nodes according to
     * {@link CanonicalNodeNameOrder}
     */
    public static CanonicalTreeBuilder create(final ObjectStore store) {
        return create(store, RevTree.EMPTY);
    }

    /**
     * Factory method to create a tree builder that clusters subtrees and nodes according to
     * {@link CanonicalNodeNameOrder}, and whose internal structure starts by matching the provided
     * {@code original} tree.
     */
    public static CanonicalTreeBuilder create(final ObjectStore store, final RevTree original) {
        checkNotNull(store);
        checkNotNull(original);
        CanonicalTreeBuilder builder = new CanonicalTreeBuilder(store, original);
        return builder;
    }
}
