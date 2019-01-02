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
import java.util.function.BooleanSupplier;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.internal.ClusteringStrategy;
import org.locationtech.geogig.model.internal.DAGTreeBuilder;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;

import lombok.Setter;
import lombok.experimental.Accessors;

public abstract @Accessors(fluent = true) class AbstractTreeBuilder implements RevTreeBuilder {

    protected final ObjectStore target;

    protected @Setter RevTree original;

    protected final AtomicBoolean disposed = new AtomicBoolean(false);

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
    public boolean put(final Node node) {
        checkNotNull(node, "Argument node is null");
        checkState(!disposed.get(), "TreeBuilder is already disposed");
        int delta = clusteringStrategy().put(node);
        checkState(delta != -1);
        return delta == 1;
    }

    @Override
    public boolean remove(Node node) {
        checkNotNull(node, "Argument node is null");
        checkState(!disposed.get(), "TreeBuilder is already disposed");
        boolean removed = clusteringStrategy().remove(node);
        return removed;
    }

    @Override
    public boolean update(Node oldNode, Node newNode) {
        checkNotNull(oldNode, "Argument oldNode is null");
        checkNotNull(newNode, "Argument newNode is null");
        checkState(!disposed.get(), "TreeBuilder is already disposed");
        int delta = clusteringStrategy().update(oldNode, newNode);
        return delta != 0;
    }

    @Override
    public RevTree build() {
        return build(() -> false);
    }

    public @Override void dispose() {
        if (!disposed.get()) {
            build(() -> true);
        }
    }

    @Override
    public final RevTree build(BooleanSupplier abortFlag) {
        Preconditions.checkNotNull(abortFlag);
        boolean alreadyDisposed = disposed.getAndSet(true);

        checkState(!alreadyDisposed, "TreeBuilder is already disposed");
        RevTree tree;
        final ClusteringStrategy clusteringStrategy = clusteringStrategy();
        try {
            tree = DAGTreeBuilder.build(clusteringStrategy, target, abortFlag);
            if (!abortFlag.getAsBoolean()) {
                Preconditions.checkState(tree != null);
                Preconditions.checkState(target.exists(tree.getId()), "tree not saved %s", tree);
                this.original = tree;
            }
        } finally {
            clusteringStrategy.dispose();
        }
        return tree;
    }
}
