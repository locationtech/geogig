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

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.impl.ObjectReader;
import org.locationtech.geogig.storage.impl.ObjectWriter;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;

/**
 * Serialization factory for serial version 2
 */
public class DataStreamRevObjectSerializerV2 implements RevObjectSerializer {

    public static final DataStreamRevObjectSerializerV2 INSTANCE = new DataStreamRevObjectSerializerV2();

    private final FormatCommonV2 format;

    @SuppressWarnings("unchecked")
    private final Serializer<? extends RevObject>[] serializers = new Serializer[TYPE
            .values().length];

    public DataStreamRevObjectSerializerV2() {
        this(FormatCommonV2.INSTANCE);
    }

    protected DataStreamRevObjectSerializerV2(FormatCommonV2 format) {
        Preconditions.checkNotNull(format);
        this.format = format;
        serializers[TYPE.COMMIT.ordinal()] = new CommitSerializer(format);
        serializers[TYPE.FEATURE.ordinal()] = new FeatureSerializer(format);
        serializers[TYPE.FEATURETYPE.ordinal()] = new FeatureTypeSerializer(format);
        serializers[TYPE.TAG.ordinal()] = new TagSerializer(format);
        serializers[TYPE.TREE.ordinal()] = new TreeSerializer(format);
    }

    public RevObject read(InputStream rawData) throws IOException {
        return read(null, rawData);
    }

    @Override
    public RevObject read(@Nullable ObjectId id, InputStream rawData) throws IOException {
        return readInternal(id, newDataInput(rawData));
    }

    private DataInput newDataInput(InputStream in) {
        if (in instanceof ByteArrayInputStream) {
            return ByteStreams.newDataInput((ByteArrayInputStream) in);
        }
        return new DataInputStream(in);
    }

    @Override
    public RevObject read(@Nullable ObjectId id, byte[] data, int offset, int length)
            throws IOException {
        return readInternal(id, ByteStreams.newDataInput(data, offset));
    }

    private RevObject readInternal(@Nullable ObjectId id, DataInput in) throws IOException {
        final TYPE type = format.readHeader(in);
        Serializer<RevObject> serializer = serializer(type);
        RevObject object = serializer.readBody(id, in);
        return object;
    }

    @Override
    public void write(RevObject o, OutputStream out) throws IOException {
        serializer(o.getType()).write(o, out);
    }

    @SuppressWarnings("unchecked")
    private <T extends RevObject> Serializer<T> serializer(TYPE type) {
        Serializer<? extends RevObject> serializer = serializers[type.ordinal()];
        return (Serializer<T>) serializer;
    }

    /**
     * Provides an interface for reading and writing objects.
     */
    private static abstract class Serializer<T extends RevObject>
            implements ObjectReader<T>, ObjectWriter<T> {

        private final TYPE header;

        protected final FormatCommonV2 format;

        Serializer(TYPE type, FormatCommonV2 format) {
            this.header = type;
            this.format = format;
        }

        @Override
        public T read(ObjectId id, InputStream rawData) throws IllegalArgumentException {
            DataInput in = new DataInputStream(rawData);
            try {
                format.requireHeader(in, header);
                return readBody(id, in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        protected abstract T readBody(@Nullable ObjectId id, DataInput in) throws IOException;

        /**
         * Writers must call
         * {@link FormatCommonV2#writeHeader(java.io.DataOutput, org.locationtech.geogig.model.RevObject.TYPE)}
         * , readers must not, in order for {@link ObjectReaderV2} to be able of parsing the header
         * and call the appropriate read method.
         */
        @Override
        public void write(T object, OutputStream out) throws IOException {
            DataOutputStream data = new DataOutputStream(out);
            try {
                format.writeHeader(data, object.getType());
                writeBody(object, data);
            } finally {
                data.flush();
            }
        }

        public abstract void writeBody(T object, DataOutput data) throws IOException;
    }

    private static final class CommitSerializer extends Serializer<RevCommit> {

        CommitSerializer(FormatCommonV2 format) {
            super(TYPE.COMMIT, format);
        }

        @Override
        public RevCommit readBody(@Nullable ObjectId id, DataInput in) throws IOException {
            return format.readCommit(id, in);
        }

        @Override
        public void writeBody(RevCommit commit, DataOutput data) throws IOException {
            format.writeCommit(commit, data);
        }
    }

    private static final class FeatureSerializer extends Serializer<RevFeature> {

        FeatureSerializer(FormatCommonV2 format) {
            super(TYPE.FEATURE, format);
        }

        @Override
        public RevFeature readBody(@Nullable ObjectId id, DataInput in) throws IOException {
            return format.readFeature(id, in);
        }

        @Override
        public void writeBody(RevFeature feature, DataOutput data) throws IOException {
            format.writeFeature(feature, data);
        }
    }

    private static final class FeatureTypeSerializer extends Serializer<RevFeatureType> {

        FeatureTypeSerializer(FormatCommonV2 format) {
            super(TYPE.FEATURETYPE, format);
        }

        @Override
        public RevFeatureType readBody(@Nullable ObjectId id, DataInput in) throws IOException {
            return format.readFeatureType(id, in);
        }

        @Override
        public void writeBody(RevFeatureType object, DataOutput data) throws IOException {
            format.writeFeatureType(object, data);
        }
    }

    private static final class TagSerializer extends Serializer<RevTag> {

        TagSerializer(FormatCommonV2 format) {
            super(TYPE.TAG, format);
        }

        @Override
        public RevTag readBody(@Nullable ObjectId id, DataInput in) throws IOException {
            return format.readTag(id, in);
        }

        @Override
        public void writeBody(RevTag tag, DataOutput data) throws IOException {
            format.writeTag(tag, data);
        }
    }

    private static final class TreeSerializer extends Serializer<RevTree> {

        TreeSerializer(FormatCommonV2 format) {
            super(TYPE.TREE, format);
        }

        @Override
        public RevTree readBody(@Nullable ObjectId id, DataInput in) throws IOException {
            return format.readTree(id, in);
        }

        @Override
        public void writeBody(RevTree tree, DataOutput data) throws IOException {
            format.writeTree(tree, data);
        }
    }

    @Override
    public String getDisplayName() {
        return "Binary 2.0";
    }
}
