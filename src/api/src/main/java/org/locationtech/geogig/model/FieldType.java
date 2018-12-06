/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

/**
 * Enumeration of value types supported as {@link RevFeature} attribute values.
 * <p>
 * The constants in this enum define the object types attribute values for GeoGig {@link RevFeature}
 * instances may assume.
 * <p>
 * The {@link FieldType} bound to a given attribute instance can be obtained through
 * {@link #forValue(Object)}, and the one bound to a given attribute class through
 * {@link #forBinding(Class)}.
 * <p>
 * {@code null} values will be bound to the {@link #NULL} constant, as well as calls to
 * {@link #forBinding(Class)} with {@link Void Void.class} as argument; {@link Integer} values to
 * the {@link #INTEGER} constant, and so on.
 * <p>
 * When the attribute value or class is not bound to a constant in this enum, the {@link #UNKNOWN}
 * constant will be returned by both {@link #forValue(Object)} and {@link #forBinding(Class)}, which
 * shall be treated by the calling code as an exceptional condition and act accordingly.
 * <p>
 * Since {@link RevFeature} is mandated to be immutable, and some of the supported value types are
 * not immutable, the {@link #safeCopy(Object)} method may be used by an implementation in order to
 * return a copy of the internal value and hence preserve the internal state of the
 * {@link RevFeature} instance.
 * <p>
 * The following is the list of supported value types:
 * <ul>
 * <li>{@link #NULL} a null value;
 * <li>{@link #BOOLEAN} a boolean value, bound to the {@link Boolean} Java type;
 * <li>{@link #BYTE} a single byte value, bound to the {@link Byte} Java type;
 * <li>{@link #SHORT} a signed 16-bit short, bound to the {@link Short} Java type;
 * <li>{@link #INTEGER} a signed 32-bit integer, bound to the {@link Integer} Java type;
 * <li>{@link #LONG} a signed 64-bit integer, bound to the {@link Long} Java type;
 * <li>{@link #FLOAT} a signed 32-bit floating point number, bound to the {@link Float} Java type;
 * <li>{@link #DOUBLE} a signed 64-bit floating point number, bound to the {@link Double} Java type;
 * <li>{@link #STRING} a 16-bit Unicode character sequence, bound to the {@link String} Java type;
 * <li>{@link #BOOLEAN_ARRAY} an array of {@link #BOOLEAN} values;
 * <li>{@link #BYTE_ARRAY} an array of {@link #BYTE} values;
 * <li>{@link #SHORT_ARRAY} an array of {@link #SHORT} values;
 * <li>{@link #INTEGER_ARRAY} an array of {@link #INTEGER} values;
 * <li>{@link #LONG_ARRAY} an array of {@link #LONG} values;
 * <li>{@link #FLOAT_ARRAY} an array of {@link #FLOAT} values;
 * <li>{@link #DOUBLE_ARRAY} an array of {@link #DOUBLE} values;
 * <li>{@link #STRING_ARRAY} an array of {@link #STRING} values;
 * <li>{@link #POINT} a point geometry, bound to the JTS {@link Point} class;
 * <li>{@link #LINESTRING} a linestring geometry, bound to the JTS {@link LineString} class;
 * <li>{@link #POLYGON} a polygon geometry, bound to the JTS {@link Polygon} class;
 * <li>{@link #MULTIPOINT} a sequence of point geometries, bound to the JTS {@link MultiPoint}
 * class;
 * <li>{@link #MULTILINESTRING} a sequence of linestring geometries, bound to the JTS
 * {@link MultiLineString} class;
 * <li>{@link #MULTIPOLYGON} a sequence of polygon geometries, bound to the JTS {@link MultiPolygon}
 * class;
 * <li>{@link #GEOMETRYCOLLECTION} a collection of other geometry objects, bound to the JTS
 * {@link GeometryCollection} class;
 * <li>{@link #GEOMETRY} a geometry object of an unspecified type, should not be used since concrete
 * instances will always be of one of the concrete geometry types
 * <li>{@link #UUID} a 128-bit
 * <a href="https://en.wikipedia.org/wiki/Universally_unique_identifier">Universally Unique
 * Identifier</a>, bound to the {@link java.util.UUID} Java class;
 * <li>{@link #BIG_INTEGER} an arbitrary-precision signed integer value, bound to the
 * {@link BigInteger} Java class;
 * <li>{@link #BIG_DECIMAL} an arbitrary-precision signed decimal number, bound to the
 * {@link BigDecimal} Java class;
 * <li>{@link #DATETIME} a (signed) Unix timestamp, milliseconds ellapsed since January 1, 1970
 * 00:00:00.000 GMT, bound to the {@link java.util.Date} class;
 * <li>{@link #DATE} a (signed) Unix timestamp, milliseconds ellapsed since January 1, 1970
 * 00:00:00.000 GMT, with milliseconds rounded to date precision, bound to the {@link java.sql.Date}
 * class
 * <li>{@link #TIME} a Unix timestamp with milliseconds corresponding to year, month and date set to
 * zero, bound to {@link java.sql.Time} class
 * <li>{@link #TIMESTAMP} a Unix timestamp plus nanoseconds precision, bound to the
 * {@link java.sql.Timestamp} class
 * <li>{@link #MAP}(0x20, java.util.Map.class, v -> new HashMap<>((Map) v)), //
 * <li>{@link #CHAR} a single 16-bit Unicode characted, bount to the {@link Character} Java type;
 * <li>{@link #CHAR_ARRAY} an array of {@link #CHAR}, essentially equivalent to {@link #STRING}, but
 * present for completeness as the client application may define a field to be of a fixed size, for
 * example.
 * <li>{@link #UNKNOWN} an error condition enum value for when a value instance can't be mapped to
 * any supported type. See {@link #forBinding(Class)} and {@link #forValue(Object)} for more
 * information.
 * </ul>
 * 
 * @since 1.0
 */
