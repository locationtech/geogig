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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.crs.CRS;
import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.Name;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import lombok.Value;

public class FormatCommonV1 {

    public final static byte NUL = 0x00;

    public final static String readToMarker(DataInput in, byte marker) throws IOException {
        StringBuilder buff = new StringBuilder();
        byte b = in.readByte();
        while (b != marker) {
            buff.append((char) b);
            b = in.readByte();
        }
        return buff.toString();
    }

    public final static void requireHeader(DataInput in, String header) throws IOException {
        String s = readToMarker(in, NUL);
        if (!header.equals(s))
            throw new IllegalArgumentException(
                    "Expected header " + header + ", but actually got " + s);
    }

    public final static ObjectId readObjectId(DataInput in) throws IOException {
        return ObjectId.readFrom(in);
    }

    public static final byte COMMIT_TREE_REF = 0x01;

    public static final byte COMMIT_PARENT_REF = 0x02;

    public static final byte COMMIT_AUTHOR_PREFIX = 0x03;

    public static final byte COMMIT_COMMITTER_PREFIX = 0x04;

    /**
     * Constant for reading TREE objects. Indicates that the end of the tree object has been
     * reached.
     */
    public static final byte NO_MORE_NODES = 0x00;

    /**
     * Constant for reading TREE objects. Indicates that the next entry is a subtree node or a
     * features node.
     */
    public static final byte NODE = 0x01;

    /**
     * Constant for reading TREE objects. Indicates that the next entry is a bucket.
     */
    public static final byte BUCKET = 0x02;

    public static RevTag readTag(@Nullable ObjectId id, DataInput in) throws IOException {
        final ObjectId commitId = readObjectId(in);
        final String name = in.readUTF();
        final String message = in.readUTF();
        final RevPerson tagger = readRevPerson(in);
        return RevTag.builder().build(id, name, commitId, message, tagger);
    }

    public static void writeTag(RevTag tag, DataOutput out) throws IOException {
        tag.getCommitId().writeTo(out);
        out.writeUTF(tag.getName());
        out.writeUTF(tag.getMessage());
        writePerson(tag.getTagger(), out);
    }

    public static RevCommit readCommit(ObjectId id, DataInput in) throws IOException {
        byte tag = in.readByte();
        if (tag != COMMIT_TREE_REF) {
            throw new IllegalArgumentException("Commit should include a tree ref");
        }

        final ObjectId treeId = ObjectId.readFrom(in);
        final List<ObjectId> parentList = new ArrayList<>();

        while (true) {
            tag = in.readByte();
            if (tag != COMMIT_PARENT_REF) {
                break;
            } else {
                parentList.add(ObjectId.readFrom(in));
            }
        }

        if (tag != COMMIT_AUTHOR_PREFIX) {
            throw new IllegalArgumentException(
                    "Expected AUTHOR element following parent ids in commit");
        }

        final RevPerson author = readRevPerson(in);

        tag = in.readByte();
        if (tag != COMMIT_COMMITTER_PREFIX) {
            throw new IllegalArgumentException(
                    "Expected COMMITTER element following author in commit");
        }

        final RevPerson committer = readRevPerson(in);

        final String message = in.readUTF();

        return RevObjectFactory.defaultInstance().createCommit(id, treeId, parentList, author,
                committer, message);
    }

    public static final RevPerson readRevPerson(DataInput in) throws IOException {
        final String name = in.readUTF();
        final String email = in.readUTF();
        final long timestamp = in.readLong();
        final int tzOffset = in.readInt();
        return RevPerson.builder().build(name.length() == 0 ? null : name,
                email.length() == 0 ? null : email, timestamp, tzOffset);
    }

    public static final void writePerson(RevPerson person, DataOutput data) throws IOException {
        data.writeUTF(person.getName().orElse(""));
        data.writeUTF(person.getEmail().orElse(""));
        data.writeLong(person.getTimestamp());
        data.writeInt(person.getTimeZoneOffset());
    }

