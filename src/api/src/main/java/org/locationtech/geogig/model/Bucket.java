/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import lombok.NonNull;

/**
 * A Bucket is merely a bounded pointer to another tree in a {@link RevTree} data structure.
 * <p>
 * {@link Node}s are pointers to named objects such as feature trees or features, while a Bucket is
 * a pointer to the {@link RevTree}s it's parent tree is split into when the builder's imposed split
 * threshold is surpassed.
 * 
 * @see RevTree#getBuckets()
 * @since 1.0
 */
public abstract class Bucket implements Bounded, Comparable<Bucket> {

    public abstract int getIndex();

    /**
     * Equality check based purely on {@link #getObjectId() ObjectId}
     */
    public final @Override boolean equals(Object o) {
        return RevObjects.equals(this, o);
    }

    public final @Override int hashCode() {
        return RevObjects.hashCode(this);
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }

    public final @Override int compareTo(@NonNull Bucket b) {
        int c = Integer.compare(getIndex(), b.getIndex());
        if (c == 0) {
            c = getObjectId().compareTo(b.getObjectId());
        }
        return c;
    }
}
