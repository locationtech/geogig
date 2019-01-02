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

import java.util.Comparator;

import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;

class CanonicalClusteringStrategy extends ClusteringStrategy {
    private static final long serialVersionUID = 1L;

    static final Ordering<NodeId> CANONICAL_ORDER = new Ordering<NodeId>() {
        @Override
        public int compare(NodeId left, NodeId right) {
            return CanonicalNodeNameOrder.INSTANCE.compare(left.name(), right.name());
        }
    };

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
        return canonicalBucket(nodeId, depthIndex);
    }

    @Override
    public NodeId computeId(Node node) {
        String name = node.getName();
        return new NodeId(name);
    }

    @Override
    protected Comparator<NodeId> getNodeOrdering() {
        return CANONICAL_ORDER;
    }

    /**
     * Overrides to only call {@link #put(Node) put(newNode)} since both old and new are guaranteed
     * to fall on the same bucket as mandated by the canonical node order.
     */
    @Override
    public int update(Node oldNode, Node newNode) {
        Preconditions.checkArgument(oldNode.getName().equals(newNode.getName()));
        int delta = put(newNode);
        if (delta == 0 && !oldNode.equals(newNode)) {
            delta = 1;
        }
        return delta;
    }

}