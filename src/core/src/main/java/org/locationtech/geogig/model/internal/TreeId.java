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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;

import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedBytes;

/**
 * A tree identifier defined by the array of bucket indices it belongs to up to a given depth, as
 * opposed to the final SHA-1 identifier of a built {@code RevTree}.
 *
 */
final class TreeId implements Comparable<TreeId>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Comparator<byte[]> COMPARATOR = UnsignedBytes.lexicographicalComparator();

    public final byte[] bucketIndicesByDepth;

    /**
     * @param bucketIndicesByDepth array of bucket indexes that define this tree id
     * @implNote: this instance takes ownership of the argument array
     */
    TreeId(byte[] bucketIndicesByDepth) {
        this.bucketIndicesByDepth = bucketIndicesByDepth;
    }

    /**
     * @return the length of this tree id (i.e. how many levels of depth are represented)
     */
    public int depthLength() {
        return bucketIndicesByDepth.length;
    }

    /**
     * @param depthIndex zero based index of the depth at which to return the bucket index for
     * @return the bucket index this tree id belongs to at the specified depth index
     */
    public Integer bucketIndex(final int depthIndex) {
        Preconditions.checkArgument(depthIndex < bucketIndicesByDepth.length,
                "depth (%s) is outside range [0 - %s]", depthIndex,
                bucketIndicesByDepth.length - 1);
        return Integer.valueOf(bucketIndicesByDepth[depthIndex] & 0xFF);
    }

    @Override
    public boolean equals(Object o) {
        // don't bother checking for instanceof TreeId, this is private and will never be
        // compared to something else
        return Arrays.equals(bucketIndicesByDepth, ((TreeId) o).bucketIndicesByDepth);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bucketIndicesByDepth);
    }

    @Override
    public int compareTo(TreeId o) {
        return COMPARATOR.compare(bucketIndicesByDepth, o.bucketIndicesByDepth);
    }

    @Override
    public String toString() {
        return Arrays.toString(bucketIndicesByDepth);
    }

    /**
     * Factory method to create a child tree id of this one at with the given bucket id
     */
    public TreeId newChild(int bucketId) {
        int depthLength = depthLength();
        byte[] childIndices = new byte[depthLength + 1];
        System.arraycopy(this.bucketIndicesByDepth, 0, childIndices, 0, depthLength);
        childIndices[depthLength] = (byte) bucketId;
        return new TreeId(childIndices);
    }
}