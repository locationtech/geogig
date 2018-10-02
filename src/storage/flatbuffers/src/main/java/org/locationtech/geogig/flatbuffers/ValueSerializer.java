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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.flatbuffers.generated.values.BIG_DECIMAL;
import org.locationtech.geogig.flatbuffers.generated.values.BIG_INTEGER;
import org.locationtech.geogig.flatbuffers.generated.values.BOOLEAN;
import org.locationtech.geogig.flatbuffers.generated.values.BYTE;
import org.locationtech.geogig.flatbuffers.generated.values.Bounds;
import org.locationtech.geogig.flatbuffers.generated.values.CHAR;
import org.locationtech.geogig.flatbuffers.generated.values.DOUBLE;
import org.locationtech.geogig.flatbuffers.generated.values.Dictionary;
import org.locationtech.geogig.flatbuffers.generated.values.ENCODEDGEOMETRY;
import org.locationtech.geogig.flatbuffers.generated.values.FLATGEOMETRY;
import org.locationtech.geogig.flatbuffers.generated.values.FLOAT;
import org.locationtech.geogig.flatbuffers.generated.values.GEOMETRY;
import org.locationtech.geogig.flatbuffers.generated.values.GeometryType;
import org.locationtech.geogig.flatbuffers.generated.values.INTEGER;
import org.locationtech.geogig.flatbuffers.generated.values.LONG;
import org.locationtech.geogig.flatbuffers.generated.values.MapEntry;
import org.locationtech.geogig.flatbuffers.generated.values.SHORT;
import org.locationtech.geogig.flatbuffers.generated.values.STRING;
import org.locationtech.geogig.flatbuffers.generated.values.UUID;
import org.locationtech.geogig.flatbuffers.generated.values.Value;
import org.locationtech.geogig.flatbuffers.generated.values.ValueUnion;
import org.locationtech.geogig.flatbuffers.generated.values.WKBGEOMETRY;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.InStream;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import com.google.flatbuffers.FlatBufferBuilder;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

final class ValueSerializer {

    private static final GeometryFactory defaultGF = new GeometryFactory(
            new PackedCoordinateSequenceFactory(PackedCoordinateSequenceFactory.FLOAT));

