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

import static java.lang.Integer.toBinaryString;
import static org.locationtech.geogig.base.Preconditions.checkState;
import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarLong;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.base.Preconditions;
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
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import com.google.common.math.DoubleMath;

import lombok.Value;

public class FormatCommonV2 {

    public final static byte NUL = 0x00;

    public static final FormatCommonV2 INSTANCE = new FormatCommonV2(
            DataStreamValueSerializerV2.INSTANCE);

    protected final ValueSerializer valueEncoder;

    public FormatCommonV2(ValueSerializer valueEncoder) {
        this.valueEncoder = valueEncoder;
    }

    public final String readToMarker(DataInput in, byte marker) throws IOException {
        StringBuilder buff = new StringBuilder();
        byte b = in.readByte();
        while (b != marker) {
            buff.append((char) b);
            b = in.readByte();
        }
        return buff.toString();
    }

    public final ObjectId readObjectId(DataInput in) throws IOException {
        return ObjectId.readFrom(in);
    }

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

    public RevTag readTag(@Nullable ObjectId id, DataInput in) throws IOException {
        final ObjectId commitId = readObjectId(in);
        final String name = in.readUTF();
        final String message = in.readUTF();
        final RevPerson tagger = readRevPerson(in);

        return RevTag.builder().build(id, name, commitId, message, tagger);
    }

    public void writeTag(RevTag tag, DataOutput out) throws IOException {
        tag.getCommitId().writeTo(out);
        out.writeUTF(tag.getName());
        out.writeUTF(tag.getMessage());
        writePerson(tag.getTagger(), out);
    }

    public void writeCommit(RevCommit commit, DataOutput data) throws IOException {
        commit.getTreeId().writeTo(data);
        final int nParents = commit.getParentIds().size();
        writeUnsignedVarInt(nParents, data);
        for (ObjectId pId : commit.getParentIds()) {
            pId.writeTo(data);
        }

        writePerson(commit.getAuthor(), data);
        writePerson(commit.getCommitter(), data);
        data.writeUTF(commit.getMessage());
    }

    public RevCommit readCommit(@Nullable ObjectId id, DataInput in) throws IOException {
        final ObjectId treeId = readObjectId(in);
        final int nParents = readUnsignedVarInt(in);
        final List<ObjectId> parents = new ArrayList<>();

        for (int i = 0; i < nParents; i++) {
            ObjectId parentId = readObjectId(in);
            parents.add(parentId);
        }
        final RevPerson author = readRevPerson(in);
        final RevPerson committer = readRevPerson(in);
        final String message = in.readUTF();

        RevCommit commit;
        if (id == null) {
            commit = RevCommit.builder().build(treeId, parents, author, committer, message);
        } else {
            commit = RevObjectFactory.defaultInstance().createCommit(id, treeId, parents, author,
                    committer, message);
        }
        return commit;
    }

    public final RevPerson readRevPerson(DataInput in) throws IOException {
        final String name = in.readUTF();
        final String email = in.readUTF();
        final long timestamp = readUnsignedVarLong(in);
        final int tzOffset = readUnsignedVarInt(in);
        return RevPerson.builder().build(name.length() == 0 ? null : name,
                email.length() == 0 ? null : email, timestamp, tzOffset);
    }

    public final void writePerson(RevPerson person, DataOutput data) throws IOException {
        data.writeUTF(person.getName().orElse(""));
        data.writeUTF(person.getEmail().orElse(""));
        writeUnsignedVarLong(person.getTimestamp(), data);
        writeUnsignedVarInt(person.getTimeZoneOffset(), data);
    }

