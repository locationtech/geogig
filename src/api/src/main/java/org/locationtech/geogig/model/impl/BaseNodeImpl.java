/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - pulled off from Node's inner class
 */
package org.locationtech.geogig.model.impl;

import static java.util.Optional.ofNullable;

import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.jts.geom.Envelope;

import lombok.NonNull;

abstract class BaseNodeImpl extends Node {

    /*
     * The name of the element
     */
    private final String name;

    /**
     * Optional ID corresponding to metadata for the element
     */
    @Nullable
    private final ObjectId metadataId;

    /**
     * Id of the object this ref points to
     */
    private final int objectId_h1;

    private final long objectId_h2, objectId_h3;

    private final ExtraData extraData;

    private final float bounds_x1, bounds_x2, bounds_y1, bounds_y2;

    public BaseNodeImpl(final @NonNull String name, final @NonNull ObjectId oid,
            final @NonNull ObjectId metadataId, @Nullable Envelope bounds,
            @Nullable Map<String, Object> extraData) {
        this.name = name;
        this.objectId_h1 = RevObjects.h1(oid);
        this.objectId_h2 = RevObjects.h2(oid);
        this.objectId_h3 = RevObjects.h3(oid);
        this.metadataId = metadataId.isNull() ? null : metadataId;
        this.extraData = ExtraData.of(extraData);

        Float32Bounds bbox = Float32Bounds.valueOf(bounds);
        bounds_x1 = bbox.xmin;
        bounds_x2 = bbox.xmax;
        bounds_y1 = bbox.ymin;
        bounds_y2 = bbox.ymax;
    }

    public @Override Optional<ObjectId> getMetadataId() {
        return Optional.ofNullable(metadataId);
    }

    /**
     * @return the name of the {@link RevObject} this node points to
     */
    public @Override String getName() {
        return name;
    }

    /**
     * @return the id of the {@link RevObject} this Node points to
     */
    public ObjectId getObjectId() {
        return ObjectId.create(objectId_h1, objectId_h2, objectId_h3);
    }

    public @Override boolean intersects(Envelope env) {
        if (isBoundsNull() || env.isNull()) {
            return false;
        }
        return boundsInternal().intersects(env);
    }

    public @Override void expand(Envelope env) {
        if (!isBoundsNull()) {
            boundsInternal().expand(env);
        }
    }

    public @Override Optional<Envelope> bounds() {
        return ofNullable(boundsInternal().isNull() ? null : boundsInternal().asEnvelope());
    }

    private Float32Bounds boundsInternal() {
        return Float32Bounds.valueOf(bounds_x1, bounds_x2, bounds_y1, bounds_y2);
    }

    private final boolean isBoundsNull() {
        return bounds_x1 > bounds_x2;
    }

    public @Override Map<String, Object> getExtraData() {
        return extraData.asMap();
    }

    public @Override @Nullable Object getExtraData(String key) {
        return extraData.get(key);
    }
}
