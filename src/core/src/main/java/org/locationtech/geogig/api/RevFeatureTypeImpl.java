/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.geotools.referencing.crs.DefaultGeographicCRS.WGS84;

import org.geotools.data.DataUtilities;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.api.plumbing.HashObject;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * A binary representation of the state of a Feature Type.
 */
public class RevFeatureTypeImpl extends AbstractRevObject implements RevFeatureType {

    private final FeatureType featureType;

    private ImmutableList<PropertyDescriptor> sortedDescriptors;

    public static RevFeatureTypeImpl build(FeatureType featureType) {
        RevFeatureTypeImpl unnamed = new RevFeatureTypeImpl(featureType);
        ObjectId id = new HashObject().setObject(unnamed).call();
        return new RevFeatureTypeImpl(id, featureType);
    }

    /**
     * Constructs a new {@code RevFeatureType} from the given {@link FeatureType}.
     * 
     * @param featureType the feature type to use
     */
    private RevFeatureTypeImpl(FeatureType featureType) {
        this(ObjectId.NULL, featureType);
    }

    /**
     * Constructs a new {@code RevFeatureType} from the given {@link ObjectId} and
     * {@link FeatureType}.
     * 
     * @param id the object id to use for this feature type
     * @param featureType the feature type to use
     */
    public RevFeatureTypeImpl(ObjectId id, FeatureType featureType) {
        super(id);
        checkNotNull(featureType);
        CoordinateReferenceSystem defaultCrs = featureType.getCoordinateReferenceSystem();
        if (WGS84.equals(defaultCrs)) {
            // GeoTools treats DefaultGeographic.WGS84 as a special case when calling the
            // CRS.toSRS() method, and that causes the parsed RevFeatureType to hash differently.
            // To compensate that, we replace any instance of it with a CRS built using the
            // EPSG:4326 code, which works consistently when storing it and later recovering it from
            // the database.
            checkArgument(featureType instanceof SimpleFeatureType);
            try {
                final boolean longitudeFirst = true;
                CoordinateReferenceSystem epsg4326 = CRS.decode("EPSG:4326", longitudeFirst);
                String[] includeAllAttributes = null;
                featureType = DataUtilities.createSubType((SimpleFeatureType) featureType,
                        includeAllAttributes, epsg4326);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        this.featureType = featureType;
        sortedDescriptors = ImmutableList.copyOf(featureType.getDescriptors());
    }

    @Override
    public TYPE getType() {
        return TYPE.FEATURETYPE;
    }

    @Override
    public FeatureType type() {
        return featureType;
    }

    /**
     * @return the sorted {@link PropertyDescriptor}s of the feature type
     */
    @Override
    public ImmutableList<PropertyDescriptor> sortedDescriptors() {
        return sortedDescriptors;
    }

    /**
     * @return the name of the feature type
     */
    @Override
    public Name getName() {
        Name name = type().getName();
        return name;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FeatureType[");
        builder.append(getId().toString());
        builder.append("; ");
        boolean first = true;
        for (PropertyDescriptor desc : sortedDescriptors()) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append(desc.getName().getLocalPart());
            builder.append(": ");
            builder.append(desc.getType().getBinding().getSimpleName());
        }
        builder.append(']');
        return builder.toString();
    }
}
