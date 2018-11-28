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

import java.util.ArrayList;
import java.util.function.Consumer;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.ValueArray;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import com.google.common.base.Optional;

/**
 * A binary representation of the values of a Feature.
 * 
 */
class ValueArrayImpl implements ValueArray {

    private final ArrayList<Object> values;

    /**
     * Constructs a new {@code RevFeature} with the provided {@link ObjectId} and set of values
     * 
     * @param id the {@link ObjectId} to use for this feature
     * @param values a list of values, {@code null} members allowed
     */
    ValueArrayImpl(ArrayList<Object> values) {
        this.values = values;
    }

    public @Override int size() {
        return values.size();
    }

    public @Override Optional<Object> get(final int index) {
        return Optional.fromNullable(ValueArray.safeCopy(values.get(index)));
    }

    public @Override Optional<Geometry> get(int index, GeometryFactory gf) {
        Geometry g = (Geometry) values.get(index);
        Geometry g2 = null;
        if (g != null) {
            g2 = gf.createGeometry(g);
        }
        return Optional.fromNullable(g2);
    }

    public @Override void forEach(final Consumer<Object> consumer) {
        values.forEach(v -> consumer.accept(ValueArray.safeCopy(v)));
    }

    public @Override String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Values[");
        builder.append("; ");
        boolean first = true;
        for (Object value : values) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }

            String valueString = String.valueOf(value);
            builder.append(valueString.substring(0, Math.min(10, valueString.length())));
        }
        builder.append(']');
        return builder.toString();
    }

}
