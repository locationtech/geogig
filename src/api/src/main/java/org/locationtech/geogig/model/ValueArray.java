/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - Interface pulled off from RevFeature and added as RevFeature superinterface
 */
package org.locationtech.geogig.model;

import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import com.google.common.base.Optional;

/**
 * A {@code ValueArray} is an immutable data structure that contains a sequence of attribute value
 * instances of a GIS feature.
 * 
 * @since 1.4
 */
public interface ValueArray {
    /**
     * @return the number of attribute values in the feature
     */
    public int size();

    /**
     * @return the feature attribute value at the provided {@code index}, or
     *         {@link Optional#absent() absent} if the object at that index is {@code null} (not to
     *         be misinterpreted as absent if the index is out of bounds, in which case an exception
     *         is thrown)
     */
    public Optional<Object> get(final int index);

    public Optional<Geometry> get(final int index, final GeometryFactory gf);

    /**
     * Performs the given action for each attribute in the feature, in it's natural order, until all
     * elements have been processed or the action throws an exception.
     * 
     * @param consumer the action to perform on each attribute value
     */
    public void forEach(final Consumer<Object> consumer);

    public static @Nullable Object safeCopy(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        FieldType fieldType = FieldType.forValue(value);
        if (FieldType.UNKNOWN.equals(fieldType)) {
            throw new IllegalArgumentException(String.format(
                    "Objects of class %s are not supported as RevFeature attributes: ",
                    value.getClass().getName()));
        }
        value = fieldType.safeCopy(value);
        return value;
    }

}