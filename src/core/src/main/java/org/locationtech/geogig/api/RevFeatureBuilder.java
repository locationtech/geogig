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

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

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

    private List</* @Nullable */Object> values = new ArrayList<>();

    private RevFeatureBuilder() {
        //
    }

    public static RevFeatureBuilder builder() {
        return new RevFeatureBuilder();
    }

    public RevFeature build() {
        ObjectId id = HashObject.hashFeatureValues(values);
        return new RevFeatureImpl(id,
                ImmutableList.copyOf(Lists.transform(values, (v) -> Optional.fromNullable(v))));
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

    public RevFeatureBuilder addValue(@Nullable Object value) {
        // TODO: normalize polygons
        Preconditions.checkArgument(!(value instanceof Optional));// remove once everything is
                                                                  // ported
        this.values.add(value);
        return this;
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
