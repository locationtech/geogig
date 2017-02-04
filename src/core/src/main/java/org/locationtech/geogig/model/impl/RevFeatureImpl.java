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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * A binary representation of the values of a Feature.
 * 
 */
class RevFeatureImpl extends AbstractRevObject implements RevFeature {

    private final ArrayList<Object> values;

    /**
     * Constructs a new {@code RevFeature} with the provided {@link ObjectId} and set of values
     * 
     * @param id the {@link ObjectId} to use for this feature
     * @param values a list of values, {@code null} members allowed
     */
    RevFeatureImpl(ObjectId id, ArrayList<Object> values) {
        super(id);
        this.values = values;
    }

    @Override
    public ImmutableList<Optional<Object>> getValues() {
        return ImmutableList
                .copyOf(Lists.transform(values, (v) -> Optional.fromNullable(safeCopy(v))));
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public Optional<Object> get(final int index) {
        return Optional.fromNullable(safeCopy(values.get(index)));
    }

    @Override
    public Optional<Geometry> get(int index, GeometryFactory gf) {
        Geometry g = (Geometry) values.get(index);
        Geometry g2 = null;
        if (g != null) {
            g2 = gf.createGeometry(g);
        }
        return Optional.fromNullable(g2);
    }

    @Override
    public void forEach(final Consumer<Object> consumer) {
        values.forEach((v) -> consumer.accept(safeCopy(v)));
    }

    private @Nullable Object safeCopy(@Nullable Object value) {
        return FieldType.forValue(value).safeCopy(value);
    }

    @Override
    public TYPE getType() {
        return TYPE.FEATURE;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Feature[");
        builder.append(getId().toString());
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