    public static RevTree readTree(@Nullable ObjectId id, DataInput in) throws IOException {
        final long size = in.readLong();
        final int treeCount = in.readInt();
        final List<Node> features = new ArrayList<>();
        final List<Node> trees = new ArrayList<>();
        final SortedSet<Bucket> buckets = new TreeSet<>();

        final int nFeatures = in.readInt();
        for (int i = 0; i < nFeatures; i++) {
            Node n = readNode(in);
            if (n.getType() != RevObject.TYPE.FEATURE) {
                throw new IllegalStateException("Non-feature node in tree's feature list.");
            }
            features.add(n);
        }

        final int nTrees = in.readInt();
        for (int i = 0; i < nTrees; i++) {
            Node n = readNode(in);
            if (n.getType() != RevObject.TYPE.TREE) {
                throw new IllegalStateException("Non-tree node in tree's subtree list.");
            }
            trees.add(n);
        }

        final int nBuckets = in.readInt();
        for (int i = 0; i < nBuckets; i++) {
            int index = in.readInt();
            Bucket bucket = readBucket(index, in);
            buckets.add(bucket);
        }
        if (null == id) {
            id = HashObject.hashTree(trees, features, buckets);
        }
        if (buckets.isEmpty()) {
            return RevObjectFactory.defaultInstance().createTree(id, size, trees, features);
        }
        return RevObjectFactory.defaultInstance().createTree(id, size, treeCount, buckets);
    }

    public static Node readNode(DataInput in) throws IOException {
        final String name = in.readUTF();
        final ObjectId objectId = ObjectId.readFrom(in);
        final ObjectId metadataId = ObjectId.readFrom(in);
        final RevObject.TYPE contentType = RevObject.TYPE.valueOf(in.readByte());
        final Envelope bbox = readBBox(in);
        Map<String, Object> extraData = DataStreamValueSerializerV1.INSTANCE.readMap(in);
        final Node node;
        node = RevObjectFactory.defaultInstance().createNode(name, objectId, metadataId,
                contentType, bbox, extraData);
        return node;
    }

    public static DiffEntry readDiff(DataInput in) throws IOException {
        boolean oldNode = in.readBoolean();
        NodeRef oldNodeRef = null;
        if (oldNode) {
            oldNodeRef = readNodeRef(in);
        }
        boolean newNode = in.readBoolean();
        NodeRef newNodeRef = null;
        if (newNode) {
            newNodeRef = readNodeRef(in);
        }

        return new DiffEntry(oldNodeRef, newNodeRef);
    }

    public static NodeRef readNodeRef(DataInput in) throws IOException {
        Node node = readNode(in);
        final ObjectId metadataId = ObjectId.readFrom(in);
        String parentPath = in.readUTF();
        return new NodeRef(node, parentPath, metadataId);
    }

    public static final Bucket readBucket(int index, DataInput in) throws IOException {
        ObjectId objectId = ObjectId.readFrom(in);
        Envelope bounds = readBBox(in);
        return RevObjectFactory.defaultInstance().createBucket(objectId, index, bounds);
    }

    @Nullable
    private static Envelope readBBox(DataInput in) throws IOException {
        final double minx = in.readDouble();
        if (Double.isNaN(minx)) {
            return null;
        }
        final double maxx = in.readDouble();
        final double miny = in.readDouble();
        final double maxy = in.readDouble();
        return new Envelope(minx, maxx, miny, maxy);
    }

    public static RevFeature readFeature(ObjectId id, DataInput in) throws IOException {
        final int count = in.readInt();
        final RevFeatureBuilder builder = RevFeature.builder();

        for (int i = 0; i < count; i++) {
            final byte fieldTag = in.readByte();
            final FieldType fieldType = FieldType.valueOf(fieldTag);
            Object value = DataStreamValueSerializerV1.INSTANCE.decode(fieldType, in);
            builder.addValue(value);
        }

        RevFeature built = builder.build();
        return built;
    }

    public static RevFeatureType readFeatureType(ObjectId id, DataInput in) throws IOException {
        Name name = readName(in);
        int propertyCount = in.readInt();
        List<PropertyDescriptor> attributes = new ArrayList<PropertyDescriptor>();
        for (int i = 0; i < propertyCount; i++) {
            attributes.add(readAttributeDescriptor(in));
        }
        FeatureType ftype = FeatureType.builder().name(name).descriptors(attributes).build();
        return RevFeatureType.builder().id(id).type(ftype).build();
    }