public enum FieldType {
    NULL(0x00, Void.class), //
    BOOLEAN(0x01, Boolean.class), //
    BYTE(0x02, Byte.class), //
    SHORT(0x03, Short.class), //
    INTEGER(0x04, Integer.class), //
    LONG(0x05, Long.class), //
    FLOAT(0x06, Float.class), //
    DOUBLE(0x07, Double.class), //
    STRING(0x08, String.class), //
    BOOLEAN_ARRAY(0x09, boolean[].class, v -> ((boolean[]) v).clone()), //
    BYTE_ARRAY(0x0A, byte[].class, v -> ((byte[]) v).clone()), //
    SHORT_ARRAY(0x0B, short[].class, v -> ((short[]) v).clone()), //
    INTEGER_ARRAY(0x0C, int[].class, v -> ((int[]) v).clone()), //
    LONG_ARRAY(0x0D, long[].class, v -> ((long[]) v).clone()), //
    FLOAT_ARRAY(0x0E, float[].class, v -> ((float[]) v).clone()), //
    DOUBLE_ARRAY(0x0F, double[].class, v -> ((double[]) v).clone()), //
    STRING_ARRAY(0x10, String[].class, v -> ((String[]) v).clone()), //
    POINT(0x11, Point.class, v -> GeometryCloner.clone((Geometry) v)), //
    LINESTRING(0x12, LineString.class, v -> GeometryCloner.clone((Geometry) v)), //
    POLYGON(0x13, Polygon.class, v -> GeometryCloner.clone((Geometry) v)), //
    MULTIPOINT(0x14, MultiPoint.class, v -> GeometryCloner.clone((Geometry) v)), //
    MULTILINESTRING(0x15, MultiLineString.class, v -> GeometryCloner.clone((Geometry) v)), //
    MULTIPOLYGON(0x16, MultiPolygon.class, v -> GeometryCloner.clone((Geometry) v)), //
    GEOMETRYCOLLECTION(0x17, GeometryCollection.class, v -> GeometryCloner.clone((Geometry) v)), //
    /**
     * a geometry object of an unspecified type
     */
    GEOMETRY(0x18, Geometry.class, v -> GeometryCloner.clone((Geometry) v)), //
    UUID(0x19, java.util.UUID.class), //
    BIG_INTEGER(0x1A, BigInteger.class), //
    BIG_DECIMAL(0x1B, BigDecimal.class), //
    DATETIME(0x1C, java.util.Date.class), //
    DATE(0x1D, java.sql.Date.class), //
    TIME(0x1E, java.sql.Time.class), //
    TIMESTAMP(0x1F, java.sql.Timestamp.class), //
    MAP(0x20, java.util.Map.class, v -> recursiveSafeCopy((Map<?, ?>) v)), //
    CHAR(0x21, Character.class), //
    CHAR_ARRAY(0x22, char[].class), //
    ENVELOPE_2D(0x23, Envelope.class, v -> new Envelope((Envelope) v)), //
    UNKNOWN(-1, null);

