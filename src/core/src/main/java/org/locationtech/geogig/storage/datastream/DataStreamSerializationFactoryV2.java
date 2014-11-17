/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.datastream;

import static org.locationtech.geogig.storage.datastream.FormatCommonV2.readCommit;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.readFeature;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.readFeatureType;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.readHeader;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.readTag;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.readTree;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.requireHeader;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.writeCommit;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.writeFeature;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.writeFeatureType;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.writeHeader;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.writeTag;
import static org.locationtech.geogig.storage.datastream.FormatCommonV2.writeTree;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.storage.ObjectReader;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.ObjectWriter;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

/**
 * Serialization factory for serial version 2
 */
public class DataStreamSerializationFactoryV2 implements ObjectSerializingFactory {

    public static final DataStreamSerializationFactoryV2 INSTANCE = new DataStreamSerializationFactoryV2();

    private final static ObjectReader<RevObject> OBJECT_READER = new ObjectReaderV2();

    private static final EnumMap<TYPE, Serializer<? extends RevObject>> serializers = Maps
            .newEnumMap(TYPE.class);
    static {
        serializers.put(TYPE.COMMIT, new CommitSerializer());
        serializers.put(TYPE.FEATURE, new FeatureSerializer());
        serializers.put(TYPE.FEATURETYPE, new FeatureTypeSerializer());
        serializers.put(TYPE.TAG, new TagSerializer());
        serializers.put(TYPE.TREE, new TreeSerializer());
    }

    @SuppressWarnings("unchecked")
    private static <T extends RevObject> Serializer<T> serializer(TYPE type) {
        Serializer<? extends RevObject> serializer = serializers.get(type);
        if (serializer == null) {
            throw new UnsupportedOperationException("No serializer for " + type);
        }
        return (Serializer<T>) serializer;
    }

    @Override
    public ObjectReader<RevCommit> createCommitReader() {
        return serializer(TYPE.COMMIT);
    }

    @Override
    public ObjectReader<RevTree> createRevTreeReader() {
        return serializer(TYPE.TREE);
    }

    @Override
    public ObjectReader<RevFeature> createFeatureReader() {
        return serializer(TYPE.FEATURE);
    }

    @Override
    public ObjectReader<RevFeature> createFeatureReader(Map<String, Serializable> hints) {
        return serializer(TYPE.FEATURE);
    }

    @Override
    public ObjectReader<RevFeatureType> createFeatureTypeReader() {
        return serializer(TYPE.FEATURETYPE);
    }

    @Override
    public <T extends RevObject> ObjectWriter<T> createObjectWriter(TYPE type) {
        return serializer(type);
    }

    @Override
    public <T extends RevObject> ObjectReader<T> createObjectReader(TYPE type) {
        return serializer(type);
    }

    @Override
    public ObjectReader<RevObject> createObjectReader() {
        return OBJECT_READER;
    }

    /**
     * Provides an interface for reading and writing objects.
     */
    private static abstract class Serializer<T extends RevObject> implements ObjectReader<T>,
            ObjectWriter<T> {

        private final TYPE header;

        Serializer(TYPE type) {
            this.header = type;
        }

        @Override
        public T read(ObjectId id, InputStream rawData) throws IllegalArgumentException {
            DataInput in = new DataInputStream(rawData);
            try {
                requireHeader(in, header);
                return readBody(id, in);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

        protected abstract T readBody(ObjectId id, DataInput in) throws IOException;

        /**
         * Writers must call
         * {@link FormatCommonV2#writeHeader(java.io.DataOutput, org.locationtech.geogig.api.RevObject.TYPE)},
         * readers must not, in order for {@link ObjectReaderV2} to be able of parsing the header
         * and call the appropriate read method.
         */
        @Override
        public void write(T object, OutputStream out) throws IOException {
            DataOutput data = new DataOutputStream(out);
            writeHeader(data, object.getType());
            writeBody(object, data);
        }

        public abstract void writeBody(T object, DataOutput data) throws IOException;
    }

    private static final class CommitSerializer extends Serializer<RevCommit> {

        CommitSerializer() {
            super(TYPE.COMMIT);
        }

        @Override
        public RevCommit readBody(ObjectId id, DataInput in) throws IOException {
            return readCommit(id, in);
        }

        @Override
        public void writeBody(RevCommit commit, DataOutput data) throws IOException {
            writeCommit(commit, data);
        }
    }

    private static final class FeatureSerializer extends Serializer<RevFeature> {

        FeatureSerializer() {
            super(TYPE.FEATURE);
        }

        @Override
        public RevFeature readBody(ObjectId id, DataInput in) throws IOException {
            return readFeature(id, in);
        }

        @Override
        public void writeBody(RevFeature feature, DataOutput data) throws IOException {
            writeFeature(feature, data);
        }
    }

    private static final class FeatureTypeSerializer extends Serializer<RevFeatureType> {

        FeatureTypeSerializer() {
            super(TYPE.FEATURETYPE);
        }

        @Override
        public RevFeatureType readBody(ObjectId id, DataInput in) throws IOException {
            return readFeatureType(id, in);
        }

        @Override
        public void writeBody(RevFeatureType object, DataOutput data) throws IOException {
            writeFeatureType(object, data);
        }
    }

    private static final class TagSerializer extends Serializer<RevTag> {

        TagSerializer() {
            super(TYPE.TAG);
        }

        @Override
        public RevTag readBody(ObjectId id, DataInput in) throws IOException {
            return readTag(id, in);
        }

        @Override
        public void writeBody(RevTag tag, DataOutput data) throws IOException {
            writeTag(tag, data);
        }
    }

    private static final class TreeSerializer extends Serializer<RevTree> {

        TreeSerializer() {
            super(TYPE.TREE);
        }

        @Override
        public RevTree readBody(ObjectId id, DataInput in) throws IOException {
            return readTree(id, in);
        }

        @Override
        public void writeBody(RevTree tree, DataOutput data) throws IOException {
            writeTree(tree, data);
        }
    }

    private static final class ObjectReaderV2 implements ObjectReader<RevObject> {
        @Override
        public RevObject read(ObjectId id, InputStream rawData) throws IllegalArgumentException {
            DataInput in = new DataInputStream(rawData);
            try {
                return readData(id, in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private RevObject readData(ObjectId id, DataInput in) throws IOException {
            final TYPE type = readHeader(in);
            Serializer<RevObject> serializer = DataStreamSerializationFactoryV2.serializer(type);
            RevObject object = serializer.readBody(id, in);
            return object;
        }
    }
}
