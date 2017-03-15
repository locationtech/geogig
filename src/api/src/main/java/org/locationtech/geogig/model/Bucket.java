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

import com.vividsolutions.jts.geom.Coordinate;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;

/**
 * A Bucket is merely a bounded pointer to another tree in a {@link RevTree} data structure.
 * <p>
 * {@link Node}s are pointers to named objects such as feature trees or features, while a Bucket is a
 * pointer to the {@link RevTree}s it's parent tree is split into when the builder's imposed split
 * threshold is surpassed.
 * 
 * @see RevTree#buckets()
 * @since 1.0
 */
public abstract class Bucket implements Bounded {

    private final ObjectId bucketTree;
    Float32Bounds bounds;


    private Bucket(ObjectId id) {
        this.bucketTree = id;
    }

    /**
     * @return the {@link ObjectId} of the tree this bucket points to
     */
    @Override
    public ObjectId getObjectId() {
        return bucketTree;
    }

    @Override
    public String toString() {
        Envelope bounds = new Envelope();
        expand(bounds);
        return getClass().getSimpleName() + "[" + getObjectId() + "] "
                + (bounds.isNull() ? "" : bounds.toString());
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

    @Override
    public boolean intersects(Envelope env) {
        if (bounds == null)
            return false;
        return bounds.intersects(env);
    }

    @Override
    public void expand(Envelope env) {
        if (bounds != null)
            bounds.expand(env);
    }

    @Override
    public Optional<Envelope> bounds() {
        if  ( (bounds == null) || (bounds.isNull()) )
            return Optional.absent();

        return Optional.of(bounds.asEnvelope());
    }

    void setBounds(Envelope env) {
        bounds = new Float32Bounds(env);
    }

    private static class PointBucket extends Bucket {


        public PointBucket(ObjectId id, double x, double y) {
            super(id);
            setBounds(new Envelope(new Coordinate(x,y)));
        }

    }

    private static class RectangleBucket extends Bucket {

        public RectangleBucket(ObjectId id, Envelope env) {
            super(id);
            setBounds(env);
        }

    }

    private static class NonSpatialBucket extends Bucket {

        public NonSpatialBucket(ObjectId id) {
            super(id);
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
