/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;

/**
 * Format common v2.1, differs from {@link FormatCommonV2 v2} only in {@link RevFeature}
 * serialization by adding a tail with value offsets, to allow parsing of individual attribute
 * values.
 * <p>
 * Format for features:
 * 
 * <pre>
 * <code>
 * <HEADER><DATA>
 * 
 * <HEADER>:
 * - unsigned varint: number of attributes
 * - unsigned varint: size of <DATA>
 * - unsigned varint[number of attributes]: attribute offsets (starting form zero at <DATA>, not including the header)
 * 
 * <DATA>:
 * - byte[]: attribute data, as of {@link DataStreamValueSerializerV2#write(Object, DataOutput)}
 * </code>
 * </pre>
 */
public class FormatCommonV2_1 extends FormatCommonV2 {

    public FormatCommonV2_1() {
        super(DataStreamValueSerializerV2.INSTANCE);
    }

    public static final FormatCommonV2_1 INSTANCE = new FormatCommonV2_1();

    private static final class InternalByteArrayOutputStream extends ByteArrayOutputStream {
        InternalByteArrayOutputStream() {
            super(1024);
        }

        public byte[] intenal() {
            return super.buf;
        }
    }

    @Override
    public void writeFeature(RevFeature feature, DataOutput target) throws IOException {
        if (feature instanceof LazyRevFeature) {
            fastEncode((LazyRevFeature) feature, target);
            return;
        }
        try (InternalByteArrayOutputStream out = new InternalByteArrayOutputStream()) {
            final DataOutput data = ByteStreams.newDataOutput(out);
            final int attrCount = feature.size();
            final int[] dataOffsets = new int[attrCount];

            int offset = 0;
            for (int i = 0; i < attrCount; i++) {
                Object value = feature.get(i).orNull();
                FieldType type = FieldType.forValue(value);
                data.writeByte(type.getTag() & 0xFF);
                valueEncoder.encode(type, value, data);
                dataOffsets[i] = offset;
                offset = out.size();
            }

            // <HEADER>
            // - unsigned varint: number of attributes
            writeUnsignedVarInt(attrCount, target);

            // - unsigned varint: size of <DATA>
            final int dataSize = out.size();
            writeUnsignedVarInt(dataSize, target);

            // - unsigned varint[number of attributes]: attribute offsets (starting form zero at
            // <DATA>, not including the header)
            for (int i = 0; i < attrCount; i++) {
                writeUnsignedVarInt(dataOffsets[i], target);
            }

            // <DATA>
            target.write(out.intenal(), 0, dataSize);
        }
    }

    @VisibleForTesting
    void fastEncode(LazyRevFeature feature, DataOutput target) throws IOException {
        final int attrCount = feature.size();
        final byte[] data = feature.data;
        final int[] offsets = feature.offsets;
        // <HEADER>
        // - unsigned varint: number of attributes
        writeUnsignedVarInt(attrCount, target);

        // - unsigned varint: size of <DATA>
        final int dataSize = data.length;
        writeUnsignedVarInt(dataSize, target);

        // - unsigned varint[number of attributes]: attribute offsets (starting form zero at
        // <DATA>, not including the header)
        for (int i = 0; i < attrCount; i++) {
            writeUnsignedVarInt(offsets[i], target);
        }

        // <DATA>
        target.write(data);
    }

    @Override
    public RevFeature readFeature(@Nullable ObjectId id, DataInput in) throws IOException {
        // <HEADER>
        // - unsigned varint: number of attributes
        final int attrCount = readUnsignedVarInt(in);

        // - unsigned varint: size of <DATA>
        final int dataSize = readUnsignedVarInt(in);

        // - unsigned varint[number of attributes]: attribute offsets (starting form zero at
        // <DATA>, not including the header)
        final int[] dataOffsets = new int[attrCount];
        for (int i = 0; i < attrCount; i++) {
            dataOffsets[i] = readUnsignedVarInt(in);
        }

        // <DATA>
        byte[] data = new byte[dataSize];
        in.readFully(data);

        LazyRevFeature f = new LazyRevFeature(id, dataOffsets, data, valueEncoder);
        if (id == null) {
            id = HashObject.hashFeature(f.values());
            f.id = id;
        }
        return f;
    }

    static final class LazyRevFeature implements RevFeature {

        private final ValueSerializer valueParser;

        private final int[] offsets;

        private final byte[] data;

        private ObjectId id;

        LazyRevFeature(ObjectId id, int[] offsets, byte[] data, final ValueSerializer valueParser) {
            this.id = id;
            this.offsets = offsets;
            this.data = data;
            this.valueParser = valueParser;
        }

        @Override
        public ObjectId getId() {
            return id;
        }

        @Override
        public TYPE getType() {
            return TYPE.FEATURE;
        }

        @Override
        public ImmutableList<Optional<Object>> getValues() {
            ImmutableList.Builder<Optional<Object>> b = ImmutableList.builder();
            final int size = size();
            for (int i = 0; i < size; i++) {
                b.add(get(i));
            }
            return b.build();
        }

        List<Object> values() {
            final int size = size();
            List<Object> values = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                values.add(parse(i));
            }
            return values;
        }

        @Override
        public int size() {
            return offsets.length;
        }

        @Override
        public Optional<Object> get(final int index) {
            return Optional.fromNullable(parse(index));
        }

        @Override
        public Optional<Geometry> get(int index, GeometryFactory gf) {
            final int offset = offsets[index];
            final int tagValue = data[offset] & 0xFF;
            final FieldType type = FieldType.valueOf(tagValue);
            if (FieldType.NULL.equals(type)) {
                return Optional.absent();
            }
            DataInput in = ByteStreams.newDataInput(data, offset + 1);

            Geometry value;
            try {
                value = valueParser.readGeometry(in, gf);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return Optional.of(value);
        }

        @Override
        public void forEach(Consumer<Object> consumer) {
            final int size = size();
            for (int i = 0; i < size; i++) {
                Object value = parse(i);
                consumer.accept(value);
            }
        }

        private @Nullable Object parse(int index) {
            final int offset = offsets[index];
            final int tagValue = data[offset] & 0xFF;
            if (tagValue > 100) {
                throw new IllegalStateException();
            }
            final FieldType type = FieldType.valueOf(tagValue);
            final DataInput in = ByteStreams.newDataInput(data, offset + 1);
            @Nullable
            Object value;
            try {
                value = valueParser.decode(type, in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return value;
        }

        /**
         * Equality is based on id
         * 
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RevFeature)) {
                return false;
            }
            return getId().equals(((RevObject) o).getId());
        }

    }
}
