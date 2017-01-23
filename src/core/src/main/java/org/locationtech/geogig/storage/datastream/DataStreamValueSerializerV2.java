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
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequenceFactory;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;

/**
 * A class to serializer/deserialize attribute values to/from a data stream
 * 
 */
public class DataStreamValueSerializerV2 {

    public static interface ValueSerializer {

        public Object read(DataInput in) throws IOException;

        public void write(Object obj, DataOutput out) throws IOException;

    }

    static final ValueSerializer byteArray = new ValueSerializer() {
        @Override
        public Object read(DataInput in) throws IOException {
            final int len = readUnsignedVarInt(in);
            byte[] bytes = new byte[len];
            in.readFully(bytes);
            return bytes;
        }

        @Override
        public void write(Object field, DataOutput data) throws IOException {
            writeUnsignedVarInt(((byte[]) field).length, data);
            data.write((byte[]) field);
        }
    };

    static final class WKBSerializer implements ValueSerializer {
        final GeometryFactory GEOM_FACT = new GeometryFactory(
                new PackedCoordinateSequenceFactory());

        @Override
        public Object read(DataInput in) throws IOException {
            return read(in, GEOM_FACT);
        }

        public Geometry read(DataInput in, GeometryFactory geomFac) throws IOException {
            byte[] bytes = (byte[]) byteArray.read(in);
            WKBReader wkbReader = new WKBReader(geomFac);
            try {
                return wkbReader.read(bytes);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void write(Object field, DataOutput data) throws IOException {
            WKBWriter wkbWriter = new WKBWriter();
            byte[] bytes = wkbWriter.write((Geometry) field);
            byteArray.write(bytes, data);
        }
    };

    static final WKBSerializer geometry = new WKBSerializer();

    static final Map<FieldType, ValueSerializer> serializers = new EnumMap<>(FieldType.class);
    static {
        serializers.put(FieldType.NULL, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                return null;
            }

            @Override
            public void write(Object obj, DataOutput out) {
                // NO-OP: There is no body for a NULL field
            }
        });
        serializers.put(FieldType.BOOLEAN, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                return in.readBoolean();
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                data.writeBoolean((Boolean) field);
            }
        });
        serializers.put(FieldType.BYTE, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                return in.readByte();
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                data.writeByte((Byte) field);
            }
        });
        serializers.put(FieldType.SHORT, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                return Short.valueOf((short) readSignedVarInt(in));
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeSignedVarInt(((Number) field).intValue(), data);
            }
        });
        serializers.put(FieldType.INTEGER, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                return Integer.valueOf(readSignedVarInt(in));
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeSignedVarInt(((Number) field).intValue(), data);
            }
        });
        serializers.put(FieldType.LONG, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                return Long.valueOf(readSignedVarLong(in));
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeSignedVarLong(((Number) field).longValue(), data);
            }
        });
        serializers.put(FieldType.FLOAT, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                return in.readFloat();
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                data.writeFloat((Float) field);
            }
        });
        serializers.put(FieldType.DOUBLE, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                return in.readDouble();
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                data.writeDouble((Double) field);
            }
        });
        final ValueSerializer stringSerializer = new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
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
            public void write(Object field, DataOutput data) throws IOException {
                final String value = (String) field;
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
        };
        serializers.put(FieldType.STRING, stringSerializer);
        serializers.put(FieldType.BOOLEAN_ARRAY, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
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
            public void write(Object field, DataOutput data) throws IOException {
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
        });
        serializers.put(FieldType.BYTE_ARRAY, byteArray);
        serializers.put(FieldType.SHORT_ARRAY, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                final int len = readUnsignedVarInt(in);
                short[] shorts = new short[len];
                for (int i = 0; i < len; i++) {
                    shorts[i] = in.readShort();
                }
                return shorts;
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeUnsignedVarInt(((short[]) field).length, data);
                for (short s : (short[]) field)
                    data.writeShort(s);
            }
        });
        serializers.put(FieldType.INTEGER_ARRAY, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                final int len = readUnsignedVarInt(in);
                int[] ints = new int[len];
                for (int i = 0; i < len; i++) {
                    ints[i] = in.readInt();
                }
                return ints;
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeUnsignedVarInt(((int[]) field).length, data);
                for (int i : (int[]) field)
                    data.writeInt(i);
            }
        });
        serializers.put(FieldType.LONG_ARRAY, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                final int len = readUnsignedVarInt(in);
                long[] longs = new long[len];
                for (int i = 0; i < len; i++) {
                    longs[i] = in.readLong();
                }
                return longs;
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeUnsignedVarInt(((long[]) field).length, data);
                for (long l : (long[]) field)
                    data.writeLong(l);
            }
        });
        serializers.put(FieldType.FLOAT_ARRAY, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                final int len = readUnsignedVarInt(in);
                float[] floats = new float[len];
                for (int i = 0; i < len; i++) {
                    floats[i] = in.readFloat();
                }
                return floats;
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeUnsignedVarInt(((float[]) field).length, data);
                for (float f : (float[]) field)
                    data.writeFloat(f);
            }
        });
        serializers.put(FieldType.DOUBLE_ARRAY, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                final int len = readUnsignedVarInt(in);
                double[] doubles = new double[len];
                for (int i = 0; i < len; i++) {
                    doubles[i] = in.readDouble();
                }
                return doubles;
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeUnsignedVarInt(((double[]) field).length, data);
                for (double d : (double[]) field)
                    data.writeDouble(d);
            }
        });
        serializers.put(FieldType.STRING_ARRAY, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                final int len = readUnsignedVarInt(in);
                String[] strings = new String[len];
                for (int i = 0; i < len; i++) {
                    strings[i] = (String) stringSerializer.read(in);
                }
                return strings;
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeUnsignedVarInt(((String[]) field).length, data);
                for (String s : (String[]) field)
                    stringSerializer.write(s, data);
            }
        });

        serializers.put(FieldType.GEOMETRY, geometry);
        serializers.put(FieldType.POINT, geometry);
        serializers.put(FieldType.LINESTRING, geometry);
        serializers.put(FieldType.POLYGON, geometry);
        serializers.put(FieldType.MULTIPOINT, geometry);
        serializers.put(FieldType.MULTILINESTRING, geometry);
        serializers.put(FieldType.MULTIPOLYGON, geometry);
        serializers.put(FieldType.GEOMETRYCOLLECTION, geometry);
        serializers.put(FieldType.UUID, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                long upper = readSignedVarLong(in);
                long lower = readSignedVarLong(in);
                return new java.util.UUID(upper, lower);
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                writeSignedVarLong(((java.util.UUID) field).getMostSignificantBits(), data);
                writeSignedVarLong(((java.util.UUID) field).getLeastSignificantBits(), data);
            }
        });
        final ValueSerializer bigInteger = new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                byte[] bytes = (byte[]) byteArray.read(in);
                return new BigInteger(bytes);
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                byte[] bytes = ((BigInteger) field).toByteArray();
                byteArray.write(bytes, data);
            }
        };
        serializers.put(FieldType.BIG_INTEGER, bigInteger);
        serializers.put(FieldType.BIG_DECIMAL, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                int scale = in.readInt();
                BigInteger intValue = (BigInteger) bigInteger.read(in);
                BigDecimal decValue = new BigDecimal(intValue, scale);
                return decValue;
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                BigDecimal d = (BigDecimal) field;
                int scale = d.scale();
                BigInteger i = d.unscaledValue();
                data.writeInt(scale);
                bigInteger.write(i, data);
            }
        });
        serializers.put(FieldType.DATETIME, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                long time = in.readLong();
                return new Date(time);
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                Date date = (Date) field;
                data.writeLong(date.getTime());
            }
        });
        serializers.put(FieldType.DATE, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                long time = in.readLong();
                return new java.sql.Date(time);
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                java.sql.Date date = (java.sql.Date) field;
                data.writeLong(date.getTime());
            }
        });
        serializers.put(FieldType.TIME, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                long time = in.readLong();
                return new java.sql.Time(time);
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                java.sql.Time time = (java.sql.Time) field;
                data.writeLong(time.getTime());
            }
        });
        serializers.put(FieldType.TIMESTAMP, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                long time = in.readLong();
                int nanos = in.readInt();
                java.sql.Timestamp timestamp = new java.sql.Timestamp(time);
                timestamp.setNanos(nanos);
                return timestamp;
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                java.sql.Timestamp timestamp = (java.sql.Timestamp) field;
                data.writeLong(timestamp.getTime());
                data.writeInt(timestamp.getNanos());
            }
        });
        serializers.put(FieldType.MAP, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                final int size = readUnsignedVarInt(in);

                Map<Object, Object> map = new HashMap<>();

                String key;
                byte fieldTag;
                FieldType fieldType;
                Object value;

                for (int i = 0; i < size; i++) {
                    key = (String) DataStreamValueSerializerV2.read(FieldType.STRING, in);

                    fieldTag = in.readByte();
                    fieldType = FieldType.valueOf(fieldTag);
                    value = DataStreamValueSerializerV2.read(fieldType, in);

                    map.put(key, value);
                }
                return map;
            }

            @SuppressWarnings("unchecked")
            @Override
            public void write(Object field, DataOutput out) throws IOException {

                Map<String, Object> map = (Map<String, Object>) field;

                final int size = map.size();

                writeUnsignedVarInt(size, out);

                for (Entry<String, Object> e : map.entrySet()) {
                    String key = e.getKey();
                    DataStreamValueSerializerV2.write(key, out);

                    Object value = e.getValue();
                    FieldType fieldType = FieldType.forValue(value);
                    out.writeByte(fieldType.getTag());
                    DataStreamValueSerializerV2.write(value, out);
                }
            }
        });
        serializers.put(FieldType.ENVELOPE_2D, new ValueSerializer() {
            @Override
            public Object read(DataInput in) throws IOException {
                double minx = in.readDouble();
                double miny = in.readDouble();
                double maxx = in.readDouble();
                double maxy = in.readDouble();
                return new Envelope(minx, maxx, miny, maxy);
            }

            @Override
            public void write(Object field, DataOutput data) throws IOException {
                Envelope e = (Envelope) field;
                data.writeDouble(e.getMinX());
                data.writeDouble(e.getMinY());
                data.writeDouble(e.getMaxX());
                data.writeDouble(e.getMaxY());
            }
        });
    }

    /**
     * Writes the passed attribute value in the specified data stream
     * 
     * @param opt
     * @param data
     */
    public static void write(Optional<Object> opt, DataOutput data) throws IOException {
        FieldType type = FieldType.forValue(opt);
        if (serializers.containsKey(type)) {
            serializers.get(type).write(opt.orNull(), data);
        } else {
            throw new IllegalArgumentException(
                    "The specified type (" + type + ") is not supported");
        }
    }

    public static void write(@Nullable Object value, DataOutput data) throws IOException {
        FieldType type = FieldType.forValue(value);
        write(type, value, data);
    }

    public static void write(FieldType type, @Nullable Object value, DataOutput data)
            throws IOException {
        ValueSerializer valueSerializer = serializers.get(type);
        if (null == valueSerializer) {
            throw new IllegalArgumentException(
                    "The specified type (" + type + ") is not supported");
        }
        valueSerializer.write(value, data);
    }

    /**
     * Reads an object of the specified type from the provided data stream
     * 
     * @param type
     * @param in
     * @return
     */
    public static Object read(FieldType type, DataInput in) throws IOException {
        if (serializers.containsKey(type)) {
            return serializers.get(type).read(in);
        } else {
            throw new IllegalArgumentException("The specified type is not supported");
        }
    }

    public static Geometry read(DataInput in, GeometryFactory geomFac) throws IOException {
        Geometry geom = geometry.read(in, geomFac);
        return geom;
    }
}
