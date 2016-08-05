/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.experimental.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;

import com.google.common.primitives.UnsignedLong;
import com.vividsolutions.jts.geom.Envelope;

abstract class ClusteringStrategyFactory {

    public abstract ClusteringStrategy create(RevTree original,
            DAGStorageProviderFactory dagStorageFactory);

    public static ClusteringStrategyFactory canonical() {
        return CANONICAL;
    }

    public static ClusteringStrategyFactory quadtree(Envelope maxBounds, int maxDepth) {
        return new QuadTreeClusteringStrategyFactory(maxBounds, maxDepth);
    }

    private static ClusteringStrategyFactory CANONICAL = new ClusteringStrategyFactory() {

        @Override
        public ClusteringStrategy create(RevTree original,
                DAGStorageProviderFactory storageFactory) {
            DAGStorageProvider dagStorageProvider = storageFactory.canonical();
            return new CanonicalClusteringStrategy(original, dagStorageProvider);
        }
    };

    private static class QuadTreeClusteringStrategyFactory extends ClusteringStrategyFactory {

        private final Envelope maxBounds;

        private final int maxDepth;

        public QuadTreeClusteringStrategyFactory(final Envelope maxBounds, final int maxDepth) {
            checkNotNull(maxBounds, "maxBounds is null");
            checkArgument(maxDepth > 0, "maxDepth must be >= 0");
            this.maxBounds = new Envelope(maxBounds);
            this.maxDepth = maxDepth;
        }

        @Override
        public ClusteringStrategy create(RevTree original,
                DAGStorageProviderFactory storageFactory) {
            DAGStorageProvider dagStorageProvider = storageFactory.quadtree();
            return new QuadTreeClusteringStrategy(original, dagStorageProvider, maxBounds,
                    maxDepth);
        }
    };

    private static class CanonicalClusteringStrategy extends ClusteringStrategy {

        private static final CanonicalNodeNameOrder CANONICAL_ORDER = CanonicalNodeNameOrder.INSTANCE;

        public CanonicalClusteringStrategy(RevTree original, DAGStorageProvider storageProvider) {
            super(original, storageProvider);
        }

        @Override
        public int maxBuckets(final int depthIndex) {
            return CanonicalNodeNameOrder.maxBucketsForLevel(depthIndex);
        }

        //
        // @Override
        // public int maxDepth() {
        // return 8;
        // }

        @Override
        public int normalizedSizeLimit(final int depthIndex) {
            return CanonicalNodeNameOrder.normalizedSizeLimit(depthIndex);
        }

        @Override
        public CanonicalNodeId computeId(Node node) {

            String name = node.getName();
            UnsignedLong hashCodeLong = CANONICAL_ORDER.hashCodeLong(name);

            return new CanonicalNodeId(hashCodeLong, name);
        }
    }
}
