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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.flatbuffers.generated.v1.values.BIG_DECIMAL;
import org.locationtech.geogig.flatbuffers.generated.v1.values.BIG_INTEGER;
import org.locationtech.geogig.flatbuffers.generated.v1.values.BOOLEAN;
import org.locationtech.geogig.flatbuffers.generated.v1.values.BOOLEAN_ARRAY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.BYTE;
import org.locationtech.geogig.flatbuffers.generated.v1.values.BYTE_ARRAY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.Bounds;
import org.locationtech.geogig.flatbuffers.generated.v1.values.CHAR;
import org.locationtech.geogig.flatbuffers.generated.v1.values.CHAR_ARRAY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.DATETIME;
import org.locationtech.geogig.flatbuffers.generated.v1.values.DOUBLE;
import org.locationtech.geogig.flatbuffers.generated.v1.values.DOUBLE_ARRAY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.Dictionary;
import org.locationtech.geogig.flatbuffers.generated.v1.values.ENVELOPE_2D;
import org.locationtech.geogig.flatbuffers.generated.v1.values.FLOAT;
import org.locationtech.geogig.flatbuffers.generated.v1.values.FLOAT_ARRAY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.GEOMETRY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.INTEGER;
import org.locationtech.geogig.flatbuffers.generated.v1.values.INTEGER_ARRAY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.LONG;
import org.locationtech.geogig.flatbuffers.generated.v1.values.LONG_ARRAY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.MapEntry;
import org.locationtech.geogig.flatbuffers.generated.v1.values.SHORT;
import org.locationtech.geogig.flatbuffers.generated.v1.values.SHORT_ARRAY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.STRING;
import org.locationtech.geogig.flatbuffers.generated.v1.values.STRING_ARRAY;
import org.locationtech.geogig.flatbuffers.generated.v1.values.TIMESTAMP;
import org.locationtech.geogig.flatbuffers.generated.v1.values.UUID;
import org.locationtech.geogig.flatbuffers.generated.v1.values.Value;
import org.locationtech.geogig.flatbuffers.generated.v1.values.ValueUnion;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import com.google.flatbuffers.FlatBufferBuilder;

import lombok.NonNull;