    public void writeTree(RevTree tree, DataOutput data) throws IOException {

        writeUnsignedVarLong(tree.size(), data);
        writeUnsignedVarInt(tree.numTrees(), data);

        Envelope envBuff = new Envelope();

        final int nFeatures = tree.featuresSize();
        writeUnsignedVarInt(nFeatures, data);
        tree.forEachFeature(n -> writeNodeQuiet(n, data, envBuff));

        final int nTrees = tree.treesSize();
        writeUnsignedVarInt(nTrees, data);
        tree.forEachTree(n -> writeNodeQuiet(n, data, envBuff));

        final int nBuckets = tree.bucketsSize();
        writeUnsignedVarInt(nBuckets, data);
        tree.forEachBucket(bucket -> {
            try {
                writeBucket(bucket, data, envBuff);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public RevTree readTree(@Nullable ObjectId id, DataInput in) throws IOException {
        final long size = readUnsignedVarLong(in);
        final int treeCount = readUnsignedVarInt(in);

        final List<Node> features = new ArrayList<>();
        final List<Node> trees = new ArrayList<>();

        final int nFeatures = readUnsignedVarInt(in);
        for (int i = 0; i < nFeatures; i++) {
            Node n = readNode(in);
            checkState(RevObject.TYPE.FEATURE.equals(n.getType()),
                    "Non-feature node in tree's feature list.");
            features.add(n);
        }

        final int nTrees = readUnsignedVarInt(in);
        for (int i = 0; i < nTrees; i++) {
            Node n = readNode(in);
            checkState(RevObject.TYPE.TREE.equals(n.getType()),
                    "Non-tree node in tree's subtree list %s->%s.", n.getType(), n);

            trees.add(n);
        }

        final int nBuckets = readUnsignedVarInt(in);
        final SortedSet<Bucket> buckets;
        buckets = nBuckets > 0 ? new TreeSet<>() : Collections.emptySortedSet();
        for (int i = 0; i < nBuckets; i++) {
            int bucketIndex = readUnsignedVarInt(in);
            Bucket bucket = readBucketBody(bucketIndex, in);
            buckets.add(bucket);
        }
        checkState(nBuckets == buckets.size(), "expected %s buckets, got %s", nBuckets,
                buckets.size());

        if (id == null) {
            id = HashObject.hashTree(trees, features, buckets);
        }
        if (buckets.isEmpty()) {
            return RevObjectFactory.defaultInstance().createTree(id, size, trees, features);
        }
        return RevObjectFactory.defaultInstance().createTree(id, size, treeCount, buckets);
    }

    public DiffEntry readDiff(DataInput in) throws IOException {
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

    public NodeRef readNodeRef(DataInput in) throws IOException {
        Node node = readNode(in);
        final ObjectId metadataId = readObjectId(in);
        String parentPath = in.readUTF();
        return new NodeRef(node, parentPath, metadataId);
    }

    public void writeFeature(RevFeature feature, DataOutput data) throws IOException {

        writeUnsignedVarInt(feature.size(), data);

        for (int i = 0; i < feature.size(); i++) {
            Object field = feature.get(i).orElse(null);
            FieldType type = FieldType.forValue(field);
            data.writeByte(type.getTag());
            valueEncoder.encode(type, field, data);
        }
    }

    public RevFeature readFeature(@Nullable ObjectId id, DataInput in) throws IOException {
        final int count = readUnsignedVarInt(in);
        final RevFeatureBuilder builder = RevFeature.builder();

        for (int i = 0; i < count; i++) {
            final byte fieldTag = in.readByte();
            final FieldType fieldType = FieldType.valueOf(fieldTag);
            Object value = valueEncoder.decode(fieldType, in);
            builder.addValueNoCopy(value);
        }

        RevFeature built = builder.id(id).build();
        return built;
    }

    public void writeHeader(DataOutput data, RevObject.TYPE header) throws IOException {
        data.writeByte(header.value());
    }

    public TYPE readHeader(DataInput in) throws IOException {
        final int header = in.readByte() & 0xFF;
        checkState(header > -1 && header < 6,
                "Illegal RevObject type header: %s, must be between 0 and 4 inclusive",
                Integer.valueOf(header));
        final RevObject.TYPE type = TYPE.valueOf(header);
        return type;
    }

    public final void requireHeader(DataInput in, RevObject.TYPE header) throws IOException {
        int s = in.readByte() & 0xFF;
        if (header.value() != s) {
            throw new IllegalArgumentException(String.format(
                    "Expected header %s(%d), but actually got %d", header, header.value(), s));
        }
    }

    private static void writeBoundingBox(double minx, double maxx, double miny, double maxy,
            DataOutput data) throws IOException {

        long x1 = toFixedPrecision(minx, RoundingMode.HALF_DOWN);
        long y1 = toFixedPrecision(miny, RoundingMode.HALF_DOWN);
        long x2 = toFixedPrecision(maxx, RoundingMode.HALF_UP);
        long y2 = toFixedPrecision(maxy, RoundingMode.HALF_UP);

        writeSignedVarLong(x1, data);
        writeSignedVarLong(y1, data);
        writeSignedVarLong(x2, data);
        writeSignedVarLong(y2, data);
    }

    private static Envelope readBoundingBox(DataInput in) throws IOException {
        final long x1 = readSignedVarLong(in);
        final long y1 = readSignedVarLong(in);
        final long x2 = readSignedVarLong(in);
        final long y2 = readSignedVarLong(in);

        final double minx = toDoublePrecision(x1);
        final double maxx = toDoublePrecision(x2);
        final double miny = toDoublePrecision(y1);
        final double maxy = toDoublePrecision(y2);

        return new Envelope(minx, maxx, miny, maxy);
    }

    public void writePointBoundingBox(double x, double y, DataOutput data) throws IOException {
        long x1 = toFixedPrecision(x);
        long y1 = toFixedPrecision(y);
        writeSignedVarLong(x1, data);
        writeSignedVarLong(y1, data);
    }

    public Envelope readPointBoundingBox(DataInput in) throws IOException {
        final long x1 = readSignedVarLong(in);
        final long y1 = readSignedVarLong(in);

        final double x = toDoublePrecision(x1);
        final double y = toDoublePrecision(y1);
        return new Envelope(x, x, y, y);
    }

    private static final double FIXED_PRECISION_FACTOR = 10_000_000D;

    /**
     * Converts the requested coordinate from double to fixed precision.
     */
    private static long toFixedPrecision(double ordinate) {
        long fixedPrecisionOrdinate = Math.round(ordinate * FIXED_PRECISION_FACTOR);
        return fixedPrecisionOrdinate;
    }

    private static long toFixedPrecision(double ordinate, RoundingMode mode) {
        long fixedPrecisionOrdinate = DoubleMath.roundToLong(ordinate * FIXED_PRECISION_FACTOR,
                mode);
        return fixedPrecisionOrdinate;
    }

    /**
     * Converts the requested coordinate from fixed to double precision.
     */
    private static double toDoublePrecision(long fixedPrecisionOrdinate) {
        double ordinate = (double) fixedPrecisionOrdinate / FIXED_PRECISION_FACTOR;
        return ordinate;
    }

    protected void writeBucket(final Bucket bucket, DataOutput data, Envelope envBuff)
            throws IOException {

        writeUnsignedVarInt(bucket.getIndex(), data);

        bucket.getObjectId().writeTo(data);
        envBuff.setToNull();
        bucket.expand(envBuff);
        if (envBuff.isNull()) {
            data.writeByte(BOUNDS_NULL_MASK);
        } else if (envBuff.getWidth() == 0D && envBuff.getHeight() == 0D) {
            data.writeByte(BOUNDS_POINT_MASK);
            writePointBoundingBox(envBuff.getMinX(), envBuff.getMinY(), data);
        } else {
            data.writeByte(BOUNDS_BOX2D_MASK);
            writeBoundingBox(envBuff.getMinX(), envBuff.getMaxX(), envBuff.getMinY(),
                    envBuff.getMaxY(), data);
        }
    }

    /**
     * Reads a bucket body (i.e assumes the head unsigned int "index" has been read already)
     * 
     * @param bucketIndex
     */
    protected Bucket readBucketBody(int bucketIndex, DataInput in) throws IOException {
        ObjectId objectId = readObjectId(in);
        final int boundsMask = in.readByte() & 0xFF;
        @Nullable
        final Envelope bounds;
        if (BOUNDS_POINT_MASK == boundsMask) {
            bounds = readPointBoundingBox(in);
        } else if (BOUNDS_BOX2D_MASK == boundsMask) {
            bounds = readBoundingBox(in);
        } else {
            bounds = null;
        }
        return RevObjectFactory.defaultInstance().createBucket(objectId, bucketIndex, bounds);
    }

    public void writeNode(Node node, DataOutput data) throws IOException {
        writeNode(node, data, new Envelope());
    }

    static final int BOUNDS_NULL_MASK = 0b00000;

    static final int BOUNDS_POINT_MASK = 0b01000;

    static final int BOUNDS_BOX2D_MASK = 0b10000;

    static final int METADATA_PRESENT_MASK = 0b100000;

    static final int METADATA_ABSENT_MASK = 0b000000;

    static final int METADATA_READ_MASK = 0b100000;

    static final int EXTRA_DATA_PRESENT_MASK = 0b1000000;

    static final int EXTRA_DATA_ABSENT_MASK = 0b0000000;

    static final int EXTRA_DATA_READ_MASK = 0b1000000;

    static final int BOUNDS_READ_MASK = 0b011000;

    static final int TYPE_READ_MASK = 0b000111;

    private void writeNodeQuiet(Node node, DataOutput data, Envelope env) {
        try {
            writeNode(node, data, env);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeNode(Node node, DataOutput data, Envelope env) throws IOException {
        // Encode the node type and the bounds and metadata presence masks in one single byte:
        // - bits 1-3 for the object type (up to 8 types, there are only 5 and no plans to add more)
        // - bits 4-5 bits for the bounds mask
        // - bit 6 metadata id present(1) or absent(0)
        // - bit 7 extra data present(1) or absent(0)
        // - bit 8 unused

        final int nodeType = node.getType().value();
        final int boundsMask;
        final int metadataMask;
        final int extraDataMask;

        env.setToNull();
        node.expand(env);
        if (env.isNull()) {
            boundsMask = BOUNDS_NULL_MASK;
        } else if (env.getWidth() == 0D && env.getHeight() == 0D) {
            boundsMask = BOUNDS_POINT_MASK;
        } else {
            boundsMask = BOUNDS_BOX2D_MASK;
        }

        final Map<String, Object> extraData = node.getExtraData();

        metadataMask = node.getMetadataId().isPresent() ? METADATA_PRESENT_MASK
                : METADATA_ABSENT_MASK;

        extraDataMask = extraData.isEmpty() ? EXTRA_DATA_ABSENT_MASK : EXTRA_DATA_PRESENT_MASK;

        // encode type and bounds mask together
        final int typeAndMasks = nodeType | boundsMask | metadataMask | extraDataMask;

        data.writeByte(typeAndMasks);
        data.writeUTF(node.getName());
        node.getObjectId().writeTo(data);
        if (metadataMask == METADATA_PRESENT_MASK) {
            node.getMetadataId().orElse(ObjectId.NULL).writeTo(data);
        }
        if (BOUNDS_BOX2D_MASK == boundsMask) {
            writeBoundingBox(env.getMinX(), env.getMaxX(), env.getMinY(), env.getMaxY(), data);
        } else if (BOUNDS_POINT_MASK == boundsMask) {
            writePointBoundingBox(env.getMinX(), env.getMinY(), data);
        }
        if (extraDataMask == EXTRA_DATA_PRESENT_MASK) {
            valueEncoder.encode(extraData, data);
        }
    }

    @SuppressWarnings("unchecked")
    public Node readNode(DataInput in) throws IOException {
        final int typeAndMasks = in.readByte() & 0xFF;
        final int nodeType = typeAndMasks & TYPE_READ_MASK;
        final int boundsMask = typeAndMasks & BOUNDS_READ_MASK;
        final int metadataMask = typeAndMasks & METADATA_READ_MASK;
        final int extraDataMask = typeAndMasks & EXTRA_DATA_READ_MASK;

        final RevObject.TYPE contentType = RevObject.TYPE.valueOf(nodeType);
        final String name = in.readUTF();
        final ObjectId objectId = readObjectId(in);
        ObjectId metadataId = ObjectId.NULL;
        if (metadataMask == METADATA_PRESENT_MASK) {
            metadataId = readObjectId(in);
        }
        @Nullable
        final Envelope bbox;
        if (boundsMask == BOUNDS_NULL_MASK) {
            bbox = null;
        } else if (boundsMask == BOUNDS_POINT_MASK) {
            bbox = readPointBoundingBox(in);
        } else if (boundsMask == BOUNDS_BOX2D_MASK) {
            bbox = readBoundingBox(in);
        } else {
            throw new IllegalStateException(
                    String.format("Illegal bounds mask: %s, expected one of %s, %s, %s",
                            toBinaryString(boundsMask), toBinaryString(BOUNDS_NULL_MASK),
                            toBinaryString(BOUNDS_POINT_MASK), toBinaryString(BOUNDS_BOX2D_MASK)));
        }
        Map<String, Object> extraData = null;
        if (extraDataMask == EXTRA_DATA_PRESENT_MASK) {
            Object extra = valueEncoder.decode(FieldType.MAP, in);
            Preconditions.checkState(extra instanceof Map);
            extraData = (Map<String, Object>) extra;
        }

        final Node node;
        node = RevObjectFactory.defaultInstance().createNode(name, objectId, metadataId,
                contentType, bbox, extraData);
        return node;
    }

    public void writeDiff(DiffEntry diff, DataOutput data) throws IOException {
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

    public void writeNodeRef(NodeRef nodeRef, DataOutput data) throws IOException {
        writeNode(nodeRef.getNode(), data);
        nodeRef.metadataId().writeTo(data);
        data.writeUTF(nodeRef.getParentPath());
    }

    public void writeFeatureType(RevFeatureType object, DataOutput data) throws IOException {
        writeName(object.getName(), data);

        List<PropertyDescriptor> descriptors = object.descriptors();
        writeUnsignedVarInt(descriptors.size(), data);

        for (PropertyDescriptor desc : object.type().getDescriptors()) {
            writeProperty(desc, data);
        }
    }

    private static @Value @lombok.Builder class AttributeType {
        private Name name;

        private Class<?> binding;

        private CoordinateReferenceSystem crs;
    }

    public RevFeatureType readFeatureType(@Nullable ObjectId id, DataInput in) throws IOException {
        Name name = readName(in);
        int propertyCount = readUnsignedVarInt(in);
        List<PropertyDescriptor> attributes = new ArrayList<>();
        for (int i = 0; i < propertyCount; i++) {
            attributes.add(readAttributeDescriptor(in));
        }
        FeatureType featureType = FeatureType.builder().name(name).descriptors(attributes).build();
        return RevFeatureType.builder().id(id).type(featureType).build();
    }

    private static Name readName(DataInput in) throws IOException {
        String namespace = in.readUTF();
        String localPart = in.readUTF();
        return Name.valueOf(namespace, localPart);
    }

    private static AttributeType readAttributeType(DataInput in) throws IOException {
        final Name name = readName(in);
        final byte typeTag = in.readByte();
        final FieldType type = FieldType.valueOf(typeTag);
        final Class<?> binding = type.getBinding();
        CoordinateReferenceSystem crs = null;
        if (Geometry.class.isAssignableFrom(type.getBinding())) {
            final boolean isCRSCode = in.readBoolean(); // as opposed to a raw WKT string
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

    private static void writeName(Name name, DataOutput data) throws IOException {
        final String ns = name.getNamespaceURI();
        final String lp = name.getLocalPart();
        data.writeUTF(ns == null ? "" : ns);
        data.writeUTF(lp);
    }

    private static void writePropertyType(PropertyDescriptor type, DataOutput data)
            throws IOException {
        writeName(type.getTypeName(), data);
        data.writeByte(FieldType.forBinding(type.getBinding()).getTag());
        if (type.isGeometryDescriptor()) {
            CoordinateReferenceSystem crs = type.coordinateReferenceSystem();
            String srsName;
            if (crs.getSrsIdentifier() != null) {
                srsName = crs.getSrsIdentifier();
            } else {
                srsName = null;
            }

            if (srsName != null) {
                data.writeBoolean(true);// code only
                data.writeUTF(srsName);
            } else {
                final String wkt = crs.getWKT();
                data.writeBoolean(false);// WKT
                data.writeUTF(wkt);
            }
        }
    }

    private static void writeProperty(PropertyDescriptor attr, DataOutput data) throws IOException {
        writeName(attr.getName(), data);
        data.writeBoolean(attr.isNillable());
        data.writeInt(attr.getMinOccurs());
        data.writeInt(attr.getMaxOccurs());
        writePropertyType(attr, data);
    }

}
