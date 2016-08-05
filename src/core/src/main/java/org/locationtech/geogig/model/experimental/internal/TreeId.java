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

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedBytes;

final class TreeId implements Comparable<TreeId> {

    public final byte[] bucketIndicesByDepth;

    TreeId(byte[] bucketIndicesByDepth) {
        this.bucketIndicesByDepth = bucketIndicesByDepth;
    }

    @Override
    public boolean equals(Object o) {
        // don't bother checking for instanceof TreeId, this is private and will never be
        // compared to something else
        return compareTo((TreeId) o) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bucketIndicesByDepth);
    }

    @Override
    public int compareTo(TreeId o) {
        return UnsignedBytes.lexicographicalComparator().compare(bucketIndicesByDepth,
                o.bucketIndicesByDepth);
    }

    @Override
    public String toString() {
        return Arrays.toString(bucketIndicesByDepth);
    }

    public Integer bucketIndex(final int depth) {
        Preconditions.checkArgument(depth < bucketIndicesByDepth.length,
                "depth (%s) is outside range [0 - %s]", depth, bucketIndicesByDepth.length - 1);
        return bucketIndicesByDepth[depth] & 0xFF;
    }
}