    public static int encode(@Nullable Object val, @NonNull FlatBufferBuilder builder) {

        byte valueType = ValueUnion.NONE;
        int valueOffset = 0;

        if (val != null) {
            valueType = getValueType(val);
            switch (valueType) {
            case ValueUnion.BOOLEAN:
                valueOffset = BOOLEAN.createBOOLEAN(builder, ((Boolean) val).booleanValue());
                break;
            case ValueUnion.BYTE:
                valueOffset = BYTE.createBYTE(builder, ((Byte) val).byteValue());
                break;
            case ValueUnion.SHORT:
                valueOffset = SHORT.createSHORT(builder, ((Short) val).shortValue());
                break;
            case ValueUnion.INTEGER:
                valueOffset = INTEGER.createINTEGER(builder, ((Integer) val).intValue());
                break;
            case ValueUnion.LONG:
                valueOffset = LONG.createLONG(builder, ((Long) val).longValue());
                break;
            case ValueUnion.FLOAT:
                valueOffset = FLOAT.createFLOAT(builder, ((Float) val).floatValue());
                break;
            case ValueUnion.DOUBLE:
                valueOffset = DOUBLE.createDOUBLE(builder, ((Double) val).doubleValue());
                break;
            case ValueUnion.CHAR:
                valueOffset = CHAR.createCHAR(builder, ((Character) val).charValue());
                break;
            case ValueUnion.STRING: {
                int offset = builder.createString((String) val);
                valueOffset = STRING.createSTRING(builder, offset);
                break;
            }
            case ValueUnion.BOOLEAN_ARRAY:
                valueOffset = 0;
                break;
            case ValueUnion.BYTE_ARRAY:
                valueOffset = 0;
                break;
            case ValueUnion.SHORT_ARRAY:
                valueOffset = 0;
                break;
            case ValueUnion.INTEGER_ARRAY:
                valueOffset = 0;
                break;
            case ValueUnion.LONG_ARRAY:
                valueOffset = 0;
                break;
            case ValueUnion.FLOAT_ARRAY:
                valueOffset = 0;
                break;
            case ValueUnion.DOUBLE_ARRAY:
                valueOffset = 0;
                break;
            case ValueUnion.CHAR_ARRAY:
                valueOffset = 0;
                break;
            case ValueUnion.STRING_ARRAY:
                valueOffset = 0;
                break;
            case ValueUnion.GEOMETRY: {
                Geometry geom = (Geometry) val;
                byte geomType;
                int geomOffset;
                if (geom instanceof GeometryCollection || geom instanceof Polygon) {
                    geomType = ENCODEDGEOMETRY.WKBGEOMETRY;
                    byte[] data = new WKBWriter().write(geom);
                    int valueVector = WKBGEOMETRY.createValueVector(builder, data);
                    geomOffset = WKBGEOMETRY.createWKBGEOMETRY(builder, valueVector);
                } else {
                    geomType = ENCODEDGEOMETRY.FLATGEOMETRY;
                    final byte type;
                    switch (geom.getGeometryType()) {
                    case "Point":
                        type = GeometryType.Point;
                        break;
                    case "LineString":
                        type = GeometryType.LineString;
                        break;
                    default:
                        throw new IllegalArgumentException(geom.getGeometryType());
                    }

                    final int dimension = 2;
                    final int numOrdinates = geom.getNumPoints() * dimension;
                    FLATGEOMETRY.startOrdinatesVector(builder, numOrdinates);
                    geom.apply((CoordinateFilter) c -> {
                        builder.addDouble(c.getX());
                        builder.addDouble(c.getY());
                    });
                    int ordinatesOffset = builder.endVector();

                    geomOffset = FLATGEOMETRY.createFLATGEOMETRY(builder, dimension, type,
                            ordinatesOffset);
                }
                valueOffset = GEOMETRY.createGEOMETRY(builder, geomType, geomOffset);
            }
                break;
            case ValueUnion.BIG_INTEGER: {
                BigInteger d = (BigInteger) val;
                int boff = builder.createByteVector(d.toByteArray());
                valueOffset = BIG_INTEGER.createBIG_INTEGER(builder, boff);
            }
                break;
            case ValueUnion.BIG_DECIMAL: {
                BigDecimal d = (BigDecimal) val;
                byte[] byteArray = d.unscaledValue().toByteArray();
                int boff = builder.createByteVector(byteArray);
                int scale = d.scale();
                valueOffset = BIG_DECIMAL.createBIG_DECIMAL(builder, scale, boff);
            }
                break;
            case ValueUnion.DATETIME:
                valueOffset = 0;
                break;
            case ValueUnion.TIMESTAMP:
                valueOffset = 0;
                break;
            case ValueUnion.UUID:
                valueOffset = UUID.createUUID(builder,
                        ((java.util.UUID) val).getLeastSignificantBits(),
                        ((java.util.UUID) val).getMostSignificantBits());
                break;
            case ValueUnion.ENVELOPE_2D: {
                Envelope env = (Envelope) val;
                Bounds.createBounds(builder, (float) env.getMinX(), (float) env.getMinY(),
                        (float) env.getMaxX(), (float) env.getMaxY());
                valueOffset = 0;
            }
                break;
            case ValueUnion.Dictionary:
                valueOffset = writeDictionary(builder, (Map<String, Object>) val);
                break;
            }
        }

        return Value.createValue(builder, valueType, valueOffset);
    }

    private static byte getValueType(@NonNull Object val) {
        final FieldType fieldType = FieldType.forValue(val);
        //@formatter:off
        switch (fieldType) {
        case BOOLEAN: return ValueUnion.BOOLEAN;
        case BYTE: return ValueUnion.BYTE;
        case SHORT: return ValueUnion.SHORT;
        case INTEGER: return ValueUnion.INTEGER;
        case LONG: return ValueUnion.LONG;
        case FLOAT: return ValueUnion.FLOAT;
        case DOUBLE: return ValueUnion.DOUBLE;
        case STRING: return ValueUnion.STRING;
        case BOOLEAN_ARRAY: return ValueUnion.BOOLEAN_ARRAY;
        case BYTE_ARRAY: return ValueUnion.BYTE_ARRAY;
        case SHORT_ARRAY: return ValueUnion.SHORT_ARRAY;
        case INTEGER_ARRAY: return ValueUnion.INTEGER_ARRAY;
        case LONG_ARRAY: return ValueUnion.LONG_ARRAY;
        case FLOAT_ARRAY: return ValueUnion.FLOAT_ARRAY;
        case DOUBLE_ARRAY: return ValueUnion.DOUBLE_ARRAY;
        case STRING_ARRAY: return ValueUnion.STRING_ARRAY;
        
        case POINT:
        case LINESTRING:
        case POLYGON:
        case MULTIPOINT:
        case MULTILINESTRING:
        case MULTIPOLYGON:
        case GEOMETRYCOLLECTION:
        case GEOMETRY:
            return ValueUnion.GEOMETRY;
            
        case UUID: return ValueUnion.UUID;
        case BIG_INTEGER: return ValueUnion.BIG_INTEGER;
        case BIG_DECIMAL: return ValueUnion.BIG_DECIMAL;
        case DATETIME: return ValueUnion.DATETIME;
        case DATE: return ValueUnion.DATETIME;
        case TIME: return ValueUnion.DATETIME;
        case TIMESTAMP: return ValueUnion.TIMESTAMP;
        case MAP: return ValueUnion.Dictionary;
        case CHAR: return ValueUnion.CHAR;
        case CHAR_ARRAY: return ValueUnion.CHAR_ARRAY;
        case ENVELOPE_2D: return ValueUnion.ENVELOPE_2D;
        default:
            throw new UnsupportedOperationException("Unknown FieldType: " + fieldType);
        }
        //@formatter:on
    }

