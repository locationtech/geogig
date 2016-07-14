/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.plumbing.HashObject;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

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
public final class RevFeatureBuilder {

    private ArrayList</* @Nullable */Object> values = new ArrayList<>(5);

    private RevFeatureBuilder() {
        //
    }

    public static RevFeatureBuilder builder() {
        return new RevFeatureBuilder();
    }

    public RevFeature build() {
        ObjectId id = HashObject.hashFeature(values);
        return new RevFeatureImpl(id, new ArrayList<>(values));
    }

    public RevFeatureBuilder reset() {
        this.values.clear();
        return this;
    }

    public RevFeatureBuilder addProperty(Property featureProp) {
        checkNotNull(featureProp);
        // This is where we might handle complex properties if ever supported
        addValue(featureProp.getValue());
        return this;
    }

    /**
     * Adds the provided value to the tail of the sequence of attribute values that compose the
     * {@link RevFeature} being built.
     * <p>
     * In order to preserve the {@link RevFeature}'s immutability, a safe copy of the value will be
     * assigned if it's a mutable type.
     * 
     * @see FieldType#safeCopy(Object)
     */
    public RevFeatureBuilder addValue(@Nullable Object value) {
        value = safeCopy(value);
        this.values.add(value);
        return this;
    }

    private Object safeCopy(@Nullable Object value) {
        FieldType fieldType = FieldType.forValue(value);
        if (FieldType.UNKNOWN.equals(fieldType)) {
            throw new IllegalArgumentException(String.format(
                    "Objects of class %s are not supported as RevFeature attributes: ",
                    value.getClass().getName()));
        }
        value = fieldType.safeCopy(value);
        return value;
    }

    public RevFeatureBuilder addAll(List<Object> values) {
        checkNotNull(values);
        for (Object v : values) {
            addValue(v);
        }
        return this;
    }

    public RevFeatureBuilder addAll(Object... values) {
        checkNotNull(values);
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
    public static RevFeature build(Feature feature) {
        if (feature == null) {
            throw new IllegalStateException("No feature set");
        }

        RevFeatureBuilder builder = RevFeatureBuilder.builder();

        if (feature instanceof SimpleFeature) {
            // Just
            SimpleFeature sf = (SimpleFeature) feature;
            int attributeCount = sf.getAttributeCount();
            for (int i = 0; i < attributeCount; i++) {
                builder.addValue(sf.getAttribute(i));
            }
        } else {
            Collection<Property> props = feature.getProperties();
            props.forEach((p) -> builder.addProperty(p));
        }
        return builder.build();
    }
}
