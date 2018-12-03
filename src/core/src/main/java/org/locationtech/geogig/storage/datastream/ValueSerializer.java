/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.locationtech.geogig.model.FieldType;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Interface to read and write atomic values from/to a byte stream.
 * <p>
 * There's a readXXX/writeXXX pair of methods for each supported atomic value type as defined by
 * {@link FieldType}.
 * <p>
 * The generic read and write methods are {@link #encode(Object, DataOutput)} and
 * {@link #decode(FieldType, DataInput)}
 * <p>
 * Implementations shall not add any header (e.g. {@link FieldType#getTag() FieldType's tag}
 * 
 * @since 1.1
 */
public interface ValueSerializer {

    Void readNull(DataInput in) throws IOException;

    void writeNull(Object obj, DataOutput out);

    Boolean readBoolean(DataInput in) throws IOException;

    void writeBoolean(Boolean field, DataOutput data) throws IOException;

    Byte readByte(DataInput in) throws IOException;

    void writeByte(Byte field, DataOutput data) throws IOException;

    Short readShort(DataInput in) throws IOException;

    void writeShort(Short field, DataOutput data) throws IOException;

    Integer readInt(DataInput in) throws IOException;

    void writeInt(Integer field, DataOutput data) throws IOException;

    Character readChar(DataInput in) throws IOException;

    void writeChar(Character field, DataOutput data) throws IOException;

    char[] readCharArray(DataInput in) throws IOException;

    void writeCharArray(char[] field, DataOutput data) throws IOException;

    Object readLong(DataInput in) throws IOException;

    void writeLong(Long field, DataOutput data) throws IOException;

    Float readFloat(DataInput in) throws IOException;

    void writeFloat(Float field, DataOutput data) throws IOException;

    Double readDouble(DataInput in) throws IOException;

    void writeDouble(Double field, DataOutput data) throws IOException;

    String readString(DataInput in) throws IOException;

    void writeString(String value, DataOutput data) throws IOException;

    boolean[] readBooleanArray(DataInput in) throws IOException;

    void writeBooleanArray(boolean[] bools, DataOutput data) throws IOException;

    byte[] readByteArray(DataInput in) throws IOException;

    void writeByteArray(byte[] field, DataOutput data) throws IOException;

    short[] readShortArray(DataInput in) throws IOException;

    void writeShortArray(short[] field, DataOutput data) throws IOException;

    int[] readIntArray(DataInput in) throws IOException;

    void writeIntArray(int[] field, DataOutput data) throws IOException;

    long[] readLongArray(DataInput in) throws IOException;

    void writeLongArray(long[] field, DataOutput data) throws IOException;

    Object readFloatArray(DataInput in) throws IOException;

    void writeFloatArray(float[] field, DataOutput data) throws IOException;

    double[] readDoubleArray(DataInput in) throws IOException;

    void writeDoubleArray(double[] field, DataOutput data) throws IOException;

    String[] readStringArray(DataInput in) throws IOException;

    void writeStringArray(String[] field, DataOutput data) throws IOException;

    Geometry readGeometry(DataInput in) throws IOException;

    Geometry readGeometry(DataInput in, GeometryFactory geomFac) throws IOException;

    void writeGeometry(Geometry field, DataOutput data) throws IOException;

    UUID readUUID(DataInput in) throws IOException;

    void writeUUID(UUID field, DataOutput data) throws IOException;

    BigInteger readBigInteger(DataInput in) throws IOException;

    void writeBigInteger(BigInteger field, DataOutput data) throws IOException;

    BigDecimal readBigDecimal(DataInput in) throws IOException;

    void writeBigDecimal(BigDecimal d, DataOutput data) throws IOException;

    Date readDateTime(DataInput in) throws IOException;

    void writeDateTime(java.util.Date date, DataOutput data) throws IOException;

    java.sql.Date readDate(DataInput in) throws IOException;

    void writeDate(java.sql.Date date, DataOutput data) throws IOException;

    Time readTime(DataInput in) throws IOException;

    void writeTime(Time time, DataOutput data) throws IOException;

    Timestamp readTimeStamp(DataInput in) throws IOException;

    void writeTimeStamp(Timestamp timestamp, DataOutput data) throws IOException;

    Map<String, Object> readMap(DataInput in) throws IOException;

    void writeMap(Map<String, Object> map, DataOutput data) throws IOException;

    Envelope readEnvelope(DataInput in) throws IOException;

    void writeEnvelope(Envelope field, DataOutput data) throws IOException;

    default public void encode(Object value, DataOutput out) throws IOException {
        final FieldType type = FieldType.forValue(value);
        encode(type, value, out);
    }

    @SuppressWarnings("unchecked")
    default public void encode(final FieldType type, Object field, DataOutput data)
            throws IOException {
        switch (type) {
        case BIG_DECIMAL:
            writeBigDecimal((BigDecimal) field, data);
            break;
        case BIG_INTEGER:
            writeBigInteger((BigInteger) field, data);
            break;
        case BOOLEAN:
            writeBoolean((Boolean) field, data);
            break;
        case BOOLEAN_ARRAY:
            writeBooleanArray((boolean[]) field, data);
            break;
        case BYTE:
            writeByte((Byte) field, data);
            break;
        case BYTE_ARRAY:
            writeByteArray((byte[]) field, data);
            break;
        case CHAR:
            writeChar((Character) field, data);
            break;
        case CHAR_ARRAY:
            writeCharArray((char[]) field, data);
            break;
        case DATE:
            writeDate((java.sql.Date) field, data);
            break;
        case DATETIME:
            writeDateTime((Date) field, data);
            break;
        case DOUBLE:
            writeDouble((Double) field, data);
            break;
        case DOUBLE_ARRAY:
            writeDoubleArray((double[]) field, data);
            break;
        case ENVELOPE_2D:
            writeEnvelope((Envelope) field, data);
            break;
        case FLOAT:
            writeFloat((Float) field, data);
            break;
        case FLOAT_ARRAY:
            writeFloatArray((float[]) field, data);
            break;
        case GEOMETRY:
        case POINT:
        case LINESTRING:
        case POLYGON:
        case MULTIPOINT:
        case MULTILINESTRING:
        case MULTIPOLYGON:
        case GEOMETRYCOLLECTION:
            writeGeometry((Geometry) field, data);
            break;
        case INTEGER:
            writeInt((Integer) field, data);
            break;
        case INTEGER_ARRAY:
            writeIntArray((int[]) field, data);
            break;
        case LONG:
            writeLong((Long) field, data);
            break;
        case LONG_ARRAY:
            writeLongArray((long[]) field, data);
            break;
        case MAP:
            writeMap((Map<String, Object>) field, data);
            break;
        case NULL:
            writeNull(field, data);
            break;
        case SHORT:
            writeShort((Short) field, data);
            break;
        case SHORT_ARRAY:
            writeShortArray((short[]) field, data);
            break;
        case STRING:
            writeString((String) field, data);
            break;
        case STRING_ARRAY:
            writeStringArray((String[]) field, data);
            break;
        case TIME:
            writeTime((Time) field, data);
            break;
        case TIMESTAMP:
            writeTimeStamp((Timestamp) field, data);
            break;
        case UUID:
            writeUUID((UUID) field, data);
            break;
        default:
            throw new IllegalArgumentException(
                    "The specified type (" + type + ") is not supported");
        }
    }

    default public Object decode(FieldType type, DataInput in) throws IOException {
        switch (type) {
        case BIG_DECIMAL:
            return readBigDecimal(in);
        case BIG_INTEGER:
            return readBigInteger(in);
        case BOOLEAN:
            return readBoolean(in);
        case BOOLEAN_ARRAY:
            return readBooleanArray(in);
        case BYTE:
            return readByte(in);
        case BYTE_ARRAY:
            return readByteArray(in);
        case CHAR:
            return readChar(in);
        case CHAR_ARRAY:
            return readCharArray(in);
        case DATE:
            return readDate(in);
        case DATETIME:
            return readDateTime(in);
        case DOUBLE:
            return readDouble(in);
        case DOUBLE_ARRAY:
            return readDoubleArray(in);
        case ENVELOPE_2D:
            return readEnvelope(in);
        case FLOAT:
            return readFloat(in);
        case FLOAT_ARRAY:
            return readFloatArray(in);
        case GEOMETRY:
        case POINT:
        case POLYGON:
        case LINESTRING:
        case MULTILINESTRING:
        case MULTIPOINT:
        case MULTIPOLYGON:
        case GEOMETRYCOLLECTION:
            return readGeometry(in);
        case INTEGER:
            return readInt(in);
        case INTEGER_ARRAY:
            return readIntArray(in);
        case LONG:
            return readLong(in);
        case LONG_ARRAY:
            return readLongArray(in);
        case MAP:
            return readMap(in);
        case NULL:
            return readNull(in);
        case SHORT:
            return readShort(in);
        case SHORT_ARRAY:
            return readShortArray(in);
        case STRING:
            return readString(in);
        case STRING_ARRAY:
            return readStringArray(in);
        case TIME:
            return readTime(in);
        case TIMESTAMP:
            return readTimeStamp(in);
        case UUID:
            return readUUID(in);
        default:
            throw new IllegalArgumentException("The specified type is not supported: " + type);
        }
    }

}