    private static Name readName(DataInput in) throws IOException {
        String namespace = in.readUTF();
        String localPart = in.readUTF();
        return Name.valueOf(namespace.length() == 0 ? null : namespace,
                localPart.length() == 0 ? null : localPart);
    }

    private static @Value @lombok.Builder class AttributeType {
        private Name name;

        private Class<?> binding;

        private CoordinateReferenceSystem crs;
    }

    private static AttributeType readAttributeType(DataInput in) throws IOException {
        final Name name = readName(in);
        final byte typeTag = in.readByte();
        final FieldType type = FieldType.valueOf(typeTag);
        CoordinateReferenceSystem crs = null;
        final Class<?> binding = type.getBinding();
        if (Geometry.class.isAssignableFrom(binding)) {
            final boolean isCRSCode = in.readBoolean(); // as opposed to a raw
                                                        // WKT string
            final String crsText = in.readUTF();
            if (isCRSCode) {
                crs = CRS.decode(crsText);
            } else {
                crs = CRS.fromWKT(crsText);
            }
            if (crs.isNull()) {
                crs = null;
            }
        }
        return AttributeType.builder().name(name).binding(binding).crs(crs).build();
    }

    private static PropertyDescriptor readAttributeDescriptor(DataInput in) throws IOException {
        final Name name = readName(in);
        final boolean nillable = in.readBoolean();
        final int minOccurs = in.readInt();
        final int maxOccurs = in.readInt();
        final AttributeType type = readAttributeType(in);

        return PropertyDescriptor.builder().name(name).typeName(type.getName())
                .binding(type.getBinding()).coordinateReferenceSystem(type.getCrs())
                .nillable(nillable).minOccurs(minOccurs).maxOccurs(maxOccurs).build();
    }

    public static void writeHeader(DataOutput data, String header) throws IOException {
        byte[] bytes = header.getBytes(Charset.forName("US-ASCII"));
        data.write(bytes);
        data.writeByte(NUL);
    }

    public static void writeBoundingBox(Envelope bbox, DataOutput data) throws IOException {
        if (bbox.isNull()) {
            data.writeDouble(Double.NaN);
        } else {
            data.writeDouble(bbox.getMinX());
            data.writeDouble(bbox.getMaxX());
            data.writeDouble(bbox.getMinY());
            data.writeDouble(bbox.getMaxY());
        }
    }

    public static void writeBucket(int index, Bucket bucket, DataOutput data) throws IOException {
        writeBucket(index, bucket, data, new Envelope());
    }

    public static void writeBucket(int index, Bucket bucket, DataOutput data, Envelope envBuff) {
        try {
            data.writeInt(index);
            bucket.getObjectId().writeTo(data);
            envBuff.setToNull();
            bucket.expand(envBuff);
            writeBoundingBox(envBuff, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeNode(Node node, DataOutput data) throws IOException {
        writeNode(node, data, new Envelope());
    }

    public static void writeNode(Node node, DataOutput data, Envelope envBuff) {
        try {
            data.writeUTF(node.getName());
            node.getObjectId().writeTo(data);
            node.getMetadataId().orElse(ObjectId.NULL).writeTo(data);
            int typeN = node.getType().value();
            data.writeByte(typeN);
            envBuff.setToNull();
            node.expand(envBuff);
            writeBoundingBox(envBuff, data);

            Map<String, Object> extraData = node.getExtraData();
            DataStreamValueSerializerV1.INSTANCE.writeMap(extraData, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeDiff(DiffEntry diff, DataOutput data) throws IOException {
        if (diff.getOldObject() == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            writeNodeRef(diff.getOldObject(), data);
        }
        if (diff.getNewObject() == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            writeNodeRef(diff.getNewObject(), data);
        }
    }

    public static void writeNodeRef(NodeRef nodeRef, DataOutput data) throws IOException {
        writeNode(nodeRef.getNode(), data);
        nodeRef.getMetadataId().writeTo(data);
        data.writeUTF(nodeRef.getParentPath());
    }
}
