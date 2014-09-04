/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import static org.locationtech.geogig.storage.datastream.FormatCommonV1.COMMIT_AUTHOR_PREFIX;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.COMMIT_COMMITTER_PREFIX;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.COMMIT_PARENT_REF;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.COMMIT_TREE_REF;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.NUL;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.readCommit;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.readFeature;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.readFeatureType;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.readTag;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.readToMarker;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.readTree;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.requireHeader;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.writeBucket;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.writeHeader;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.writeNode;

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

import org.geotools.referencing.CRS;
import org.geotools.referencing.CRS.AxisOrder;
import org.geotools.referencing.wkt.Formattable;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.storage.FieldType;
import org.locationtech.geogig.storage.ObjectReader;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.ObjectWriter;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Envelope;

public class DataStreamSerializationFactoryV1 implements ObjectSerializingFactory {

    /**
     * factory singleton
     */
    public static final DataStreamSerializationFactoryV1 INSTANCE = new DataStreamSerializationFactoryV1();

    private final static ObjectReader<RevObject> OBJECT_READER = new ObjectReaderV1();

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

    private static interface Serializer<T extends RevObject> extends ObjectReader<T>,
            ObjectWriter<T> {
        //
    }

    private static class CommitSerializer implements Serializer<RevCommit> {
        @Override
        public RevCommit read(ObjectId id, InputStream rawData) throws IllegalArgumentException {
            DataInput in = new DataInputStream(rawData);
            try {
                requireHeader(in, "commit");
                return readCommit(id, in);
            } catch (IOException e) {
                Throwables.propagate(e);
            }
            throw new IllegalStateException(
                    "Unexpected state: neither succeeded nor threw exception while trying to read commit "
                            + id);
        }

        @Override
        public void write(RevCommit commit, OutputStream out) throws IOException {
            DataOutput data = new DataOutputStream(out);
            FormatCommonV1.writeHeader(data, "commit");
            data.writeByte(COMMIT_TREE_REF);
            data.write(commit.getTreeId().getRawValue());
            for (ObjectId pId : commit.getParentIds()) {
                data.writeByte(COMMIT_PARENT_REF);
                data.write(pId.getRawValue());
            }
            data.writeByte(COMMIT_AUTHOR_PREFIX);
            FormatCommonV1.writePerson(commit.getAuthor(), data);
            data.writeByte(COMMIT_COMMITTER_PREFIX);
            FormatCommonV1.writePerson(commit.getCommitter(), data);
            data.writeUTF(commit.getMessage());
        }
    }

    private static class FeatureSerializer implements Serializer<RevFeature> {

        @Override
        public RevFeature read(ObjectId id, InputStream rawData) throws IllegalArgumentException {
            DataInput in = new DataInputStream(rawData);
            try {
                requireHeader(in, "feature");
                return readFeature(id, in);
            } catch (IOException e) {
                Throwables.propagate(e);
            }
            throw new IllegalStateException(
                    "Didn't expect to reach end of FeatureReader.read(); We should have returned or thrown an error before this point.");
        }

        @Override
        public void write(RevFeature feature, OutputStream out) throws IOException {
            DataOutput data = new DataOutputStream(out);
            writeHeader(data, "feature");
            data.writeInt(feature.getValues().size());
            for (Optional<Object> field : feature.getValues()) {
                FieldType type = FieldType.forValue(field);
                data.writeByte(type.getTag());
                if (type != FieldType.NULL) {
                    DataStreamValueSerializerV1.write(field, data);
                }
            }
        }
    }

    private static class FeatureTypeSerializer implements Serializer<RevFeatureType> {

