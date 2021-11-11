/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import org.locationtech.geogig.feature.FeatureType;

import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

public @Accessors(fluent = true) class RevFeatureTypeBuilder {

    private @Setter ObjectId id;

    private @Setter FeatureType type;

    RevFeatureTypeBuilder() {
        //
    }

    /**
     * Builds a {@link RevFeatureType} for the given feature type, computing its id throuh
     * {@link HashObject}
     */
    public RevFeatureType build() {
        return build(id, type);
    }

    /**
     * Creates and returns a new {@link RevFeatureType} for the given FeatureType with the given
     * {@link ObjectId id} without verifying the SHA-1 matches the contents of the
     * {@link RevFeatureType}
     */
    public @NonNull RevFeatureType build(ObjectId id, @NonNull FeatureType featureType) {
        ObjectId oid = id == null ? HashObjectFunnels.hashFeatureType(featureType) : id;
        return RevObjectFactory.defaultInstance().createFeatureType(oid, featureType);
    }

}
