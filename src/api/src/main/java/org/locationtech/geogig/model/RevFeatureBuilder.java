/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Builder for {@link RevFeature} instances.
 * 
 * <p>
 * Use the {@link #addValue(Object)} method as many times as needed to provide the sequence of
 * property values, then call {@link #build()} to get the final {@link RevFeature}.
 * 
 * @see RevFeature
 * @see Feature
 */
public final @Accessors(fluent = true) class RevFeatureBuilder {

    private @Setter @Nullable ObjectId id;

    private ArrayList</* @Nullable */Object> values = new ArrayList<>(5);

    public RevFeatureBuilder() {
        //
    }

    public RevFeature build() {
        if (id == null) {
            id = HashObjectFunnels.hashFeature(values);
        }
        return RevObjectFactory.defaultInstance().createFeature(id, values);
    }

    public RevFeatureBuilder reset() {
        this.values.clear();
        return this;
    }

    public RevFeatureBuilder addProperty(@NonNull Property featureProp) {
        // This is where we might handle complex properties if ever supported
        addValue(featureProp.getValue());
        return this;
    }

    /**
     * Adds the provided value to the tail of the sequence of attribute values that compose the
     * {@link RevFeature} being built.
     */
    public RevFeatureBuilder addValue(@Nullable Object value) {
        return addValueNoCopy(value);
    }

    /**
     * Use with caution, this object takes ownership of {@code value} without making a safe copy of
     * it.
     */
    public RevFeatureBuilder addValueNoCopy(@Nullable Object value) {
        if (value instanceof Geometry) {
            value = normalizeIfNeeded((Geometry) value);
        }
        this.values.add(value);
        return this;
    }

    Geometry normalizeIfNeeded(Geometry value) {
        if (value instanceof Polygon) {
            value.normalize();
        } else if (value instanceof MultiPolygon
                || GeometryCollection.class.equals(value.getClass())) {// ignore
                                                                       // multipoint/linestring
            normalize((GeometryCollection) value);
        }

        return value;
    }

    private void normalize(GeometryCollection col) {
        for (int i = 0; i < col.getNumGeometries(); i++) {
            Geometry geometryN = col.getGeometryN(i);
            normalizeIfNeeded(geometryN);
        }

    }

    public RevFeatureBuilder addAll(@NonNull List<Object> values) {
        for (Object v : values) {
            addValue(v);
        }
        return this;
    }

    public RevFeatureBuilder addAll(@NonNull Object... values) {
        for (Object v : values) {
            addValue(v);
        }
        return this;
    }

    /**
     * Constructs a new {@link RevFeature} from the provided {@link Feature}.
     * 
     * @param feature the feature to build from
     * @return the newly constructed RevFeature
     */
    public RevFeature build(@NonNull Feature feature) {
        if (feature instanceof SimpleFeature) {
            // Just
            SimpleFeature sf = (SimpleFeature) feature;
            int attributeCount = sf.getAttributeCount();
            for (int i = 0; i < attributeCount; i++) {
                addValue(sf.getAttribute(i));
            }
        } else {
            Collection<Property> props = feature.getProperties();
            props.forEach(this::addProperty);
        }
        return build();
    }
}
