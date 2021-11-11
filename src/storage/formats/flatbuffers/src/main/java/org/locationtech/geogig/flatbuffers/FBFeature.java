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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.locationtech.geogig.flatbuffers.generated.v1.Feature;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import lombok.NonNull;

final class FBFeature extends FBRevObject<Feature> implements RevFeature {

    public FBFeature(@NonNull Feature f) {
        super(f);
    }

    public @Override TYPE getType() {
        return TYPE.FEATURE;
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }

    public @Override List<Optional<Object>> getValues() {
        List<Optional<Object>> values = new ArrayList<>();
        forEach(o -> values.add(Optional.ofNullable(o)));
        return values;
    }

    public @Override int size() {
        return getTable().valuesLength();
    }

    public @Override Optional<Object> get(int index) {
        return Optional.ofNullable(getInternal(index, null));
    }

    public @Override Optional<Geometry> get(int index, GeometryFactory gf) {
        return Optional.ofNullable((Geometry) getInternal(index, gf));
    }

    public @Override void forEach(Consumer<Object> consumer) {
        final int length = size();
        for (int i = 0; i < length; i++) {
            consumer.accept(getInternal(i, null));
        }
    }

    private Object getInternal(int index, GeometryFactory gf) {
        return ValueSerializer.decodeValue(getTable().values(index), gf);
    }
}
