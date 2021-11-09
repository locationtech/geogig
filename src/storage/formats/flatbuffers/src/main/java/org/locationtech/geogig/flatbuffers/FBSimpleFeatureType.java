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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.Name;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.flatbuffers.generated.v1.QualifiedName;
import org.locationtech.geogig.flatbuffers.generated.v1.SimpleFeatureType;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObjects;

import lombok.NonNull;

final class FBSimpleFeatureType extends FBRevObject<SimpleFeatureType> implements RevFeatureType {

    private WeakReference<FeatureType> parsedObject = new WeakReference<>(null);

    public FBSimpleFeatureType(@NonNull SimpleFeatureType t) {
        super(t);
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }

    public @Override Name getName() {
        QualifiedName qname = getTable().name();
        return FBAdapters.toName(qname);
    }

    public @Override TYPE getType() {
        return TYPE.FEATURETYPE;
    }

    public @Override FeatureType type() {
        FeatureType type = parsedObject.get();
        if (type == null) {
            type = FBAdapters.toFeatureType(getTable());
            parsedObject = new WeakReference<>(type);
        }
        return type;
    }

    public @Override List<PropertyDescriptor> descriptors() {
        return new ArrayList<>(type().getDescriptors());
    }

}
