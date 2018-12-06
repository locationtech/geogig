/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - pulled off from Bucket's inner class
 */
package org.locationtech.geogig.model.impl;

import static com.google.common.base.Optional.fromNullable;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;

class BucketImpl extends Bucket {
    private final int bucketTreeH1;

    private final long bucketTreeH2;

    private final long bucketTreeH3;

    private final Float32Bounds bounds;

    private final int index;

    BucketImpl(ObjectId id, int index, Float32Bounds bounds) {
        this.bucketTreeH1 = RevObjects.h1(id);
        this.bucketTreeH2 = RevObjects.h2(id);
        this.bucketTreeH3 = RevObjects.h3(id);
        this.index = index;
        this.bounds = bounds;
    }

    public @Override ObjectId getObjectId() {
        return ObjectId.create(bucketTreeH1, bucketTreeH2, bucketTreeH3);
    }

    public @Override int getIndex() {
        return index;
    }

    public @Override boolean intersects(Envelope env) {
        return bounds.intersects(env);
    }

    public @Override void expand(Envelope env) {
        bounds.expand(env);
    }

    public @Override Optional<Envelope> bounds() {
        return fromNullable(bounds.isNull() ? null : bounds.asEnvelope());
    }

}
