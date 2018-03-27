/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.storage.datastream.DataStreamValueSerializerV2;
import org.locationtech.geogig.storage.datastream.ValueSerializer;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Maps.EntryTransformer;

/**
 * A {@link ValueSerializer} based off {@link DataStreamValueSerializerV2} that encodes string
 * values using the integer index provided by a supplied {@link StringTable} instead of writing the
 * strings inline.
 * <p>
 * The {@link StringTable} is provided, hence it shall be encoded/decoded by this class' client
 * code.
 */
class DataStreamValueSerializerV2_3 extends DataStreamValueSerializerV2 {

    final Supplier<StringTable> stringTable;

    private DataStreamValueSerializerV2_3(Supplier<StringTable> stringTable) {
        this.stringTable = stringTable;
    }

    static DataStreamValueSerializerV2_3 create(Supplier<StringTable> stringTable) {
        return new DataStreamValueSerializerV2_3(stringTable);
    }

    public @Override String readString(DataInput in) throws IOException {
        final int stIndex = readUnsignedVarInt(in);
        StringTable st = stringTable.get();
        String value = st.get(stIndex);
        return value;
    }

    public @Override void writeString(String value, DataOutput data) throws IOException {
        StringTable st = stringTable.get();
        final int stIndex = st.add(value);
        writeUnsignedVarInt(stIndex, data);
    }

    public @Nullable Object findInMap(final DataInput in, final String mapKey) throws IOException {
        final StringTable st = stringTable.get();
        final int keyIndex = st.get(mapKey);
        if (-1 == keyIndex) {
            return null;
        }
        final int[] keyIndices = readIntArray(in);
        final int valueIndex = Arrays.binarySearch(keyIndices, keyIndex);
        Preconditions.checkState(valueIndex >= 0);
        final int[] offsets = readIntArray(in);
        final int valueOffset = offsets[valueIndex];
        final int dataSectionSize = readUnsignedVarInt(in);
        Preconditions.checkState(dataSectionSize > valueOffset);
        in.skipBytes(valueOffset);
        final int tagValue = in.readUnsignedByte();
        FieldType type = FieldType.valueOf(tagValue);
        return decode(type, in);
    }

    public @Override Map<String, Object> readMap(final DataInput in) throws IOException {
        final StringTable st = stringTable.get();
        final int[] keyIndices = readIntArray(in);
        if (keyIndices.length == 0) {
            return new HashMap<>();
        }
        final int[] offsets = readIntArray(in);
        final int dataSectionSize = readUnsignedVarInt(in);
        final byte[] rawData = new byte[dataSectionSize];
        in.readFully(rawData);

        Map<String, Integer> rawMap = new HashMap<>();
        for (int i = 0; i < keyIndices.length; i++) {
            String key = st.get(keyIndices[i]);
            rawMap.put(key, Integer.valueOf(offsets[i]));
        }

        EntryTransformer<String, Integer, Object> transformer = new EntryTransformer<String, Integer, Object>() {

            private final InternalInputStream byteStream = new InternalInputStream(rawData);

            public @Override Object transformEntry(String key, Integer dataOffset) {
                byteStream.setPosition(dataOffset.intValue());
                DataInput in = new DataInputStream(byteStream);
                try {
                    FieldType type = FieldType.valueOf(in.readUnsignedByte());
                    return decode(type, in);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return Maps.transformEntries(rawMap, transformer);
    }

    /**
     * Format:
     * 
     * <pre>
     * {@code
     * uvarint[]: map keys StringTable indices
     * uvarint[]: offsets of values starting at the end of this array
     * uvarint: data block size
     * byte[]: raw values, each one starts at the offset given by the array above
     * }
     * </pre>
     */
    public @Override void writeMap(Map<String, Object> map, DataOutput out) throws IOException {
        checkNotNull(map);

        final int size = map.size();
        final StringTable st = stringTable.get();

        int[] keys = new int[size];
        int[] offsets = new int[size];
        InternalDataOutput data = new InternalDataOutput(32 * size);
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            final int keyIndex = st.add(key);
            final int offset = data.size();
            keys[i] = keyIndex;
            offsets[i] = offset;

            FieldType type = FieldType.forValue(value);
            data.writeByte(type.getTag());
            encode(value, data);
            i++;
        }
        writeIntArray(keys, out);
        if (size > 0) {
            writeIntArray(offsets, out);
            writeUnsignedVarInt(data.size(), out);
            data.writeTo(out);
        }
    }

}
