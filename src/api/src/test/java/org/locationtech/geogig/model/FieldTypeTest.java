/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;

import com.google.common.base.Optional;

public class FieldTypeTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testFieldTypes() {
        assertEquals(Void.class, FieldType.NULL.getBinding());
        assertEquals(Boolean.class, FieldType.BOOLEAN.getBinding());
        assertEquals(Byte.class, FieldType.BYTE.getBinding());
        assertEquals(Short.class, FieldType.SHORT.getBinding());
        assertEquals(Integer.class, FieldType.INTEGER.getBinding());
        assertEquals(Long.class, FieldType.LONG.getBinding());
        assertEquals(Float.class, FieldType.FLOAT.getBinding());
        assertEquals(Double.class, FieldType.DOUBLE.getBinding());
        assertEquals(String.class, FieldType.STRING.getBinding());
        assertEquals(boolean[].class, FieldType.BOOLEAN_ARRAY.getBinding());
        assertEquals(byte[].class, FieldType.BYTE_ARRAY.getBinding());
        assertEquals(short[].class, FieldType.SHORT_ARRAY.getBinding());
        assertEquals(int[].class, FieldType.INTEGER_ARRAY.getBinding());
        assertEquals(long[].class, FieldType.LONG_ARRAY.getBinding());
        assertEquals(float[].class, FieldType.FLOAT_ARRAY.getBinding());
        assertEquals(double[].class, FieldType.DOUBLE_ARRAY.getBinding());
        assertEquals(String[].class, FieldType.STRING_ARRAY.getBinding());
        assertEquals(Point.class, FieldType.POINT.getBinding());
        assertEquals(LineString.class, FieldType.LINESTRING.getBinding());
        assertEquals(Polygon.class, FieldType.POLYGON.getBinding());
        assertEquals(MultiPoint.class, FieldType.MULTIPOINT.getBinding());
        assertEquals(MultiLineString.class, FieldType.MULTILINESTRING.getBinding());
        assertEquals(MultiPolygon.class, FieldType.MULTIPOLYGON.getBinding());
        assertEquals(GeometryCollection.class, FieldType.GEOMETRYCOLLECTION.getBinding());
        assertEquals(Geometry.class, FieldType.GEOMETRY.getBinding());
        assertEquals(UUID.class, FieldType.UUID.getBinding());
        assertEquals(BigInteger.class, FieldType.BIG_INTEGER.getBinding());
        assertEquals(BigDecimal.class, FieldType.BIG_DECIMAL.getBinding());
        assertEquals(java.sql.Date.class, FieldType.DATE.getBinding());
        assertEquals(java.util.Date.class, FieldType.DATETIME.getBinding());
        assertEquals(java.sql.Time.class, FieldType.TIME.getBinding());
        assertEquals(java.sql.Timestamp.class, FieldType.TIMESTAMP.getBinding());
        assertEquals(Map.class, FieldType.MAP.getBinding());
        assertEquals(Character.class, FieldType.CHAR.getBinding());
        assertEquals(char[].class, FieldType.CHAR_ARRAY.getBinding());
        assertEquals(Envelope.class, FieldType.ENVELOPE_2D.getBinding());
        assertEquals(null, FieldType.UNKNOWN.getBinding());
    }

    @Test
    public void testValueOf() {
        assertEquals(FieldType.NULL, FieldType.valueOf(FieldType.NULL.getTag()));
        assertEquals(FieldType.BOOLEAN, FieldType.valueOf(FieldType.BOOLEAN.getTag()));
        assertEquals(FieldType.BYTE, FieldType.valueOf(FieldType.BYTE.getTag()));
        assertEquals(FieldType.SHORT, FieldType.valueOf(FieldType.SHORT.getTag()));
        assertEquals(FieldType.INTEGER, FieldType.valueOf(FieldType.INTEGER.getTag()));
        assertEquals(FieldType.LONG, FieldType.valueOf(FieldType.LONG.getTag()));
        assertEquals(FieldType.FLOAT, FieldType.valueOf(FieldType.FLOAT.getTag()));
        assertEquals(FieldType.DOUBLE, FieldType.valueOf(FieldType.DOUBLE.getTag()));
        assertEquals(FieldType.STRING, FieldType.valueOf(FieldType.STRING.getTag()));
        assertEquals(FieldType.BOOLEAN_ARRAY, FieldType.valueOf(FieldType.BOOLEAN_ARRAY.getTag()));
        assertEquals(FieldType.BYTE_ARRAY, FieldType.valueOf(FieldType.BYTE_ARRAY.getTag()));
        assertEquals(FieldType.SHORT_ARRAY, FieldType.valueOf(FieldType.SHORT_ARRAY.getTag()));
        assertEquals(FieldType.INTEGER_ARRAY, FieldType.valueOf(FieldType.INTEGER_ARRAY.getTag()));
        assertEquals(FieldType.LONG_ARRAY, FieldType.valueOf(FieldType.LONG_ARRAY.getTag()));
        assertEquals(FieldType.FLOAT_ARRAY, FieldType.valueOf(FieldType.FLOAT_ARRAY.getTag()));
        assertEquals(FieldType.DOUBLE_ARRAY, FieldType.valueOf(FieldType.DOUBLE_ARRAY.getTag()));
        assertEquals(FieldType.STRING_ARRAY, FieldType.valueOf(FieldType.STRING_ARRAY.getTag()));
        assertEquals(FieldType.POINT, FieldType.valueOf(FieldType.POINT.getTag()));
        assertEquals(FieldType.LINESTRING, FieldType.valueOf(FieldType.LINESTRING.getTag()));
        assertEquals(FieldType.POLYGON, FieldType.valueOf(FieldType.POLYGON.getTag()));
        assertEquals(FieldType.MULTIPOINT, FieldType.valueOf(FieldType.MULTIPOINT.getTag()));
        assertEquals(FieldType.MULTILINESTRING,
                FieldType.valueOf(FieldType.MULTILINESTRING.getTag()));
        assertEquals(FieldType.MULTIPOLYGON, FieldType.valueOf(FieldType.MULTIPOLYGON.getTag()));
        assertEquals(FieldType.GEOMETRYCOLLECTION,
                FieldType.valueOf(FieldType.GEOMETRYCOLLECTION.getTag()));
        assertEquals(FieldType.GEOMETRY, FieldType.valueOf(FieldType.GEOMETRY.getTag()));
        assertEquals(FieldType.UUID, FieldType.valueOf(FieldType.UUID.getTag()));
        assertEquals(FieldType.BIG_INTEGER, FieldType.valueOf(FieldType.BIG_INTEGER.getTag()));
        assertEquals(FieldType.BIG_DECIMAL, FieldType.valueOf(FieldType.BIG_DECIMAL.getTag()));
        assertEquals(FieldType.DATE, FieldType.valueOf(FieldType.DATE.getTag()));
        assertEquals(FieldType.DATETIME, FieldType.valueOf(FieldType.DATETIME.getTag()));
        assertEquals(FieldType.TIME, FieldType.valueOf(FieldType.TIME.getTag()));
        assertEquals(FieldType.TIMESTAMP, FieldType.valueOf(FieldType.TIMESTAMP.getTag()));
        assertEquals(FieldType.MAP, FieldType.valueOf(FieldType.MAP.getTag()));
        assertEquals(FieldType.CHAR, FieldType.valueOf(FieldType.CHAR.getTag()));
        assertEquals(FieldType.CHAR_ARRAY, FieldType.valueOf(FieldType.CHAR_ARRAY.getTag()));
        assertEquals(FieldType.ENVELOPE_2D, FieldType.valueOf(FieldType.ENVELOPE_2D.getTag()));
        assertEquals(FieldType.UNKNOWN, FieldType.valueOf(-1));

        // If this fails it means a new type was added and all of these unit tests need to be
        // updated with the new type.
        exception.expect(ArrayIndexOutOfBoundsException.class);
        FieldType.valueOf(0x25);
    }

    @Test
    public void testForValue() throws Exception {
        WKTReader reader = new WKTReader();
        assertEquals(FieldType.NULL, FieldType.forValue(Optional.absent()));
        assertEquals(FieldType.BOOLEAN, FieldType.forValue(Optional.of(new Boolean(false))));
        assertEquals(FieldType.BYTE, FieldType.forValue(Optional.of((byte) 0x0)));
        assertEquals(FieldType.SHORT, FieldType.forValue(Optional.of((short) 0)));
        assertEquals(FieldType.INTEGER, FieldType.forValue(Optional.of(0)));
        assertEquals(FieldType.LONG, FieldType.forValue(Optional.of(0L)));
        assertEquals(FieldType.FLOAT, FieldType.forValue(Optional.of(0.f)));
        assertEquals(FieldType.DOUBLE, FieldType.forValue(Optional.of(new Double(0))));
        assertEquals(FieldType.STRING, FieldType.forValue(Optional.of("")));
        assertEquals(FieldType.BOOLEAN_ARRAY, FieldType.forValue(Optional.of(new boolean[2])));
        assertEquals(FieldType.BYTE_ARRAY, FieldType.forValue(Optional.of(new byte[2])));
        assertEquals(FieldType.SHORT_ARRAY, FieldType.forValue(Optional.of(new short[2])));
        assertEquals(FieldType.INTEGER_ARRAY, FieldType.forValue(Optional.of(new int[2])));
        assertEquals(FieldType.LONG_ARRAY, FieldType.forValue(Optional.of(new long[2])));
        assertEquals(FieldType.FLOAT_ARRAY, FieldType.forValue(Optional.of(new float[2])));
        assertEquals(FieldType.DOUBLE_ARRAY, FieldType.forValue(Optional.of(new double[2])));
        assertEquals(FieldType.STRING_ARRAY, FieldType.forValue(Optional.of(new String[2])));
        assertEquals(FieldType.POINT, FieldType.forValue(Optional.of(reader.read("POINT(0 0)"))));
        assertEquals(FieldType.LINESTRING,
                FieldType.forValue(Optional.of(reader.read("LINESTRING(0 0, 1 1)"))));
        assertEquals(FieldType.POLYGON,
                FieldType.forValue(Optional.of(reader.read("POLYGON((0 0, 1 1, 2 2, 0 0))"))));
        assertEquals(FieldType.MULTIPOINT,
                FieldType.forValue(Optional.of(reader.read("MULTIPOINT((0 0),(1 1))"))));
        assertEquals(FieldType.MULTILINESTRING,
 FieldType
                .forValue(Optional.of(reader.read("MULTILINESTRING ((0 0, 1 1),(2 2, 3 3))"))));
        assertEquals(FieldType.MULTIPOLYGON, FieldType.forValue(Optional
                .of(reader.read("MULTIPOLYGON(((0 0, 1 1, 2 2, 0 0)),((3 3, 4 4, 5 5, 3 3)))"))));
        assertEquals(FieldType.GEOMETRYCOLLECTION,
 FieldType.forValue(
                Optional.of(reader.read("GEOMETRYCOLLECTION(POINT(4 6),LINESTRING(4 6,7 10))"))));
        assertEquals(FieldType.UUID, FieldType.forValue(Optional.of(UUID.randomUUID())));
        assertEquals(FieldType.BIG_INTEGER, FieldType.forValue(Optional.of(new BigInteger("0"))));
        assertEquals(FieldType.BIG_DECIMAL, FieldType.forValue(Optional.of(new BigDecimal("0.0"))));
        assertEquals(FieldType.DATE, FieldType.forValue(Optional.of(new java.sql.Date(0L))));
        assertEquals(FieldType.DATETIME, FieldType.forValue(Optional.of(new java.util.Date())));
        assertEquals(FieldType.TIME, FieldType.forValue(Optional.of(new java.sql.Time(0L))));
        assertEquals(FieldType.TIMESTAMP,
                FieldType.forValue(Optional.of(new java.sql.Timestamp(0L))));
        assertEquals(FieldType.MAP, FieldType.forValue(Optional.of(new HashMap<String, String>())));
        assertEquals(FieldType.CHAR, FieldType.forValue(Optional.of('a')));
        assertEquals(FieldType.CHAR_ARRAY, FieldType.forValue(Optional.of(new char[2])));
        assertEquals(FieldType.ENVELOPE_2D, FieldType.forValue(Optional.of(new Envelope())));
    }

    @Test
    public void testForBinding() {
        assertEquals(FieldType.NULL, FieldType.forBinding(null));
        assertEquals(FieldType.NULL, FieldType.forBinding(Void.class));
        assertEquals(FieldType.BOOLEAN, FieldType.forBinding(Boolean.class));
        assertEquals(FieldType.BYTE, FieldType.forBinding(Byte.class));
        assertEquals(FieldType.SHORT, FieldType.forBinding(Short.class));
        assertEquals(FieldType.INTEGER, FieldType.forBinding(Integer.class));
        assertEquals(FieldType.LONG, FieldType.forBinding(Long.class));
        assertEquals(FieldType.FLOAT, FieldType.forBinding(Float.class));
        assertEquals(FieldType.DOUBLE, FieldType.forBinding(Double.class));
        assertEquals(FieldType.STRING, FieldType.forBinding(String.class));
        assertEquals(FieldType.BOOLEAN_ARRAY, FieldType.forBinding(boolean[].class));
        assertEquals(FieldType.BYTE_ARRAY, FieldType.forBinding(byte[].class));
        assertEquals(FieldType.SHORT_ARRAY, FieldType.forBinding(short[].class));
        assertEquals(FieldType.INTEGER_ARRAY, FieldType.forBinding(int[].class));
        assertEquals(FieldType.LONG_ARRAY, FieldType.forBinding(long[].class));
        assertEquals(FieldType.FLOAT_ARRAY, FieldType.forBinding(float[].class));
        assertEquals(FieldType.DOUBLE_ARRAY, FieldType.forBinding(double[].class));
        assertEquals(FieldType.STRING_ARRAY, FieldType.forBinding(String[].class));
        assertEquals(FieldType.POINT, FieldType.forBinding(Point.class));
        assertEquals(FieldType.LINESTRING, FieldType.forBinding(LineString.class));
        assertEquals(FieldType.POLYGON, FieldType.forBinding(Polygon.class));
        assertEquals(FieldType.MULTIPOINT, FieldType.forBinding(MultiPoint.class));
        assertEquals(FieldType.MULTILINESTRING, FieldType.forBinding(MultiLineString.class));
        assertEquals(FieldType.MULTIPOLYGON, FieldType.forBinding(MultiPolygon.class));
        assertEquals(FieldType.GEOMETRY, FieldType.forBinding(Geometry.class));
        assertEquals(FieldType.UUID, FieldType.forBinding(UUID.class));
        assertEquals(FieldType.BIG_INTEGER, FieldType.forBinding(BigInteger.class));
        assertEquals(FieldType.BIG_DECIMAL, FieldType.forBinding(BigDecimal.class));
        assertEquals(FieldType.DATE, FieldType.forBinding(java.sql.Date.class));
        assertEquals(FieldType.DATETIME, FieldType.forBinding(java.util.Date.class));
        assertEquals(FieldType.TIME, FieldType.forBinding(java.sql.Time.class));
        assertEquals(FieldType.TIMESTAMP, FieldType.forBinding(java.sql.Timestamp.class));
        assertEquals(FieldType.MAP, FieldType.forBinding(Map.class));
        assertEquals(FieldType.CHAR, FieldType.forBinding(Character.class));
        assertEquals(FieldType.CHAR_ARRAY, FieldType.forBinding(char[].class));
        assertEquals(FieldType.ENVELOPE_2D, FieldType.forBinding(Envelope.class));
        assertEquals(FieldType.UNKNOWN, FieldType.forBinding(Object.class));
    }

    @Test
    public void testSafeCopy() {
        byte[] original = new byte[] { (byte) 0x01, (byte) 0x02 };
        byte[] copy = (byte[]) FieldType.BYTE_ARRAY.safeCopy(original);
        assertNotSame(original, copy);
        assertEquals(original[0], copy[0]);
        assertEquals(original[1], copy[1]);
    }
}