        @Override
        public RevFeatureType read(ObjectId id, InputStream rawData)
                throws IllegalArgumentException {
            DataInput in = new DataInputStream(rawData);
            try {
                requireHeader(in, "featuretype");
                return readFeatureType(id, in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void write(RevFeatureType object, OutputStream out) throws IOException {
            DataOutput data = new DataOutputStream(out);
            writeHeader(data, "featuretype");
            writeName(object.getName(), data);
            data.writeInt(object.sortedDescriptors().size());
            for (PropertyDescriptor desc : object.type().getDescriptors()) {
                writeProperty(desc, data);
            }
        }

        private void writeName(Name name, DataOutput data) throws IOException {
            final String ns = name.getNamespaceURI();
            final String lp = name.getLocalPart();
            data.writeUTF(ns == null ? "" : ns);
            data.writeUTF(lp == null ? "" : lp);
        }

        private void writePropertyType(PropertyType type, DataOutput data) throws IOException {
            writeName(type.getName(), data);
            data.writeByte(FieldType.forBinding(type.getBinding()).getTag());
            if (type instanceof GeometryType) {
                GeometryType gType = (GeometryType) type;
                CoordinateReferenceSystem crs = gType.getCoordinateReferenceSystem();
                String srsName;
                if (crs == null) {
                    srsName = "urn:ogc:def:crs:EPSG::0";
                } else {
                    final boolean longitudeFirst = CRS.getAxisOrder(crs, false) == AxisOrder.EAST_NORTH;
                    final boolean codeOnly = true;
                    String crsCode = CRS.toSRS(crs, codeOnly);
                    if (crsCode != null) {
                        srsName = (longitudeFirst ? "EPSG:" : "urn:ogc:def:crs:EPSG::") + crsCode;
                        // check that what we are writing is actually a valid EPSG code and we will
                        // be
                        // able to decode it later. If not, we will use WKT instead
                        try {
                            CRS.decode(srsName, longitudeFirst);
                        } catch (NoSuchAuthorityCodeException e) {
                            srsName = null;
                        } catch (FactoryException e) {
                            srsName = null;
                        }
                    } else {
                        srsName = null;
                    }
                }
                if (srsName != null) {
                    data.writeBoolean(true);
                    data.writeUTF(srsName);
                } else {
                    final String wkt;
                    if (crs instanceof Formattable) {
                        wkt = ((Formattable) crs).toWKT(Formattable.SINGLE_LINE);
                    } else {
                        wkt = crs.toWKT();
                    }
                    data.writeBoolean(false);
                    data.writeUTF(wkt);
                }
            }
        }

        private void writeProperty(PropertyDescriptor attr, DataOutput data) throws IOException {
            writeName(attr.getName(), data);
            data.writeBoolean(attr.isNillable());
            data.writeInt(attr.getMinOccurs());
            data.writeInt(attr.getMaxOccurs());
            writePropertyType(attr.getType(), data);
        }
    }

    private static class TagSerializer implements Serializer<RevTag> {
        public RevTag read(ObjectId id, InputStream in) {
            DataInput data = new DataInputStream(in);
            try {
                FormatCommonV1.requireHeader(data, "tag");
                return FormatCommonV1.readTag(id, data);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }

        public void write(RevTag tag, OutputStream out) {
            final DataOutput data = new DataOutputStream(out);
            try {
                FormatCommonV1.writeHeader(data, "tag");
                FormatCommonV1.writeTag(tag, data);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private static class TreeSerializer implements Serializer<RevTree> {

        @Override
        public RevTree read(ObjectId id, InputStream rawData) throws IllegalArgumentException {
            DataInput in = new DataInputStream(rawData);
            try {
                requireHeader(in, "tree");
                return readTree(id, in);
            } catch (IOException e) {
                Throwables.propagate(e);
            }
            throw new IllegalStateException(
                    "Unexpected state: neither succeeded nor threw exception while trying to read commit "
                            + id);
        }

        @Override
        public void write(RevTree tree, OutputStream out) throws IOException {
            DataOutput data = new DataOutputStream(out);
            writeHeader(data, "tree");
            data.writeLong(tree.size());
            data.writeInt(tree.numTrees());

            Envelope envBuff = new Envelope();

            if (tree.features().isPresent()) {
                data.writeInt(tree.features().get().size());
                ImmutableList<Node> features = tree.features().get();
                for (Node feature : features) {
                    writeNode(feature, data, envBuff);
                }
            } else {
                data.writeInt(0);
            }
            if (tree.trees().isPresent()) {
                data.writeInt(tree.trees().get().size());
                ImmutableList<Node> subTrees = tree.trees().get();
                for (Node subTree : subTrees) {
                    writeNode(subTree, data, envBuff);
                }
            } else {
                data.writeInt(0);
            }
            if (tree.buckets().isPresent()) {
                data.writeInt(tree.buckets().get().size());
                ImmutableSortedMap<Integer, Bucket> buckets = tree.buckets().get();
                for (Map.Entry<Integer, Bucket> bucket : buckets.entrySet()) {
                    writeBucket(bucket.getKey(), bucket.getValue(), data, envBuff);
                }
            } else {
                data.writeInt(0);
            }
        }
    }

    private static class ObjectReaderV1 implements org.locationtech.geogig.storage.ObjectReader<RevObject> {
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
            String header = readToMarker(in, NUL);
            if ("commit".equals(header))
                return readCommit(id, in);
            else if ("tree".equals(header))
                return readTree(id, in);
            else if ("feature".equals(header))
                return readFeature(id, in);
            else if ("featuretype".equals(header))
                return readFeatureType(id, in);
            else if ("tag".equals(header))
                return readTag(id, in);
            else
                throw new IllegalArgumentException("Unrecognized object header: " + header);
        }
    }
}