    private final byte tagValue;

    private final Class<?> binding;

    /**
     * A function that creates a "safe copy" for an attribute value of the type denoted by this enum
     * member instance.
     */
    private final Function<Object, Object> safeCopyBuilder;

    private static final Map<Class<?>, FieldType> BINDING_MAPPING = Maps.newHashMap();
    static {
        for (FieldType t : FieldType.values()) {
            BINDING_MAPPING.put(t.getBinding(), t);
        }
    }

    private FieldType(int tagValue, Class<?> binding) {
        this(tagValue, binding, val -> val);
    }

    private static Map<Object, Object> recursiveSafeCopy(Map<?, ?> m) {
        Map<Object, Object> copy = new HashMap<>(m.size());
        m.forEach((k, v) -> copy.put(k, FieldType.forValue(v).safeCopy(v)));
        return copy;
    }

    private FieldType(int tagValue, Class<?> binding,
            Function<Object, Object> immutableCopyBuilder) {
        this.tagValue = (byte) tagValue;
        this.binding = binding;
        this.safeCopyBuilder = immutableCopyBuilder;
    }

    public Class<?> getBinding() {
        return binding;
    }

    /**
     * A unique identifier for this enum member, in order to not rely on {@link #ordinal()}, that
     * can be used, for example, by serializers to identify the kind of value that's to be encoded.
     */
    public byte getTag() {
        return tagValue;
    }

    // cached array to avoid a clone performed by Enum on each call to valueOf(int)
    private static final FieldType[] VALUES_CACHE = FieldType.values();

    /**
     * Obtain a {@code FieldType} constant by it's {@link #getTag() tag}
     */
    public static FieldType valueOf(final int tagValue) {
        if (tagValue == -1) {
            return UNKNOWN;
        }
        // NOTE: we're using the tagValue as the ordinal index because they match, the moment they
        // don't we need to reimplement this method accordingly.
        return VALUES_CACHE[tagValue];
    }

    /**
     * @return the {@code FieldType} corresponding to the {@link Optional}'s value.
     * @see #forValue(Object)
     */
    public static FieldType forValue(Optional<Object> field) {
        return forValue(field.orNull());
    }

    /**
     * Resolves the {@code FieldType} corresponding to the {@code value}'s class
     * 
     * @see #forBinding(Class)
     */
    public static FieldType forValue(@Nullable Object value) {
        if (value == null) {
            return NULL;
        }
        Class<?> fieldClass = value.getClass();
        return forBinding(fieldClass);
    }

    /**
     * @return the {@code FieldType} associated to the provided binding, or
     *         {@link FieldType#UNKNOWN} if no {@code FieldType} relates to the argument attribute
     *         type.
     */
    public static FieldType forBinding(@Nullable Class<?> binding) {
        if (binding == null || Void.class.equals(binding)) {
            return NULL;
        }
        // try a hash lookup first
        FieldType fieldType = BINDING_MAPPING.get(binding);
        if (fieldType != null) {
            return fieldType;
        }
        // not in the map, lets check one by one anyways
        // beware for this to work properly FieldTypes for super classes must be defined _after_
        // any subclass (i.e. Point before Geometry)
        for (FieldType t : values()) {
            Class<?> fieldTypeBinding = t.getBinding();
            if (null != fieldTypeBinding && fieldTypeBinding.isAssignableFrom(binding)) {
                return t;
            }
        }
        return UNKNOWN;
    }

    /**
     * Returns a safe copy (e.g. a clone or immutable copy) of {@code value}, if it is of a mutable
     * object type (e.g. {@code java.util.Map, org.locationtech.jts.geom.Geometry, etc}), or the
     * same object if it's already immutable (e.g. {@code java.lang.Integer,etc}).
     * 
     * @param value an object of this {@code FiledType}'s {@link FieldType#getBinding() binding}
     *        type.
     */
    @Nullable
    public Object safeCopy(@Nullable Object value) {
        return safeCopyBuilder.apply(value);
    }

}
