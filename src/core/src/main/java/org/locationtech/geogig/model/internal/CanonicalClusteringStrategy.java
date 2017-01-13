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

    /**
     * @return the max number of nodes a leaf tree can hold at depth {@code depthIndex} as mandated
     *         by {@link CanonicalNodeNameOrder}
     */
    @Override
    public int normalizedSizeLimit(final int depthIndex) {
        return CanonicalNodeNameOrder.normalizedSizeLimit(depthIndex);
    }

    /**
     * @return the bucket corresponding to {@code nodeId} at depth {@code depthIndex} as mandated by
     *         {@link CanonicalNodeNameOrder}
     */
    public int bucket(final NodeId nodeId, final int depthIndex) {
        long bucketsByDepth = ((CanonicalNodeId) nodeId).bucketsByDepth();
        int bucket = CanonicalNodeNameOrder.bucket(bucketsByDepth, depthIndex);
        return bucket;
    }

    @Override
    public CanonicalNodeId computeId(Node node) {

        String name = node.getName();
        UnsignedLong hashCodeLong = CANONICAL_ORDER.hashCodeLong(name);

        return new CanonicalNodeId(hashCodeLong.longValue(), name);
    }

}