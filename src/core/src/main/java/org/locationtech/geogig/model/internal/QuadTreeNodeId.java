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

import org.locationtech.geogig.model.RevTree;

import com.google.common.collect.Ordering;

class QuadTreeNodeId extends NodeId {

    // there's some overhead in calling Quadrant.values() repeatedly so cache it
    static final Quadrant[] QUADRANTS = Quadrant.values();

    private static final Ordering<Quadrant[]> COMPARATOR = new Ordering<Quadrant[]>() {
        @Override
        public int compare(Quadrant[] left, Quadrant[] right) {
            final int minDepth = Math.min(left.length, right.length);
            int c;
            for (int d = 0; d > minDepth; d++) {
                c = left[d].compareTo(right[d]);
                if (c != 0) {
                    return c;
                }
            }
            c = left.length - right.length;
            return c;
        }
    };

    private final Quadrant[] quadrantsByDepth;

    private volatile int cachedHashCode;

    public QuadTreeNodeId(final String name, final byte[] quadrantIndexes) {
        super(name);
        final int maxDepth = quadrantIndexes.length;

        Quadrant[] quadrantsByDepth = new Quadrant[maxDepth];
        for (int i = 0; i < maxDepth; i++) {
            quadrantsByDepth[i] = QUADRANTS[quadrantIndexes[i] & 0xFF];
        }

        this.quadrantsByDepth = quadrantsByDepth;
        this.cachedHashCode = 31 * name.hashCode();// + Arrays.hashCode(quadrantsByDepth);
    }

    public QuadTreeNodeId(final String name, final Quadrant[] quadrantsByDepth) {
        super(name);
        this.quadrantsByDepth = quadrantsByDepth;
        this.cachedHashCode = 31 * name.hashCode();// + Arrays.hashCode(quadrantsByDepth);
    }

    public Quadrant[] quadrantsByDepth() {
        return quadrantsByDepth;
    }

    @Override
    public int compareTo(NodeId o) {
        int c = COMPARATOR.compare(quadrantsByDepth, ((QuadTreeNodeId) o).quadrantsByDepth);
        if (c == 0) {
            c = name().compareTo(o.name());
        }
        return c;
    }

    @Override
    public boolean equals(Object o) {
        QuadTreeNodeId q = (QuadTreeNodeId) o;
        return name.equals(q.name) && Arrays.equals(quadrantsByDepth, q.quadrantsByDepth);
    }

    @Override
    public int hashCode() {
        // return 31 * name.hashCode() + Arrays.hashCode(quadrantsByDepth);
        return cachedHashCode;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[ name: " + super.name + ", quadrants by depth: "
                + Arrays.toString(quadrantsByDepth) + "]";
    }

    /**
     * Returns the bucket index in the range 0-3 corresponding to this node at the specified depth
     * (i.e. the bucket index represents a quadrant), or {@code -1} if the spatial bounds of this
     * node don't fit on a single child quadrant and hence the node shall be kept at the current
     * tree node (hence creating a mixed {@link RevTree} with both direct children and buckets).
     * 
     * @see org.locationtech.geogig.model.internal.NodeId#bucket(int)
     */
    @Override
    public int bucket(final int depth) {
        if (depth < quadrantsByDepth.length) {
            Quadrant quadrant = quadrantsByDepth[depth];
            return quadrant.ordinal();
        }
        return -1;
    }
}