    public static @Nullable Object decodeValue(@NonNull Value v) {
        return decodeValue(v, null);
    }

    public static @Nullable Object decodeValue(@NonNull Value v, @Nullable GeometryFactory gf) {
        final byte valueType = v.valueType();
        if (ValueUnion.NONE == valueType) {
            return null;
        }
        switch (valueType) {
        case ValueUnion.BOOLEAN:
            return ((BOOLEAN) v.value(new BOOLEAN())).value();
        case ValueUnion.BYTE:
            return ((BYTE) v.value(new BYTE())).value();
        case ValueUnion.SHORT:
            return ((SHORT) v.value(new SHORT())).value();
        case ValueUnion.INTEGER:
            return ((INTEGER) v.value(new INTEGER())).value();
        case ValueUnion.LONG:
            return ((LONG) v.value(new LONG())).value();
        case ValueUnion.FLOAT:
            return ((FLOAT) v.value(new FLOAT())).value();
        case ValueUnion.DOUBLE:
            return ((DOUBLE) v.value(new DOUBLE())).value();
        case ValueUnion.CHAR:
            return ((CHAR) v.value(new CHAR())).value();
        case ValueUnion.STRING:
            return ((STRING) v.value(new STRING())).value();
        // case ValueUnion.BOOLEAN_ARRAY: valueOffset = 0; break;
        // case ValueUnion.BYTE_ARRAY: valueOffset = 0; break;
        // case ValueUnion.SHORT_ARRAY: valueOffset = 0; break;
        // case ValueUnion.INTEGER_ARRAY: valueOffset = 0; break;
        // case ValueUnion.LONG_ARRAY: valueOffset = 0; break;
        // case ValueUnion.FLOAT_ARRAY: valueOffset = 0; break;
        // case ValueUnion.DOUBLE_ARRAY: valueOffset = 0; break;
        // case ValueUnion.CHAR_ARRAY: valueOffset = 0; break;
        // case ValueUnion.STRING_ARRAY: valueOffset = 0; break;
        case ValueUnion.GEOMETRY: {
            GEOMETRY gval = (GEOMETRY) v.value(new GEOMETRY());
            final byte gtype = gval.valueType();
            final GeometryFactory geomFac = gf == null ? defaultGF : gf;
            if (ENCODEDGEOMETRY.WKBGEOMETRY == gtype) {
                WKBGEOMETRY wkb = (WKBGEOMETRY) gval.value(new WKBGEOMETRY());
                ByteBuffer bb = wkb.valueAsByteBuffer();
                WKBReader reader = new WKBReader(geomFac);
                try {
                    return reader.read(new ByteBufferInStream(bb));
                } catch (IOException | ParseException e) {
                    throw new RuntimeException(e);
                }
            } else if (ENCODEDGEOMETRY.FLATGEOMETRY == gtype) {
                FLATGEOMETRY fg = (FLATGEOMETRY) gval.value(new FLATGEOMETRY());
                final int dimension = fg.dimension();
                final byte geometryType = fg.type();
                final int numOrdinates = fg.ordinatesLength();
                final int numCoordinates = numOrdinates / dimension;
                CoordinateSequence coordSeq = new FlatGeomCoordSequence(fg, dimension,
                        numCoordinates);
                Geometry geom;
                switch (geometryType) {
                case GeometryType.Point:
                    geom = geomFac.createPoint(coordSeq);
                    break;
                case GeometryType.LineString:
                    geom = geomFac.createLineString(coordSeq);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unrecognized encoded GeometryType enum: " + geometryType);
                }
                return geom;
            }
            throw new IllegalArgumentException("Unrecognized encoded geometry type: " + gtype);
        }
        case ValueUnion.BIG_INTEGER: {
            ByteBuffer bb = ((BIG_INTEGER) v.value(new BIG_INTEGER())).valueAsByteBuffer();
            byte[] val = new byte[bb.remaining()];
            bb.get(val);
            return new BigInteger(val);
        }
        case ValueUnion.BIG_DECIMAL: {
            BIG_DECIMAL bd = (BIG_DECIMAL) v.value(new BIG_DECIMAL());
            int scale = bd.scale();
            ByteBuffer bb = bd.valueAsByteBuffer();
            byte[] val = new byte[bb.remaining()];
            bb.get(val);
            return new BigDecimal(new BigInteger(val), scale);
        }
        case ValueUnion.Dictionary: {
            Dictionary dict = (Dictionary) v.value(new Dictionary());
            return decode(dict);
        }
        // case ValueUnion.DATETIME: valueOffset = 0; break;
        // case ValueUnion.TIMESTAMP: valueOffset = 0; break;
        // case ValueUnion.UUID: valueOffset = 0; break;
        // case ValueUnion.Bounds: valueOffset = 0; break;
        // case ValueUnion.ENCODEDMAP: valueOffset = 0; break;
        }
        return null;
    }

