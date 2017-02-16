/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_2;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.model.FieldType.BIG_DECIMAL;
import static org.locationtech.geogig.model.FieldType.BIG_INTEGER;
import static org.locationtech.geogig.model.FieldType.BOOLEAN_ARRAY;
import static org.locationtech.geogig.model.FieldType.BYTE_ARRAY;
import static org.locationtech.geogig.model.FieldType.CHAR_ARRAY;
import static org.locationtech.geogig.model.FieldType.DOUBLE_ARRAY;
import static org.locationtech.geogig.model.FieldType.FLOAT_ARRAY;
import static org.locationtech.geogig.model.FieldType.GEOMETRY;
import static org.locationtech.geogig.model.FieldType.GEOMETRYCOLLECTION;
import static org.locationtech.geogig.model.FieldType.INTEGER_ARRAY;
import static org.locationtech.geogig.model.FieldType.LINESTRING;
import static org.locationtech.geogig.model.FieldType.LONG_ARRAY;
import static org.locationtech.geogig.model.FieldType.MAP;
import static org.locationtech.geogig.model.FieldType.MULTILINESTRING;
import static org.locationtech.geogig.model.FieldType.MULTIPOINT;
import static org.locationtech.geogig.model.FieldType.MULTIPOLYGON;
import static org.locationtech.geogig.model.FieldType.POINT;
import static org.locationtech.geogig.model.FieldType.POLYGON;
import static org.locationtech.geogig.model.FieldType.SHORT_ARRAY;
import static org.locationtech.geogig.model.FieldType.STRING_ARRAY;
import static org.locationtech.geogig.model.FieldType.UUID;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.storage.datastream.DataStreamValueSerializerV2;
import org.locationtech.geogig.storage.datastream.ValueSerializer;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

/**
 * A {@link ValueSerializer} based off {@link DataStreamValueSerializerV2} that encodes string
 * values using the integer index provided by a supplied {@link StringTable} instead of writing the
 * strings inline.
 * <p>
 * The {@link StringTable} is provided, hence it shall be encoded/decoded by this class' client
 * code.
 */
class DataStreamValueSerializerV2_2 extends DataStreamValueSerializerV2 {

    final Supplier<StringTable> stringTable;

    DataStreamValueSerializerV2_2(Supplier<StringTable> stringTable) {
        this.stringTable = stringTable;
    }

    static ValueSerializer create(Supplier<StringTable> stringTable) {
        return new DataStreamValueSerializerV2_2(stringTable);
    }

    @Override
    public String readString(DataInput in) throws IOException {
        final int stIndex = readUnsignedVarInt(in);
        StringTable st = stringTable.get();
        String value = st.get(stIndex);
        return value;
    }

    @Override
    public void writeString(String value, DataOutput data) throws IOException {
        StringTable st = stringTable.get();
        final int stIndex = st.add(value);
        writeUnsignedVarInt(stIndex, data);
    }

    @Override
    public void writeMap(Map<String, Object> map, DataOutput out) throws IOException {
        checkNotNull(map);

        InternalDataOutput inlineData = new InternalDataOutput(128);
        InternalDataOutput deferredDataBuffer = new InternalDataOutput(128);

        final int size = map.size();
        writeUnsignedVarInt(size, inlineData);

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            final String key = entry.getKey();
            writeString(key, inlineData);

            final Object value = entry.getValue();
            final FieldType valueType = FieldType.forValue(value);
            final boolean isOffsetted = OFFSETTED_TYPES.contains(valueType);

            final int maskedFieldType;
            if (isOffsetted) {
                maskedFieldType = valueType.ordinal() | OFFSETTED_MASK;
            } else {
                maskedFieldType = valueType.ordinal();
            }

            inlineData.writeByte(maskedFieldType);
            if (isOffsetted) {
                int relativeValueOffset = deferredDataBuffer.size();
                encode(valueType, value, deferredDataBuffer);
                writeUnsignedVarInt(relativeValueOffset, inlineData);
            } else {
                super.encode(valueType, value, inlineData);
            }
        }

        final int inlineDataSize = inlineData.size();
        final int deferredDataSize = deferredDataBuffer.size();

        writeUnsignedVarInt(inlineDataSize, out);
        writeUnsignedVarInt(deferredDataSize, out);

        inlineData.writeTo(out);
        deferredDataBuffer.writeTo(out);
    }

    @Override
    public Map<String, Object> readMap(final DataInput input) throws IOException {
        final int inlineDataSize = readUnsignedVarInt(input);
        final int deferredDataSize = readUnsignedVarInt(input);

        byte[] inline = read(input, inlineDataSize);
        byte[] deferredData = read(input, deferredDataSize);

        DataInput in = ByteStreams.newDataInput(inline);

        Map<String, Supplier<Object>> map = null;
        try {
            final int size = readUnsignedVarInt(in);
            map = new HashMap<>();
            for (int i = 0; i < size; i++) {
                final String key = readString(in);
                final boolean isLazilyLoaded;
                final FieldType valueType;
                {
                    final int maskedType = in.readUnsignedByte();
                    final int unmaskedType;
                    isLazilyLoaded = OFFSETTED_MASK == (maskedType & OFFSETTED_MASK);
                    if (isLazilyLoaded) {
                        unmaskedType = maskedType & ~OFFSETTED_MASK;
                    } else {
                        unmaskedType = maskedType;
                    }
                    valueType = FieldType.valueOf(unmaskedType);
                }

                final Supplier<Object> valueSupplier;
                if (isLazilyLoaded) {
                    int relativeValueOffset = readUnsignedVarInt(in);
                    valueSupplier = new DefferredValueSupplier(deferredData, valueType,
                            relativeValueOffset);
                } else {
                    Object value = decode(valueType, in);
                    valueSupplier = () -> value;
                }
                map.put(key, valueSupplier);
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return Maps.transformValues(map, (s) -> s.get());
    }

    private byte[] read(DataInput in, int size) throws IOException {
        byte[] buff = new byte[size];
        in.readFully(buff);
        return buff;
    }

    private class DefferredValueSupplier implements Supplier<Object> {

        private final byte[] buffer;

        private final FieldType fieldType;

        private final int offset;

        public DefferredValueSupplier(byte[] buffer, FieldType fieldType,
                final int valueAbsoluteOffset) {
            this.buffer = buffer;
            this.fieldType = fieldType;
            this.offset = valueAbsoluteOffset;
        }

        @Override
        public Object get() {
            try {
                DataInput in = ByteStreams.newDataInput(buffer, offset);
                Object val = decode(fieldType, in);
                return val;
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    static final int OFFSETTED_MASK = 0b10000000;

    /**
     * The set of field types whose values are not encoded inline. The set can be changed without
     * affecting existing repositories, since existing fieldtype bitmasks will be respected.
     */
    private static final EnumSet<FieldType> OFFSETTED_TYPES = EnumSet.of(//
            BOOLEAN_ARRAY, //
            BYTE_ARRAY, //
            SHORT_ARRAY, //
            INTEGER_ARRAY, //
            LONG_ARRAY, //
            FLOAT_ARRAY, //
            DOUBLE_ARRAY, //
            STRING_ARRAY, //
            POINT, //
            LINESTRING, //
            POLYGON, //
            MULTIPOINT, //
            MULTILINESTRING, //
            MULTIPOLYGON, //
            GEOMETRYCOLLECTION, //
            GEOMETRY, //
            UUID, //
            BIG_INTEGER, //
            BIG_DECIMAL, //
            MAP, //
            CHAR_ARRAY//
    );

}
