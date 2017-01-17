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

import java.util.concurrent.atomic.AtomicBoolean;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.internal.ClusteringStrategy;
import org.locationtech.geogig.model.internal.DAGTreeBuilder;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;

abstract class AbstractTreeBuilder implements RevTreeBuilder {

    protected final ObjectStore target;

    protected RevTree original;

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    protected AbstractTreeBuilder(final ObjectStore store) {
        this(store, RevTree.EMPTY);
    }

    protected AbstractTreeBuilder(final ObjectStore store, final RevTree original) {
        checkNotNull(store);
        checkNotNull(original);
        this.target = store;
        this.original = original;
    }

    protected abstract ClusteringStrategy clusteringStrategy();

    @Override
    public final AbstractTreeBuilder put(final Node node) {
        checkNotNull(node, "Argument node is null");
        checkState(!disposed.get(), "TreeBuilder is already disposed");
        clusteringStrategy().put(node);
        return this;
    }

    @Override
    public final AbstractTreeBuilder remove(final String featureId) {
        checkNotNull(featureId, "Argument featureId is null");
        checkState(!disposed.get(), "TreeBuilder is already disposed");
        clusteringStrategy().remove(featureId);
        return this;
    }

    @Override
    public RevTree build() {
        boolean alreadyDisposed = disposed.getAndSet(true);
        checkState(!alreadyDisposed, "TreeBuilder is already disposed");
        RevTree tree;
        ClusteringStrategy clusteringStrategy = clusteringStrategy();
        tree = DAGTreeBuilder.build(clusteringStrategy, target);
        Preconditions.checkState(target.exists(tree.getId()), "tree not saved %s", tree);
        this.original = tree;
        clusteringStrategy().dispose();
        return tree;
    }


    public int getDepth() {
        return clusteringStrategy().depth();
    }

}
