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

import java.util.Optional;
import java.util.function.Consumer;

import org.locationtech.geogig.flatbuffers.generated.v1.Feature;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.ValueArray;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import lombok.NonNull;

final class FBValueArray implements ValueArray {

    private Feature values;

    public FBValueArray(@NonNull Feature f) {
        this.values = f;
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }

    public @Override int size() {
        return values.valuesLength();
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
        return ValueSerializer.decodeValue(values.values(index), gf);
    }
}
