/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import org.geotools.feature.NameImpl;
import org.locationtech.geogig.flatbuffers.generated.SimpleFeatureType;
import org.locationtech.geogig.model.RevFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.collect.ImmutableList;

import lombok.NonNull;

final class FBSimpleFeatureType extends FBRevObject<SimpleFeatureType> implements RevFeatureType {

    public FBSimpleFeatureType(@NonNull SimpleFeatureType t) {
        super(t);
    }

    public @Override Name getName() {
        return new NameImpl(getTable().name());
    }

    public @Override TYPE getType() {
        return TYPE.FEATURETYPE;
    }

    public @Override FeatureType type() {
        return FBAdapters.toFeatureType(getTable());
    }

    public @Override ImmutableList<PropertyDescriptor> descriptors() {
        return ImmutableList.copyOf(type().getDescriptors());
    }

}
