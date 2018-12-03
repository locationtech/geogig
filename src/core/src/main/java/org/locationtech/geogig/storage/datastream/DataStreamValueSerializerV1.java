/* Copyright (c) 2014-2016 Boundless and others.
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.locationtech.geogig.model.FieldType;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

/**
 * A class to serializer/deserialize attribute values to/from a data stream
 * 
 */
class DataStreamValueSerializerV1 implements ValueSerializer {

    public static DataStreamValueSerializerV1 INSTANCE = new DataStreamValueSerializerV1();

    protected static final GeometryFactory DEFAULT_GEOMETRY_FACT = new GeometryFactory(
            new PackedCoordinateSequenceFactory());

    @Override
    public Void readNull(DataInput in) throws IOException {
        return null;
    }

    @Override
    public void writeNull(Object obj, DataOutput out) {
        // NO-OP: There is no body for a NULL field
    }

    @Override
    public Boolean readBoolean(DataInput in) throws IOException {
        return in.readBoolean();
    }

    @Override
    public void writeBoolean(Boolean field, DataOutput data) throws IOException {
        data.writeBoolean(field.booleanValue());
    }

    @Override
    public Byte readByte(DataInput in) throws IOException {
        return in.readByte();
    }

    @Override
    public void writeByte(Byte field, DataOutput data) throws IOException {
        data.writeByte(field);
    }

    @Override
    public Short readShort(DataInput in) throws IOException {
        return in.readShort();
    }

    @Override
    public void writeShort(Short field, DataOutput data) throws IOException {
        data.writeShort(field);
    }

    @Override
    public Integer readInt(DataInput in) throws IOException {
        return in.readInt();
    }

    @Override
    public void writeInt(Integer field, DataOutput data) throws IOException {
        data.writeInt(field);
    }

    @Override
    public Character readChar(DataInput in) throws IOException {
        return Character.valueOf((char) in.readInt());
    }

    @Override
    public void writeChar(Character field, DataOutput data) throws IOException {
        data.writeInt(field.charValue());
    }

