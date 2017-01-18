/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;

import com.vividsolutions.jts.geom.Envelope;

public abstract class ClusteringStrategyBuilder {

    protected final ObjectStore treeStore;

    protected RevTree original = RevTree.EMPTY;

    ClusteringStrategyBuilder(ObjectStore treeStore) {
        checkNotNull(treeStore);
        this.treeStore = treeStore;
    }

    public ClusteringStrategyBuilder original(RevTree original) {
        checkNotNull(original, "null original tree, did you mean RevTree.EMPTY?");
        this.original = original;
        return this;
    }

    public ClusteringStrategy build() {
        DAGStorageProvider dagStoreProvider = createDAGStoreageProvider();
        try {
            return buildInternal(dagStoreProvider);
        } catch (RuntimeException e) {
            dagStoreProvider.dispose();
            throw e;
        }
    }

    protected DAGStorageProvider createDAGStoreageProvider() {
        // return new CachingDAGStorageProvider(treeStore);
        // return new HeapDAGStorageProvider(treeStore);
        return new RocksdbDAGStorageProvider(treeStore);
    }

    protected abstract ClusteringStrategy buildInternal(DAGStorageProvider dagStoreProvider);

    public static CanonicalClusteringStrategyBuilder canonical(ObjectStore treeStore) {
        return new CanonicalClusteringStrategyBuilder(treeStore);
    }

    public static QuadTreeClusteringStrategyBuilder quadTree(ObjectStore treeStore) {
        return new QuadTreeClusteringStrategyBuilder(treeStore);
    }

    public static class CanonicalClusteringStrategyBuilder extends ClusteringStrategyBuilder {

        CanonicalClusteringStrategyBuilder(ObjectStore treeStore) {
            super(treeStore);
        }

        @Override
        public CanonicalClusteringStrategyBuilder original(RevTree original) {
            super.original(original);
            return this;
        }

        @Override
        protected ClusteringStrategy buildInternal(DAGStorageProvider dagStoreProvider) {
            return new CanonicalClusteringStrategy(original, dagStoreProvider);
        }

    }

    public static class QuadTreeClusteringStrategyBuilder extends ClusteringStrategyBuilder {

        private Envelope maxBounds;

        QuadTreeClusteringStrategyBuilder(ObjectStore treeStore) {
            super(treeStore);
        }

        @Override
        public QuadTreeClusteringStrategyBuilder original(RevTree original) {
            super.original(original);
            return this;
        }

        @Override
        public QuadTreeClusteringStrategy build() {
            return (QuadTreeClusteringStrategy) super.build();
        }

        @Override
        protected ClusteringStrategy buildInternal(DAGStorageProvider dagStoreProvider) {
            checkState(maxBounds != null, "QuadTree max bounds was not set");
            return new QuadTreeClusteringStrategy(original, dagStoreProvider, maxBounds);
        }

        public QuadTreeClusteringStrategyBuilder maxBounds(Envelope maxBounds) {
            checkNotNull(maxBounds, "maxBounds is null");
            checkArgument(!maxBounds.isNull(), "maxBounds is not initialized");
            this.maxBounds = new Envelope(maxBounds);
            return this;
        }
    }
}