    public static int writeDictionary(FlatBufferBuilder builder, Map<String, Object> data) {
        int[] entriesOffsets = new int[data.size()];
        int i = 0;
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            int keyOffset = builder.createString(key);
            int valueOffset = encode(value, builder);
            int entryOffset = MapEntry.createMapEntry(builder, keyOffset, valueOffset);
            entriesOffsets[i++] = entryOffset;
        }
        int entriesOffset = Dictionary.createEntriesVector(builder, entriesOffsets);
        return Dictionary.createDictionary(builder, entriesOffset);
    }

    private @RequiredArgsConstructor static class ByteBufferInStream implements InStream {
        private final ByteBuffer bb;

        public @Override void read(byte[] buf) throws IOException {
            bb.get(buf);
        }
    }

    private static @RequiredArgsConstructor class FlatGeomCoordSequence
            implements CoordinateSequence {

        private final FLATGEOMETRY fg;

        private final int dimension;

        private final int numCoordinates;

        public @Override int getDimension() {
            return dimension;
        }

        public @Override Coordinate getCoordinate(int i) {
            return getCoordinateCopy(i);
        }

        public @Override Coordinate getCoordinateCopy(int i) {
            Coordinate c = new Coordinate();
            getCoordinate(i, c);
            return c;
        }

        public @Override void getCoordinate(int index, Coordinate coord) {
            coord.setX(getX(index));
            coord.setY(getY(index));
        }

        public @Override double getX(int index) {
            return getOrdinate(index, 0);
        }

        public @Override double getY(int index) {
            return getOrdinate(index, 1);
        }

        public @Override double getOrdinate(int index, int ordinateIndex) {
            int idx = index * dimension + ordinateIndex;
            return fg.ordinates(idx);
        }

        public @Override int size() {
            return numCoordinates;
        }

        public @Override void setOrdinate(int index, int ordinateIndex, double value) {
            throw new UnsupportedOperationException();
        }

        public @Override Coordinate[] toCoordinateArray() {
            int size = size();
            Coordinate[] coords = new Coordinate[size];
            for (int i = 0; i < size; i++) {
                coords[i] = getCoordinate(i);
            }
            return coords;
        }

        public @Override Envelope expandEnvelope(Envelope env) {
            for (int i = 0; i < size(); i++) {
                env.expandToInclude(getOrdinate(i, 0), getOrdinate(i, 1));
            }
            return env;
        }

        public @Override CoordinateSequence copy() {
            return new FlatGeomCoordSequence(fg, dimension, numCoordinates);
        }

        public @Override Object clone() {
            return copy();
        }
    }

    public static Map<String, Object> decode(@NonNull Dictionary dict) {
        final int numEntries = dict.entriesLength();
        Map<String, Object> map = new HashMap<>();
        MapEntry buff = new MapEntry();
        for (int i = 0; i < numEntries; i++) {
            buff = dict.entries(buff, i);
            String key = buff.key();
            Value value = buff.value();
            Object decodedValue = decodeValue(value);
            map.put(key, decodedValue);
        }
        return map;
    }
}
