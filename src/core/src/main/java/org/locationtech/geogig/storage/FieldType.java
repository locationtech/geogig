/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.Map;
import java.util.NoSuchElementException;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Enumeration with types supported for attribute values
 * 
 * 
 */
public enum FieldType {
    NULL(0x00, -1, Void.class), //
    BOOLEAN(0x01, 1, Boolean.class), //
    BYTE(0x02, 2, Byte.class), //
    SHORT(0x03, -1, Short.class), //
    INTEGER(0x04, 6, Integer.class), //
    LONG(0x05, 8, Long.class), //
    FLOAT(0x06, 5, Float.class), //
    DOUBLE(0x07, 3, Double.class), //
    STRING(0x08, 0, String.class), //
    BOOLEAN_ARRAY(0x09, 9, boolean[].class), //
    BYTE_ARRAY(0x0A, 10, byte[].class), //
    SHORT_ARRAY(0x0B, -1, short[].class), //
    INTEGER_ARRAY(0x0C, 14, int[].class), //
    LONG_ARRAY(0x0D, 15, long[].class), //
    FLOAT_ARRAY(0x0E, 13, float[].class), //
    DOUBLE_ARRAY(0x0F, 12, double[].class), //
    STRING_ARRAY(0x10, -1, String[].class), //
    POINT(0x11, 16, Point.class), //
    LINESTRING(0x12, 18,  LineString.class), //
    POLYGON(0x13, 20, Polygon.class), //
    MULTIPOINT(0x14, 17, MultiPoint.class), //
    MULTILINESTRING(0x15, 19, MultiLineString.class), //
    MULTIPOLYGON(0x16, 21, MultiPolygon.class), //
    GEOMETRYCOLLECTION(0x17, 22, GeometryCollection.class), //
    GEOMETRY(0x18, 23, Geometry.class), //
    UUID(0x19, 26, java.util.UUID.class), //
    BIG_INTEGER(0x1A, 7, BigInteger.class), //
    BIG_DECIMAL(0x1B, 4, BigDecimal.class), //
    DATETIME(0x1C, 27, Date.class), //
    DATE(0x1D, 28, java.sql.Date.class), //
    TIME(0x1E, 29, java.sql.Time.class), //
    TIMESTAMP(0x1F, 30, java.sql.Timestamp.class), //
    UNKNOWN(-1, 25, null);

    private final byte tagValue;
    private final byte textTagValue;

    private final Class<?> binding;

    private static final Map<Class<?>, FieldType> BINDING_MAPPING = Maps.newHashMap();
    static {
        for (FieldType t : FieldType.values()) {
            BINDING_MAPPING.put(t.getBinding(), t);
        }
    }

    private FieldType(int tagValue, int textTagValue, Class<?> binding) {
        this.tagValue = (byte) tagValue;
        this.textTagValue = (byte) textTagValue;
        this.binding = binding;
    }

    public Class<?> getBinding() {
        return binding;
    }

    public byte getTag() {
        return tagValue;
    }

    public byte getTextTag() {
        return textTagValue;
    }

    public static FieldType valueOf(int i) {
        return values()[i];
    }

    public static FieldType valueFromText(int i) {
        for (FieldType f : values()) {
            if (f.getTextTag() == i) {
                return f;
            }
        }
        throw new NoSuchElementException();
    }

    public static FieldType forValue(Optional<Object> field) {
        if (field.isPresent()) {
            Object realField = field.get();
            Class<?> fieldClass = realField.getClass();
            return forBinding(fieldClass);
        } else {
            return NULL;
        }
    }

    public static FieldType forBinding(Class<?> binding) {
        if (binding == null) return NULL;
        // try a hash lookup first
        FieldType fieldType = BINDING_MAPPING.get(binding);
        if (fieldType != null) {
            return fieldType;
        }
        // not in the map, lets check one by one anyways
        // beware for this to work properly FieldTypes for super classes must be defined _after_
        // any subclass (i.e. Point before Geometry)
        for (FieldType t : values()) {
            if (t.getBinding() != null && t.getBinding().isAssignableFrom(binding)) {
                return t;
            }
        }
        return UNKNOWN;
    }

}
