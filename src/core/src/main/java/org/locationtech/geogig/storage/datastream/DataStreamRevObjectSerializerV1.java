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

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumMap;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.referencing.CRS;
import org.geotools.referencing.CRS.AxisOrder;
import org.geotools.referencing.wkt.Formattable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.impl.ObjectReader;
import org.locationtech.geogig.storage.impl.ObjectWriter;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

public class DataStreamRevObjectSerializerV1 implements RevObjectSerializer {

    /**
     * factory singleton
     */
    public static final DataStreamRevObjectSerializerV1 INSTANCE = new DataStreamRevObjectSerializerV1();

    private static final EnumMap<TYPE, Serializer<? extends RevObject>> serializers = Maps
            .newEnumMap(TYPE.class);
    static {
        serializers.put(TYPE.COMMIT, new CommitSerializer());
        serializers.put(TYPE.FEATURE, new FeatureSerializer());
        serializers.put(TYPE.FEATURETYPE, new FeatureTypeSerializer());
        serializers.put(TYPE.TAG, new TagSerializer());
        serializers.put(TYPE.TREE, new TreeSerializer());
    }

    @Override
    public void write(RevObject o, OutputStream out) throws IOException {
        serializer(o.getType()).write(o, out);
    }

    @Override
    public RevObject read(@Nullable ObjectId id, byte[] data, int offset, int length)
            throws IOException {
        return read(id, new ByteArrayInputStream(data, offset, length));
    }

    @Override
    public RevObject read(ObjectId id, InputStream rawData) throws IOException {
        DataInput in = new DataInputStream(rawData);
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

    @SuppressWarnings("unchecked")
    private static <T extends RevObject> Serializer<T> serializer(TYPE type) {
        Serializer<? extends RevObject> serializer = serializers.get(type);
        if (serializer == null) {
            throw new UnsupportedOperationException("No serializer for " + type);
        }
        return (Serializer<T>) serializer;
    }

    private static interface Serializer<T extends RevObject>
            extends ObjectReader<T>, ObjectWriter<T> {
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
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void write(RevCommit commit, OutputStream out) throws IOException {
            DataOutputStream data = new DataOutputStream(out);
            try {
                FormatCommonV1.writeHeader(data, "commit");
                data.writeByte(COMMIT_TREE_REF);
                commit.getTreeId().writeTo(data);
                for (ObjectId pId : commit.getParentIds()) {
                    data.writeByte(COMMIT_PARENT_REF);
                    pId.writeTo(data);
                }
                data.writeByte(COMMIT_AUTHOR_PREFIX);
                FormatCommonV1.writePerson(commit.getAuthor(), data);
                data.writeByte(COMMIT_COMMITTER_PREFIX);
                FormatCommonV1.writePerson(commit.getCommitter(), data);
                data.writeUTF(commit.getMessage());
            } finally {
                data.flush();
            }
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
                throw new RuntimeException(e);
            }
        }

        @Override
        public void write(RevFeature feature, OutputStream out) throws IOException {
            DataOutputStream data = new DataOutputStream(out);
            try {
                writeHeader(data, "feature");
                data.writeInt(feature.size());
                for (Optional<Object> field : feature.getValues()) {
                    Object value = field.orNull();
                    FieldType type = FieldType.forValue(value);
                    data.writeByte(type.getTag());
                    DataStreamValueSerializerV1.INSTANCE.encode(type, value, data);
                }
            } finally {
                data.flush();
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
            DataOutputStream data = new DataOutputStream(out);
            try {
                writeHeader(data, "featuretype");
                writeName(object.getName(), data);
                data.writeInt(object.descriptors().size());
                for (PropertyDescriptor desc : object.type().getDescriptors()) {
                    writeProperty(desc, data);
                }
            } finally {
                data.flush();
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
                    srsName = RevObjects.NULL_CRS_IDENTIFIER;
                } else {
                    final boolean longitudeFirst = CRS.getAxisOrder(crs,
                            false) == AxisOrder.EAST_NORTH;
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
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void write(RevTag tag, OutputStream out) throws IOException {
            final DataOutputStream data = new DataOutputStream(out);
            try {
                FormatCommonV1.writeHeader(data, "tag");
                FormatCommonV1.writeTag(tag, data);
            } finally {
                data.flush();
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
                throw new RuntimeException(e);
            }
        }

        @Override
        public void write(RevTree tree, OutputStream out) throws IOException {
            DataOutputStream data = new DataOutputStream(out);
            try {
                writeHeader(data, "tree");
                data.writeLong(tree.size());
                data.writeInt(tree.numTrees());

                Envelope envBuff = new Envelope();

                data.writeInt(tree.features().size());
                tree.features().forEach((feature) -> writeNode(feature, data, envBuff));

                data.writeInt(tree.trees().size());
                tree.trees().forEach((subTree) -> writeNode(subTree, data, envBuff));

                data.writeInt(tree.bucketsSize());
                tree.getBuckets()
                        .forEach(bucket -> writeBucket(bucket.getIndex(), bucket, data, envBuff));
            } finally {
                data.flush();
            }
        }
    }

    @Override
    public String getDisplayName() {
        return "Binary 1.0";
    }
}