final class ValueSerializer {

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
                valueOffset = BOOLEAN_ARRAY.createBOOLEAN_ARRAY(builder,
                        BOOLEAN_ARRAY.createValueVector(builder, (boolean[]) val));
                break;
            case ValueUnion.BYTE_ARRAY:
                valueOffset = BYTE_ARRAY.createBYTE_ARRAY(builder,
                        BYTE_ARRAY.createValueVector(builder, (byte[]) val));
                break;
            case ValueUnion.SHORT_ARRAY:
                valueOffset = SHORT_ARRAY.createSHORT_ARRAY(builder,
                        SHORT_ARRAY.createValueVector(builder, (short[]) val));
                break;
            case ValueUnion.INTEGER_ARRAY:
                valueOffset = INTEGER_ARRAY.createINTEGER_ARRAY(builder,
                        INTEGER_ARRAY.createValueVector(builder, (int[]) val));
                break;
            case ValueUnion.LONG_ARRAY:
                valueOffset = LONG_ARRAY.createLONG_ARRAY(builder,
                        LONG_ARRAY.createValueVector(builder, (long[]) val));
                break;
            case ValueUnion.FLOAT_ARRAY:
                valueOffset = FLOAT_ARRAY.createFLOAT_ARRAY(builder,
                        FLOAT_ARRAY.createValueVector(builder, (float[]) val));
                break;
            case ValueUnion.DOUBLE_ARRAY:
                valueOffset = DOUBLE_ARRAY.createDOUBLE_ARRAY(builder,
                        DOUBLE_ARRAY.createValueVector(builder, (double[]) val));
                break;
            case ValueUnion.CHAR_ARRAY:
                char[] charData = (char[]) val;
                short[] shortData = new short[charData.length];
                for (int i = 0; i < charData.length; i++) {
                    shortData[i] = (short) charData[i];
                }
                valueOffset = CHAR_ARRAY.createCHAR_ARRAY(builder,
                        CHAR_ARRAY.createValueVector(builder, shortData));
                break;
            case ValueUnion.STRING_ARRAY:
                String[] sdata = (String[]) val;
                int[] strVector = new int[sdata.length];
                for (int i = 0; i < sdata.length; i++) {
                    String s = sdata[i];
                    if (s != null) {
                        strVector[i] = builder.createString(s);
                    }
                }
                valueOffset = STRING_ARRAY.createSTRING_ARRAY(builder,
                        STRING_ARRAY.createValueVector(builder, strVector));
                break;
            case ValueUnion.GEOMETRY:
                valueOffset = GeometrySerializer.encode((Geometry) val, builder);
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
                valueOffset = DATETIME.createDATETIME(builder, ((java.util.Date) val).getTime());
                break;
            case ValueUnion.TIMESTAMP:
                valueOffset = TIMESTAMP.createTIMESTAMP(builder,
                        ((java.sql.Timestamp) val).getTime(),
                        ((java.sql.Timestamp) val).getNanos());
                break;
            case ValueUnion.UUID:
                valueOffset = UUID.createUUID(builder,
                        ((java.util.UUID) val).getLeastSignificantBits(),
                        ((java.util.UUID) val).getMostSignificantBits());
                break;
            case ValueUnion.ENVELOPE_2D:
                Envelope env = (Envelope) val;
                valueOffset = Bounds.createBounds(builder, (float) env.getMinX(),
                        (float) env.getMinY(), (float) env.getMaxX(), (float) env.getMaxY());
                ENVELOPE_2D.startENVELOPE_2D(builder);
                ENVELOPE_2D.addValue(builder, valueOffset);
                valueOffset = ENVELOPE_2D.endENVELOPE_2D(builder);
                break;
            case ValueUnion.Dictionary:
                valueOffset = writeDictionary(builder, (Map<String, Object>) val);
                break;
            default:
                throw new IllegalArgumentException("Unknown ValueUnion value: " + valueType);
            }
        }

        return Value.createValue(builder, valueType, valueOffset);
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
            return Character.valueOf((char) ((CHAR) v.value(new CHAR())).value());
        case ValueUnion.STRING:
            return ((STRING) v.value(new STRING())).value();
        case ValueUnion.BOOLEAN_ARRAY: {
            BOOLEAN_ARRAY value = (BOOLEAN_ARRAY) v.value(new BOOLEAN_ARRAY());
            int valueLength = value.valueLength();
            boolean[] ret = new boolean[valueLength];
            for (int i = 0; i < valueLength; i++) {
                ret[i] = value.value(i);
            }
            return ret;
        }
        case ValueUnion.BYTE_ARRAY: {
            BYTE_ARRAY value = (BYTE_ARRAY) v.value(new BYTE_ARRAY());
            int valueLength = value.valueLength();
            byte[] ret = new byte[valueLength];
            for (int i = 0; i < valueLength; i++) {
                ret[i] = value.value(i);
            }
            return ret;
        }
        case ValueUnion.SHORT_ARRAY: {
            SHORT_ARRAY value = (SHORT_ARRAY) v.value(new SHORT_ARRAY());
            int valueLength = value.valueLength();
            short[] ret = new short[valueLength];
            for (int i = 0; i < valueLength; i++) {
                ret[i] = value.value(i);
            }
            return ret;
        }
        case ValueUnion.INTEGER_ARRAY: {
            INTEGER_ARRAY value = (INTEGER_ARRAY) v.value(new INTEGER_ARRAY());
            int valueLength = value.valueLength();
            int[] ret = new int[valueLength];
            for (int i = 0; i < valueLength; i++) {
                ret[i] = value.value(i);
            }
            return ret;
        }
        case ValueUnion.LONG_ARRAY: {
            LONG_ARRAY value = (LONG_ARRAY) v.value(new LONG_ARRAY());
            int valueLength = value.valueLength();
            long[] ret = new long[valueLength];
            for (int i = 0; i < valueLength; i++) {
                ret[i] = value.value(i);
            }
            return ret;
        }
        case ValueUnion.FLOAT_ARRAY: {
            FLOAT_ARRAY value = (FLOAT_ARRAY) v.value(new FLOAT_ARRAY());
            int valueLength = value.valueLength();
            float[] ret = new float[valueLength];
            for (int i = 0; i < valueLength; i++) {
                ret[i] = value.value(i);
            }
            return ret;
        }
        case ValueUnion.DOUBLE_ARRAY: {
            DOUBLE_ARRAY value = (DOUBLE_ARRAY) v.value(new DOUBLE_ARRAY());
            int valueLength = value.valueLength();
            double[] ret = new double[valueLength];
            for (int i = 0; i < valueLength; i++) {
                ret[i] = value.value(i);
            }
            return ret;
        }
        case ValueUnion.CHAR_ARRAY: {
            CHAR_ARRAY value = (CHAR_ARRAY) v.value(new CHAR_ARRAY());
            int valueLength = value.valueLength();
            char[] ret = new char[valueLength];
            for (int i = 0; i < valueLength; i++) {
                ret[i] = (char) value.value(i);
            }
            return ret;
        }
        case ValueUnion.STRING_ARRAY: {
            STRING_ARRAY value = (STRING_ARRAY) v.value(new STRING_ARRAY());
            int valueLength = value.valueLength();
            String[] ret = new String[valueLength];
            for (int i = 0; i < valueLength; i++) {
                ret[i] = value.value(i);
            }
            return ret;
        }
        case ValueUnion.GEOMETRY:
            return GeometrySerializer.decode((GEOMETRY) v.value(new GEOMETRY()), gf);

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
        case ValueUnion.DATETIME:
            DATETIME dtime = (DATETIME) v.value(new DATETIME());
            return new java.util.Date(dtime.millis());
        case ValueUnion.TIMESTAMP:
            TIMESTAMP ts = (TIMESTAMP) v.value(new TIMESTAMP());
            Timestamp jts = new java.sql.Timestamp(ts.millis());
            jts.setNanos(ts.nanos());
            return jts;
        case ValueUnion.UUID:
            UUID uuid = (UUID) v.value(new UUID());
            return new java.util.UUID(uuid.msb(), uuid.lsb());
        case ValueUnion.ENVELOPE_2D:
            ENVELOPE_2D e2d = (ENVELOPE_2D) v.value(new ENVELOPE_2D());
            Bounds bounds = e2d.value();
            return new Envelope(bounds.x1(), bounds.x2(), bounds.y1(), bounds.y2());
        default:
            throw new IllegalArgumentException("Unknown ValueUnion value: " + valueType);
        }
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

}
