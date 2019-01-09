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

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeOrdering;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;

public abstract class ClusteringStrategyBuilder {

    protected final ObjectStore treeStore;

    protected RevTree original = RevTree.EMPTY;

    ClusteringStrategyBuilder(ObjectStore treeStore) {
        checkNotNull(treeStore);
        this.treeStore = treeStore;
    }

    protected ClusteringStrategyBuilder() {
        treeStore = null;
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

    private static final DAGStorageProviderFactory DAGSTOREFACTORY = DAGStorageProviderFactory
            .defaultInstance();

    public @VisibleForTesting static String getDAGStoreName() {
        return DAGSTOREFACTORY.getClass().getSimpleName();
    }

    protected DAGStorageProvider createDAGStoreageProvider() {
        // return new LMDBDAGStorageProvider(treeStore);
        // return new HeapDAGStorageProvider(treeStore);
        // return new RocksdbDAGStorageProvider(treeStore);
        DAGStorageProvider provider;
        provider = DAGSTOREFACTORY.newInstance(treeStore);
        return provider;
    }

    protected abstract ClusteringStrategy buildInternal(DAGStorageProvider dagStoreProvider);

    public static CanonicalClusteringStrategyBuilder canonical(ObjectStore treeStore) {
        return new CanonicalClusteringStrategyBuilder(treeStore);
    }

    public static QuadTreeClusteringStrategyBuilder quadTree(ObjectStore treeStore) {
        return new QuadTreeClusteringStrategyBuilder(treeStore);
    }

    public static NodeOrdering quadTreeOrdering(Envelope maxBounds) {
        return QuadTreeClusteringStrategyBuilder.buildNodeOrdering(maxBounds);
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

        private int maxDepth = -1;

        /**
         * Absolute max depth, to set a hard limit for when too many nodes fall on the same bucket
         * indefinitely or almost indefinitely.
         * <p>
         * An overflowed DAG that reaches this depth (i.e. deph index 19) will have all it's nodes
         * moved to a canonical (i.e. unpromotables) tree at depth index 20.
         * 
         * NOTE: this constant is the maximum depth at which a WGS84 bounds can be split into
         * quadrants that fully contains the envelope for a point when converted to a
         * {@code Float32Bounds} by {@link Node#create}
         * 
         * @see #computeQuadrant(Envelope, int)
         */
        static final int ABSOLUTE_MAX_DEPTH = 35;

        QuadTreeClusteringStrategyBuilder(ObjectStore treeStore) {
            super(treeStore);
        }

        QuadTreeClusteringStrategyBuilder() {
            super();
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

        public static NodeOrdering buildNodeOrdering(@NonNull Envelope maxBounds) {
            Envelope preciseBounds = RevObjects.makePrecise(maxBounds);
            int maxDepth = Quadrant.findMaxDepth(preciseBounds,
                    QuadTreeClusteringStrategyBuilder.ABSOLUTE_MAX_DEPTH);
            return new QuadTreeClusteringStrategy(RevTree.EMPTY, new HeapDAGStorageProvider(null),
                    preciseBounds, maxDepth);
        }

        @Override
        protected ClusteringStrategy buildInternal(DAGStorageProvider dagStoreProvider) {
            checkState(maxBounds != null, "QuadTree max bounds was not set");
            Envelope preciseBounds = RevObjects.makePrecise(maxBounds);
            int maxDepth;
            if (this.maxDepth > -1) {
                maxDepth = this.maxDepth;
            } else {
                maxDepth = Quadrant.findMaxDepth(preciseBounds,
                        QuadTreeClusteringStrategyBuilder.ABSOLUTE_MAX_DEPTH);
            }
            return new QuadTreeClusteringStrategy(original, dagStoreProvider, preciseBounds,
                    maxDepth);
        }

        public QuadTreeClusteringStrategyBuilder maxBounds(Envelope maxBounds) {
            checkNotNull(maxBounds, "maxBounds is null");
            checkArgument(!maxBounds.isNull(), "maxBounds is not initialized");
            this.maxBounds = new Envelope(maxBounds);
            return this;
        }

        @VisibleForTesting
        public QuadTreeClusteringStrategyBuilder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }
    }
}
