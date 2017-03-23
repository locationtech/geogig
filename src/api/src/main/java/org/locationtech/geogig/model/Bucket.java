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

import static com.google.common.base.Optional.fromNullable;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Envelope;

/**
 * A Bucket is merely a bounded pointer to another tree in a {@link RevTree} data structure.
 * <p>
 * {@link Node}s are pointers to named objects such as feature trees or features, while a Bucket is
 * a pointer to the {@link RevTree}s it's parent tree is split into when the builder's imposed split
 * threshold is surpassed.
 * 
 * @see RevTree#buckets()
 * @since 1.0
 */
public abstract class Bucket implements Bounded {

    public static Bucket create(final ObjectId bucketTree, final @Nullable Envelope bounds) {
        Preconditions.checkNotNull(bucketTree);
        Float32Bounds b32 = Float32Bounds.valueOf(bounds);
        return new BucketImpl(bucketTree, b32);
    }

    /**
     * @return the {@link ObjectId} of the tree this bucket points to
     */
    @Override
    public abstract ObjectId getObjectId();

    /**
     * Equality check based purely on {@link #getObjectId() ObjectId}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Bucket)) {
            return false;
        }
        return getObjectId().equals(((Bucket) o).getObjectId());
    }

    private static class BucketImpl extends Bucket {
        private final ObjectId bucketTree;

        private final Float32Bounds bounds;

        private BucketImpl(ObjectId id, Float32Bounds bounds) {
            this.bucketTree = id;
            this.bounds = bounds;
        }

        @Override
        public ObjectId getObjectId() {
            return bucketTree;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + getObjectId() + "] "
                    + (bounds.isNull() ? "" : bounds.toString());
        }

        @Override
        public boolean intersects(Envelope env) {
            return bounds.intersects(env);
        }

        @Override
        public void expand(Envelope env) {
            bounds.expand(env);
        }

        @Override
        public Optional<Envelope> bounds() {
            return fromNullable(bounds.isNull() ? null : bounds.asEnvelope());
        }

    }
}
