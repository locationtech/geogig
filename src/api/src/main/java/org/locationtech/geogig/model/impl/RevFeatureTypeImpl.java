/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.collect.ImmutableList;

import lombok.NonNull;

/**
 * A binary representation of the state of a Feature Type.
 */
class RevFeatureTypeImpl extends AbstractRevObject implements RevFeatureType {

    private final FeatureType featureType;

    /**
     * Constructs a new {@code RevFeatureType} from the given {@link ObjectId} and
     * {@link FeatureType}.
     * 
     * @param id the object id to use for this feature type
     * @param featureType the feature type to use
     */
    RevFeatureTypeImpl(@NonNull ObjectId id, @NonNull FeatureType featureType) {
        super(id);
        this.featureType = featureType;
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
     * @return the list of {@link PropertyDescriptor}s of the feature type
     */
    @Override
    public ImmutableList<PropertyDescriptor> descriptors() {
        return ImmutableList.copyOf(featureType.getDescriptors());
    }

    /**
     * @return the name of the feature type
     */
    @Override
    public Name getName() {
        return type().getName();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FeatureType[");
        builder.append(getId().toString());
        builder.append("; ");
        boolean first = true;
        for (PropertyDescriptor desc : descriptors()) {
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
