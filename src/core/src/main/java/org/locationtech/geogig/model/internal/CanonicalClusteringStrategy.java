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

import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;

import com.google.common.primitives.UnsignedLong;

class CanonicalClusteringStrategy extends ClusteringStrategy {

    private static final CanonicalNodeNameOrder CANONICAL_ORDER = CanonicalNodeNameOrder.INSTANCE;

    public CanonicalClusteringStrategy(RevTree original, DAGStorageProvider storageProvider) {
        super(original, storageProvider);
    }

    @Override
    public int maxBuckets(final int depthIndex) {
        return CanonicalNodeNameOrder.maxBucketsForLevel(depthIndex);
    }

    @Override
    public int normalizedSizeLimit(final int depthIndex) {
        return CanonicalNodeNameOrder.normalizedSizeLimit(depthIndex);
    }

    @Override
    public CanonicalNodeId computeId(Node node) {

        String name = node.getName();
        UnsignedLong hashCodeLong = CANONICAL_ORDER.hashCodeLong(name);

        return new CanonicalNodeId(hashCodeLong.longValue(), name);
    }
}