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

import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.locationtech.geogig.model.FieldType;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.InStream;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import com.google.common.base.Splitter;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

/**
 * A class to serializer/deserialize attribute values to/from a data stream
 * 
 */
public class DataStreamValueSerializerV2 extends DataStreamValueSerializerV1 {

    public static final DataStreamValueSerializerV2 INSTANCE = new DataStreamValueSerializerV2();

    @Override
    public byte[] readByteArray(DataInput in) throws IOException {
        final int len = readUnsignedVarInt(in);
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return bytes;
    }

    @Override
    public void writeByteArray(byte[] field, DataOutput data) throws IOException {
        writeUnsignedVarInt(field.length, data);
        data.write(field);
    }


    @Override
    public void writeGeometry(Geometry field, DataOutput data) throws IOException {
        WKBWriter wkbWriter = new WKBWriter();
        byte[] bytes = wkbWriter.write(field);
        writeByteArray(bytes, data);
    }

    @Override
    public Geometry readGeometry(DataInput in, GeometryFactory geomFac) throws IOException {
        final int len = readUnsignedVarInt(in);
        final DataInputInStream inStream = new DataInputInStream(in, len);
        WKBReader wkbReader = new WKBReader(geomFac);
        try {
            return wkbReader.read(inStream);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static class DataInputInStream implements InStream {

        private final DataInput in;

        private final int limit;

        private int read;

        DataInputInStream(DataInput in, int limit) {
            this.in = in;
            this.limit = limit;

        }

        @Override
        public void read(byte[] buf) throws IOException {
            read += buf.length;
            if (read > limit) {
                throw new EOFException(
                        String.format("Trying to read %,d bytes, limit is %,d", read, limit));
            }
            this.in.readFully(buf);
        }

    }

    @Override
    public Short readShort(DataInput in) throws IOException {
        return Short.valueOf((short) readSignedVarInt(in));
    }

    @Override
    public void writeShort(Short field, DataOutput data) throws IOException {
        writeSignedVarInt(((Number) field).intValue(), data);
    }

    @Override
    public Integer readInt(DataInput in) throws IOException {
        return Integer.valueOf(readSignedVarInt(in));
    }

    @Override
    public void writeInt(Integer field, DataOutput data) throws IOException {
        writeSignedVarInt(((Number) field).intValue(), data);
    }

    @Override
    public Long readLong(DataInput in) throws IOException {
        return Long.valueOf(readSignedVarLong(in));
    }

    @Override
    public void writeLong(Long field, DataOutput data) throws IOException {
        writeSignedVarLong(field.longValue(), data);
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
        final int len = readUnsignedVarInt(in);
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
    public void writeBooleanArray(boolean[] field, DataOutput data) throws IOException {
        boolean[] bools = (boolean[]) field;
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

        writeUnsignedVarInt(bools.length, data);
        data.write(bytes);
    }

    @Override
    public short[] readShortArray(DataInput in) throws IOException {
        final int len = readUnsignedVarInt(in);
        short[] shorts = new short[len];
        for (int i = 0; i < len; i++) {
            shorts[i] = in.readShort();
        }
        return shorts;
    }

    @Override
    public void writeShortArray(short[] field, DataOutput data) throws IOException {
        writeUnsignedVarInt(field.length, data);
        for (short s : field)
            data.writeShort(s);
    }

    @Override
    public int[] readIntArray(DataInput in) throws IOException {
        final int len = readUnsignedVarInt(in);
        int[] ints = new int[len];
        for (int i = 0; i < len; i++) {
            ints[i] = in.readInt();
        }
        return ints;
    }

    @Override
    public void writeIntArray(int[] field, DataOutput data) throws IOException {
        writeUnsignedVarInt(field.length, data);
        for (int i : (int[]) field)
            data.writeInt(i);
    }

    @Override
    public long[] readLongArray(DataInput in) throws IOException {
        final int len = readUnsignedVarInt(in);
        long[] longs = new long[len];
        for (int i = 0; i < len; i++) {
            longs[i] = in.readLong();
        }
        return longs;
    }

    @Override
    public void writeLongArray(long[] field, DataOutput data) throws IOException {
        writeUnsignedVarInt(field.length, data);
        for (long l : (long[]) field)
            data.writeLong(l);
    }

    @Override
    public float[] readFloatArray(DataInput in) throws IOException {
        final int len = readUnsignedVarInt(in);
        float[] floats = new float[len];
        for (int i = 0; i < len; i++) {
            floats[i] = in.readFloat();
        }
        return floats;
    }

    @Override
    public void writeFloatArray(float[] field, DataOutput data) throws IOException {
        writeUnsignedVarInt(field.length, data);
        for (float f : field)
            data.writeFloat(f);
    }

    @Override
    public double[] readDoubleArray(DataInput in) throws IOException {
        final int len = readUnsignedVarInt(in);
        double[] doubles = new double[len];
        for (int i = 0; i < len; i++) {
            doubles[i] = in.readDouble();
        }
        return doubles;
    }

    @Override
    public void writeDoubleArray(double[] field, DataOutput data) throws IOException {
        writeUnsignedVarInt(field.length, data);
        for (double d : field)
            data.writeDouble(d);
    }

    @Override
    public String[] readStringArray(DataInput in) throws IOException {
        final int len = readUnsignedVarInt(in);
        String[] strings = new String[len];
        for (int i = 0; i < len; i++) {
            strings[i] = readString(in);
        }
        return strings;
    }

    @Override
    public void writeStringArray(String[] field, DataOutput data) throws IOException {
        writeUnsignedVarInt(field.length, data);
        for (String s : field)
            writeString(s, data);
    }

    @Override
    public UUID readUUID(DataInput in) throws IOException {
        long upper = readSignedVarLong(in);
        long lower = readSignedVarLong(in);
        return new java.util.UUID(upper, lower);
    }

    @Override
    public void writeUUID(UUID field, DataOutput data) throws IOException {
        writeSignedVarLong(field.getMostSignificantBits(), data);
        writeSignedVarLong(field.getLeastSignificantBits(), data);
    }

    @Override
    public BigInteger readBigInteger(DataInput in) throws IOException {
        byte[] bytes = readByteArray(in);
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
        BigInteger intValue = readBigInteger(in);
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
    public Map<String, Object> readMap(DataInput in) throws IOException {
        final int size = readUnsignedVarInt(in);

        Map<String, Object> map = new HashMap<>();

        String key;
        byte fieldTag;
        FieldType fieldType;
        Object value;

        for (int i = 0; i < size; i++) {
            key = readString(in);

            fieldTag = in.readByte();
            fieldType = FieldType.valueOf(fieldTag);
            value = decode(fieldType, in);

            map.put(key, value);
        }
        return map;
    }

    @Override
    public void writeMap(Map<String, Object> map, DataOutput out) throws IOException {
        final int size = map.size();

        writeUnsignedVarInt(size, out);

        for (Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey();
            writeString(key, out);

            Object value = e.getValue();
            FieldType fieldType = FieldType.forValue(value);
            out.writeByte(fieldType.getTag());
            encode(fieldType, value, out);
        }
    }
}
