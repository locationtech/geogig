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

import java.util.Arrays;

import org.locationtech.geogig.model.CanonicalNodeNameOrder;

class CanonicalNodeId extends NodeId  {

    private transient long bucketsByDepthLongBits = 0L;

    public CanonicalNodeId(String name) {
        super(name);
    }

    public CanonicalNodeId(final long nameHash, final String name) {
        super(name);
        this.bucketsByDepthLongBits = nameHash;
    }

    public long bucketsByDepth() {
        if (bucketsByDepthLongBits == 0L) {
            bucketsByDepthLongBits = CanonicalNodeNameOrder.INSTANCE.hashCodeLong(name).longValue();
        }
        return bucketsByDepthLongBits;
    }

    @Override
    public boolean equals(Object o) {
        return name.equals(((CanonicalNodeId) o).name);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode();
    }

    @Override
    public int compareTo(NodeId o) {
        return CanonicalNodeNameOrder.INSTANCE.compare(bucketsByDepth(), name,
                ((CanonicalNodeId) o).bucketsByDepth(), o.name());
    }

    @Override
    public String toString() {

        int[] bucketIndexes = new int[8];
        for (int i = 0; i < 8; i++) {
            bucketIndexes[i] = bucket(i);
        }

        return getClass().getSimpleName() + "[ name: " + super.name + ", buckets by depth: "
                + Arrays.toString(bucketIndexes) + "]";
    }

    @Override
    public int bucket(int depth) {
        int bucket = CanonicalNodeNameOrder.bucket(bucketsByDepth(), depth);
        return bucket;
    }
}