    @Override
    public char[] readCharArray(DataInput in) throws IOException {
        int len = in.readInt();
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) {
            chars[i] = (char) in.readInt();
        }
        return chars;
    }

    @Override
    public void writeCharArray(char[] field, DataOutput data) throws IOException {
        data.writeInt(field.length);
        for (int i : field)
            data.writeInt(i);
    }

    @Override
    public Object readLong(DataInput in) throws IOException {
        return in.readLong();
    }

    @Override
    public void writeLong(Long field, DataOutput data) throws IOException {
        data.writeLong(field);
    }

    @Override
    public Float readFloat(DataInput in) throws IOException {
        return in.readFloat();
    }

    @Override
    public void writeFloat(Float field, DataOutput data) throws IOException {
        data.writeFloat(field);
    }

    @Override
    public Double readDouble(DataInput in) throws IOException {
        return in.readDouble();
    }

    @Override
    public void writeDouble(Double field, DataOutput data) throws IOException {
        data.writeDouble(field);
    }

    @Override
    public String readString(DataInput in) throws IOException {
        final int multiStringMarkerOrsingleLength;
        // this is the first thing readUTF() does
        multiStringMarkerOrsingleLength = in.readUnsignedShort();

        if (65535 == multiStringMarkerOrsingleLength) {
            final int numChunks = in.readInt();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < numChunks; i++) {
                String chunk = in.readUTF();
                sb.append(chunk);
            }
            return sb.toString();
        } else {
            if (0 == multiStringMarkerOrsingleLength) {
                return "";
            }
            byte[] bytes = new byte[2 + multiStringMarkerOrsingleLength];
            in.readFully(bytes, 2, multiStringMarkerOrsingleLength);
            int utfsize = multiStringMarkerOrsingleLength;
            // reset the utf size header
            bytes[0] = (byte) (0xff & (utfsize >> 8));
            bytes[1] = (byte) (0xff & utfsize);

            ByteArrayDataInput sin = ByteStreams.newDataInput(bytes);
            return sin.readUTF();
        }
    }

    @Override
    public void writeString(String value, DataOutput data) throws IOException {
        // worst case scenario every character is encoded
        // as three bytes and the encoded max length is
        // 65535
        final int maxSafeLength = 65535 / 3;
        if (value.length() > maxSafeLength) {
            List<String> splitted = Splitter.fixedLength(maxSafeLength).splitToList(value);
            data.writeShort(65535);// it's safe to use the max possible UTF length since
                                   // we'll never write such a string in a single chunk
            data.writeInt(splitted.size());
            for (String s : splitted) {
                data.writeUTF(s);
            }
        } else {
            data.writeUTF(value);
        }
    }

    @Override
    public boolean[] readBooleanArray(DataInput in) throws IOException {
        final int len = in.readInt();
        byte[] packed = new byte[(len + 7) / 8]; // we want to round up as long as i % 8 !=
                                                 // 0
        boolean[] bits = new boolean[len];

        int offset = 0;
        int remainingBits = len;
        while (remainingBits > 8) {
            byte chunk = packed[offset / 8];
            for (int i = 0; i < 8; i++) {
                bits[offset + i] = (chunk & (128 >> i)) != 0;
            }
            offset += 8;
            remainingBits -= 8;
        }
        if (remainingBits > 0) {
            byte chunk = packed[packed.length - 1];
            int bitN = 0;
            while (remainingBits > 0) {
                bits[offset + bitN] = (chunk & (128 >> bitN)) != 0;
                remainingBits -= 1;
                bitN += 1;
            }
        }
        return bits;
    }

    @Override
    public void writeBooleanArray(boolean[] bools, DataOutput data) throws IOException {
        byte[] bytes = new byte[(bools.length + 7) / 8];

        int index = 0;
        while (index < bytes.length) {
            int bIndex = index * 8;
            int chunk = 0;
            int bitsInChunk = Math.min(bools.length - bIndex, 8);
            for (int i = 0; i < bitsInChunk; i++) {
                chunk |= (bools[bIndex + i] ? 0 : 1) << (7 - i);
            }
            bytes[index] = (byte) chunk;
        }

        data.writeInt(bools.length);
        data.write(bytes);
    }

    @Override
    public byte[] readByteArray(DataInput in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return bytes;
    }

    @Override
    public void writeByteArray(byte[] field, DataOutput data) throws IOException {
        data.writeInt(field.length);
        data.write((byte[]) field);
    }

    @Override
    public short[] readShortArray(DataInput in) throws IOException {
        int len = in.readInt();
        short[] shorts = new short[len];
        for (int i = 0; i < len; i++) {
            shorts[i] = in.readShort();
        }
        return shorts;
    }

    @Override
    public void writeShortArray(short[] field, DataOutput data) throws IOException {
        data.writeInt(field.length);
        for (short s : field)
            data.writeShort(s);
    }

    @Override
    public int[] readIntArray(DataInput in) throws IOException {
        int len = in.readInt();
        int[] ints = new int[len];
        for (int i = 0; i < len; i++) {
            ints[i] = in.readInt();
        }
        return ints;
    }

    @Override
    public void writeIntArray(int[] field, DataOutput data) throws IOException {
        data.writeInt(field.length);
        for (int i : field)
            data.writeInt(i);
    }

    @Override
    public long[] readLongArray(DataInput in) throws IOException {
        int len = in.readInt();
        long[] longs = new long[len];
        for (int i = 0; i < len; i++) {
            longs[i] = in.readLong();
        }
        return longs;
    }

    @Override
    public void writeLongArray(long[] field, DataOutput data) throws IOException {
        data.writeInt(field.length);
        for (long l : field)
            data.writeLong(l);
    }

    @Override
    public Object readFloatArray(DataInput in) throws IOException {
        int len = in.readInt();
        float[] floats = new float[len];
        for (int i = 0; i < len; i++) {
            floats[i] = in.readFloat();
        }
        return floats;
    }

    @Override
    public void writeFloatArray(float[] field, DataOutput data) throws IOException {
        data.writeInt(field.length);
        for (float f : field)
            data.writeFloat(f);
    }

    @Override
    public double[] readDoubleArray(DataInput in) throws IOException {
        int len = in.readInt();
        double[] doubles = new double[len];
        for (int i = 0; i < len; i++) {
            doubles[i] = in.readDouble();
        }
        return doubles;
    }

    @Override
    public void writeDoubleArray(double[] field, DataOutput data) throws IOException {
        data.writeInt(field.length);
        for (double d : field)
            data.writeDouble(d);
    }

    @Override
    public String[] readStringArray(DataInput in) throws IOException {
        int len = in.readInt();
        String[] strings = new String[len];
        for (int i = 0; i < len; i++) {
            strings[i] = in.readUTF();
        }
        return strings;
    }

    @Override
    public void writeStringArray(String[] field, DataOutput data) throws IOException {
        data.writeInt(field.length);
        for (String s : field)
            writeString(s, data);
    }

    @Override
    public Geometry readGeometry(DataInput in) throws IOException {
        return readGeometry(in, DEFAULT_GEOMETRY_FACT);
    }

    @Override
    public Geometry readGeometry(DataInput in, GeometryFactory geomFac) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        WKBReader wkbReader = new WKBReader(geomFac);
        try {
            return wkbReader.read(bytes);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeGeometry(Geometry field, DataOutput data) throws IOException {
        WKBWriter wkbWriter = new WKBWriter();
        byte[] bytes = wkbWriter.write(field);
        writeByteArray(bytes, data);
    }

    @Override
    public UUID readUUID(DataInput in) throws IOException {
        long upper = in.readLong();
        long lower = in.readLong();
        return new java.util.UUID(upper, lower);
    }

    @Override
    public void writeUUID(UUID field, DataOutput data) throws IOException {
        data.writeLong(field.getMostSignificantBits());
        data.writeLong(field.getLeastSignificantBits());
    }

    @Override
    public BigInteger readBigInteger(DataInput in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new BigInteger(bytes);
    }

    @Override
    public void writeBigInteger(BigInteger field, DataOutput data) throws IOException {
        byte[] bytes = field.toByteArray();
        writeByteArray(bytes, data);
    }

    @Override
    public BigDecimal readBigDecimal(DataInput in) throws IOException {
        int scale = in.readInt();
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        BigInteger intValue = new BigInteger(bytes);
        BigDecimal decValue = new BigDecimal(intValue, scale);
        return decValue;
    }

    @Override
    public void writeBigDecimal(BigDecimal d, DataOutput data) throws IOException {
        int scale = d.scale();
        BigInteger i = d.unscaledValue();
        data.writeInt(scale);
        writeBigInteger(i, data);
    }

    @Override
    public Date readDateTime(DataInput in) throws IOException {
        long time = in.readLong();
        return new Date(time);
    }

    @Override
    public void writeDateTime(java.util.Date date, DataOutput data) throws IOException {
        data.writeLong(date.getTime());
    }

    @Override
    public java.sql.Date readDate(DataInput in) throws IOException {
        long time = in.readLong();
        return new java.sql.Date(time);
    }

    @Override
    public void writeDate(java.sql.Date date, DataOutput data) throws IOException {
        data.writeLong(date.getTime());
    }

    @Override
    public Time readTime(DataInput in) throws IOException {
        long time = in.readLong();
        return new java.sql.Time(time);
    }

    @Override
    public void writeTime(Time time, DataOutput data) throws IOException {
        data.writeLong(time.getTime());
    }

    @Override
    public Timestamp readTimeStamp(DataInput in) throws IOException {
        long time = in.readLong();
        int nanos = in.readInt();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(time);
        timestamp.setNanos(nanos);
        return timestamp;
    }

    @Override
    public void writeTimeStamp(Timestamp timestamp, DataOutput data) throws IOException {
        data.writeLong(timestamp.getTime());
        data.writeInt(timestamp.getNanos());
    }

    @Override
    public Map<String, Object> readMap(DataInput in) throws IOException {
        final int size = in.readInt();
        Map<String, Object> map = Maps.newHashMap();
        for (int i = 0; i < size; i++) {
            String key = readString(in);
            byte fieldTag = in.readByte();
            FieldType fieldType = FieldType.valueOf(fieldTag);
            Object value = decode(fieldType, in);

            map.put(key, value);
        }
        return map;
    }

    @Override
    public void writeMap(Map<String, Object> map, DataOutput data) throws IOException {
        data.writeInt(map.size());
        for (Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey();
            writeString(key, data);

            Object value = e.getValue();
            FieldType fieldType = FieldType.forValue(value);
            data.writeByte(fieldType.getTag());
            encode(fieldType, value, data);
        }
    }

    @Override
    public Envelope readEnvelope(DataInput in) throws IOException {
        double minx = in.readDouble();
        double miny = in.readDouble();
        double maxx = in.readDouble();
        double maxy = in.readDouble();
        return new Envelope(minx, maxx, miny, maxy);
    }

    @Override
    public void writeEnvelope(Envelope field, DataOutput data) throws IOException {
        data.writeDouble(field.getMinX());
        data.writeDouble(field.getMinY());
        data.writeDouble(field.getMaxX());
        data.writeDouble(field.getMaxY());
    }
}
