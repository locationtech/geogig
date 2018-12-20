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

import static com.google.common.base.Preconditions.checkArgument;
import static org.geotools.referencing.crs.DefaultGeographicCRS.WGS84;

import javax.annotation.Nullable;

import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

public @Accessors(fluent = true) class RevFeatureTypeBuilder {

    private @Setter @Nullable ObjectId id;

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
    public RevFeatureType build(@Nullable ObjectId id, @NonNull FeatureType ftype) {
        FeatureType featureType = adapt(ftype);
        ObjectId oid = id == null ? HashObjectFunnels.hashFeatureType(featureType) : id;
        return RevObjectFactory.defaultInstance().createFeatureType(oid, featureType);
    }

    // GeoTools treats DefaultGeographic.WGS84 as a special case when calling the
    // CRS.toSRS() method, and that causes the parsed RevFeatureType to hash differently.
    // To compensate that, we replace any instance of it with a CRS built using the
    // EPSG:4326 code, which works consistently when storing it and later recovering it from
    // the database.
    private static FeatureType adapt(FeatureType featureType) {
        CoordinateReferenceSystem defaultCrs = featureType.getCoordinateReferenceSystem();
        final boolean compareMetadata = false;
        if (null != defaultCrs && WGS84.equals(
                (org.geotools.referencing.AbstractIdentifiedObject) defaultCrs, compareMetadata)) {
            checkArgument(featureType instanceof SimpleFeatureType);
            try {
                final boolean longitudeFirst = true;
                CoordinateReferenceSystem epsg4326 = CRS.decode("EPSG:4326", longitudeFirst);
                String[] includeAllAttributes = null;
                featureType = DataUtilities.createSubType((SimpleFeatureType) featureType,
                        includeAllAttributes, epsg4326);
            } catch (FactoryException | SchemaException e) {
                throw new RuntimeException(e);
            }
        }
        return featureType;
    }
}
