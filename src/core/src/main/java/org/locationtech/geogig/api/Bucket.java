/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;

/**
 *
 */
public abstract class Bucket implements Bounded {

    private final ObjectId bucketTree;

    private Bucket(ObjectId id) {
        this.bucketTree = id;
    }

    /**
     * @deprecated use {@link #getObjectId()} instead
     */
    @Deprecated
    public ObjectId id() {
        return bucketTree;
    }

    @Override
    public ObjectId getObjectId() {
        return bucketTree;
    }

    @Override
    public String toString() {
        Envelope bounds = new Envelope();
        expand(bounds);
        return getClass().getSimpleName() + "[" + getObjectId() + "] " + bounds;
    }

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

    private static class PointBucket extends Bucket {

        private final double x;

        private final double y;

        public PointBucket(ObjectId id, double x, double y) {
            super(id);
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean intersects(Envelope env) {
            return env.intersects(x, y);
        }

        @Override
        public void expand(Envelope env) {
            env.expandToInclude(x, y);
        }

        @Override
        public Optional<Envelope> bounds() {
            return Optional.of(new Envelope(x, x, y, y));
        }
    }

    private static class RectangleBucket extends Bucket {

        private Envelope bucketBounds;

        public RectangleBucket(ObjectId id, Envelope env) {
            super(id);
            this.bucketBounds = env;
        }

        @Override
        public boolean intersects(Envelope env) {
            return env.intersects(this.bucketBounds);
        }

        @Override
        public void expand(Envelope env) {
            env.expandToInclude(this.bucketBounds);
        }

        @Override
        public Optional<Envelope> bounds() {
            return Optional.of(new Envelope(bucketBounds));
        }
    }

    private static class NonSpatialBucket extends Bucket {

        public NonSpatialBucket(ObjectId id) {
            super(id);
        }

        @Override
        public boolean intersects(Envelope env) {
            return false;
        }

        @Override
        public void expand(Envelope env) {
            // nothing to do
        }

        @Override
        public Optional<Envelope> bounds() {
            return Optional.absent();
        }
    }

    public static Bucket create(final ObjectId bucketTree, final @Nullable Envelope bounds) {
        if (bounds == null || bounds.isNull()) {
            return new NonSpatialBucket(bucketTree);
        }
        if (bounds.getWidth() == 0D && bounds.getHeight() == 0D) {
            return new PointBucket(bucketTree, bounds.getMinX(), bounds.getMinY());
        }
        return new RectangleBucket(bucketTree, new Envelope(bounds));
    }
}
