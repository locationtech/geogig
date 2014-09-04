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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Integer.toBinaryString;
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
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.Nullable;

import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.type.BasicFeatureTypes;
import org.geotools.referencing.CRS;
import org.geotools.referencing.CRS.AxisOrder;
import org.geotools.referencing.wkt.Formattable;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevCommitImpl;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureImpl;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevPerson;
import org.locationtech.geogig.api.RevPersonImpl;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTagImpl;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeImpl;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.storage.FieldType;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.FeatureTypeFactory;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.filter.Filter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.math.DoubleMath;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

public class FormatCommonV2 {

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

    public final static ObjectId readObjectId(DataInput in) throws IOException {
        byte[] bytes = new byte[ObjectId.NUM_BYTES];
        in.readFully(bytes);
        return ObjectId.createNoClone(bytes);
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

    /**
     * The featuretype factory to use when calling code does not provide one.
     */
    private static final FeatureTypeFactory DEFAULT_FEATURETYPE_FACTORY = new SimpleFeatureTypeBuilder()
            .getFeatureTypeFactory();

    public static RevTag readTag(ObjectId id, DataInput in) throws IOException {
        final ObjectId commitId = readObjectId(in);
        final String name = in.readUTF();
        final String message = in.readUTF();
        final RevPerson tagger = readRevPerson(in);

        return new RevTagImpl(id, name, commitId, message, tagger);
    }

    public static void writeTag(RevTag tag, DataOutput out) throws IOException {
        out.write(tag.getCommitId().getRawValue());
        out.writeUTF(tag.getName());
        out.writeUTF(tag.getMessage());
        writePerson(tag.getTagger(), out);
    }

    public static void writeCommit(RevCommit commit, DataOutput data) throws IOException {
        data.write(commit.getTreeId().getRawValue());
        final int nParents = commit.getParentIds().size();
        writeUnsignedVarInt(nParents, data);
        for (ObjectId pId : commit.getParentIds()) {
            data.write(pId.getRawValue());
        }

        writePerson(commit.getAuthor(), data);
        writePerson(commit.getCommitter(), data);
        data.writeUTF(commit.getMessage());
    }

    public static RevCommit readCommit(ObjectId id, DataInput in) throws IOException {
        final ObjectId treeId = readObjectId(in);
        final int nParents = readUnsignedVarInt(in);
        final Builder<ObjectId> parentListBuilder = ImmutableList.builder();

        for (int i = 0; i < nParents; i++) {
            ObjectId parentId = readObjectId(in);
            parentListBuilder.add(parentId);
        }
        final RevPerson author = readRevPerson(in);
        final RevPerson committer = readRevPerson(in);
        final String message = in.readUTF();

        return new RevCommitImpl(id, treeId, parentListBuilder.build(), author, committer, message);
    }

    public static final RevPerson readRevPerson(DataInput in) throws IOException {
        final String name = in.readUTF();
        final String email = in.readUTF();
        final long timestamp = readUnsignedVarLong(in);
        final int tzOffset = readUnsignedVarInt(in);
        return new RevPersonImpl(name.length() == 0 ? null : name, email.length() == 0 ? null
                : email, timestamp, tzOffset);
    }

    public static final void writePerson(RevPerson person, DataOutput data) throws IOException {
        data.writeUTF(person.getName().or(""));
        data.writeUTF(person.getEmail().or(""));
        writeUnsignedVarLong(person.getTimestamp(), data);
        writeUnsignedVarInt(person.getTimeZoneOffset(), data);
    }

    public static void writeTree(RevTree tree, DataOutput data) throws IOException {

        writeUnsignedVarLong(tree.size(), data);
        writeUnsignedVarInt(tree.numTrees(), data);

        Envelope envBuff = new Envelope();

        final int nFeatures = tree.features().isPresent() ? tree.features().get().size() : 0;
        writeUnsignedVarInt(nFeatures, data);
        if (nFeatures > 0) {
            for (Node feature : tree.features().get()) {
                writeNode(feature, data, envBuff);
            }
        }
        final int nTrees = tree.trees().isPresent() ? tree.trees().get().size() : 0;
        writeUnsignedVarInt(nTrees, data);
        if (nTrees > 0) {
            for (Node subTree : tree.trees().get()) {
                writeNode(subTree, data, envBuff);
            }
        }

        final int nBuckets = tree.buckets().isPresent() ? tree.buckets().get().size() : 0;
        writeUnsignedVarInt(nBuckets, data);
        if (tree.buckets().isPresent()) {
            ImmutableSortedMap<Integer, Bucket> buckets = tree.buckets().get();
            for (Map.Entry<Integer, Bucket> bucket : buckets.entrySet()) {
                writeBucket(bucket.getKey(), bucket.getValue(), data, envBuff);
            }
        }
    }

    public static RevTree readTree(ObjectId id, DataInput in) throws IOException {
        final long size = readUnsignedVarLong(in);
        final int treeCount = readUnsignedVarInt(in);

        final ImmutableList.Builder<Node> featuresBuilder = new ImmutableList.Builder<Node>();
        final ImmutableList.Builder<Node> treesBuilder = new ImmutableList.Builder<Node>();
        final SortedMap<Integer, Bucket> buckets = new TreeMap<Integer, Bucket>();

        final int nFeatures = readUnsignedVarInt(in);
        for (int i = 0; i < nFeatures; i++) {
            Node n = readNode(in);
            checkState(RevObject.TYPE.FEATURE.equals(n.getType()),
                    "Non-feature node in tree's feature list.");
            featuresBuilder.add(n);
        }

        final int nTrees = readUnsignedVarInt(in);
        for (int i = 0; i < nTrees; i++) {
            Node n = readNode(in);
            checkState(RevObject.TYPE.TREE.equals(n.getType()),
                    "Non-tree node in tree's subtree list.");

            treesBuilder.add(n);
        }

        final int nBuckets = readUnsignedVarInt(in);
        for (int i = 0; i < nBuckets; i++) {
            int bucketIndex = readUnsignedVarInt(in);
            {
                Integer idx = Integer.valueOf(bucketIndex);
                checkState(!buckets.containsKey(idx), "duplicate bucket index: %s", idx);
                // checkState(bucketIndex < RevTree.MAX_BUCKETS, "Illegal bucket index: %s", idx);
            }
            Bucket bucket = readBucketBody(in);
            buckets.put(Integer.valueOf(bucketIndex), bucket);
        }
        checkState(nBuckets == buckets.size(), "expected %s buckets, got %s", nBuckets,
                buckets.size());
        ImmutableList<Node> trees = treesBuilder.build();
        ImmutableList<Node> features = featuresBuilder.build();
        checkArgument(buckets.isEmpty() || (trees.isEmpty() && features.isEmpty()),
                "Tree has mixed buckets and nodes; this is not supported.");

        if (trees.isEmpty() && features.isEmpty()) {
            return RevTreeImpl.createNodeTree(id, size, treeCount, buckets);
        }
        return RevTreeImpl.createLeafTree(id, size, features, trees);
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
        final ObjectId metadataId = readObjectId(in);
        String parentPath = in.readUTF();
        return new NodeRef(node, parentPath, metadataId);
    }

    public static void writeFeature(RevFeature feature, DataOutput data) throws IOException {
        ImmutableList<Optional<Object>> values = feature.getValues();

        writeUnsignedVarInt(values.size(), data);

        for (Optional<Object> field : values) {
            FieldType type = FieldType.forValue(field);
            data.writeByte(type.getTag());
            if (type != FieldType.NULL) {
                DataStreamValueSerializerV2.write(field, data);
            }
        }
    }

    public static RevFeature readFeature(ObjectId id, DataInput in) throws IOException {
        final int count = readUnsignedVarInt(in);
        final ImmutableList.Builder<Optional<Object>> builder = ImmutableList.builder();

        for (int i = 0; i < count; i++) {
            final byte fieldTag = in.readByte();
            final FieldType fieldType = FieldType.valueOf(fieldTag);
            Object value = DataStreamValueSerializerV2.read(fieldType, in);
            builder.add(Optional.fromNullable(value));
        }

        return new RevFeatureImpl(id, builder.build());
    }

    public static void writeHeader(DataOutput data, RevObject.TYPE header) throws IOException {
        data.writeByte(header.value());
    }

    public static TYPE readHeader(DataInput in) throws IOException {
        final int header = in.readByte() & 0xFF;
        checkState(header > -1 && header < 6,
                "Illegal RevObject type header: %s, must be between 0 and 4 inclusive",
                Integer.valueOf(header));
        final RevObject.TYPE type = TYPE.valueOf(header);
        return type;
    }

    public final static void requireHeader(DataInput in, RevObject.TYPE header) throws IOException {
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

    public static void writePointBoundingBox(double x, double y, DataOutput data)
            throws IOException {
        long x1 = toFixedPrecision(x);
        long y1 = toFixedPrecision(y);
        writeSignedVarLong(x1, data);
        writeSignedVarLong(y1, data);
    }

    public static Envelope readPointBoundingBox(DataInput in) throws IOException {
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

    public static void writeBucket(final int index, final Bucket bucket, DataOutput data,
            Envelope envBuff) throws IOException {

        writeUnsignedVarInt(index, data);

        data.write(bucket.id().getRawValue());
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
     */
    private static final Bucket readBucketBody(DataInput in) throws IOException {
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
        return Bucket.create(objectId, bounds);
    }

    public static void writeNode(Node node, DataOutput data) throws IOException {
        writeNode(node, data, new Envelope());
    }

    static final int BOUNDS_NULL_MASK = 0b00000;

    static final int BOUNDS_POINT_MASK = 0b01000;

    static final int BOUNDS_BOX2D_MASK = 0b10000;

    static final int METADATA_PRESENT_MASK = 0b100000;

    static final int METADATA_ABSENT_MASK = 0b000000;

    static final int METADATA_READ_MASK = 0b100000;

    static final int BOUNDS_READ_MASK = 0b011000;

    static final int TYPE_READ_MASK = 0b000111;

    public static void writeNode(Node node, DataOutput data, Envelope env) throws IOException {
        // Encode the node type and the bounds and metadata presence masks in one single byte:
        // - bits 1-3 for the object type (up to 8 types, there are only 5 and no plans to add more)
        // - bits 4-5 bits for the bounds mask
        // - bit 6 metadata id present(1) or absent(0)
        // - bits 7-8 unused

        final int nodeType = node.getType().value();
        final int boundsMask;
        final int metadataMask;

        env.setToNull();
        node.expand(env);
        if (env.isNull()) {
            boundsMask = BOUNDS_NULL_MASK;
        } else if (env.getWidth() == 0D && env.getHeight() == 0D) {
            boundsMask = BOUNDS_POINT_MASK;
        } else {
            boundsMask = BOUNDS_BOX2D_MASK;
        }

        metadataMask = node.getMetadataId().isPresent() ? METADATA_PRESENT_MASK
                : METADATA_ABSENT_MASK;

        // encode type and bounds mask together
        final int typeAndMasks = nodeType | boundsMask | metadataMask;

        data.writeByte(typeAndMasks);
        data.writeUTF(node.getName());
        data.write(node.getObjectId().getRawValue());
        if (metadataMask == METADATA_PRESENT_MASK) {
            data.write(node.getMetadataId().or(ObjectId.NULL).getRawValue());
        }
        if (BOUNDS_BOX2D_MASK == boundsMask) {
            writeBoundingBox(env.getMinX(), env.getMaxX(), env.getMinY(), env.getMaxY(), data);
        } else if (BOUNDS_POINT_MASK == boundsMask) {
            writePointBoundingBox(env.getMinX(), env.getMinY(), data);
        }
    }

    public static Node readNode(DataInput in) throws IOException {
        final int typeAndMasks = in.readByte() & 0xFF;
        final int nodeType = typeAndMasks & TYPE_READ_MASK;
        final int boundsMask = typeAndMasks & BOUNDS_READ_MASK;
        final int metadataMask = typeAndMasks & METADATA_READ_MASK;

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
            throw new IllegalStateException(String.format(
                    "Illegal bounds mask: %s, expected one of %s, %s, %s",
                    toBinaryString(boundsMask), toBinaryString(BOUNDS_NULL_MASK),
                    toBinaryString(BOUNDS_POINT_MASK), toBinaryString(BOUNDS_BOX2D_MASK)));
        }
        final Node node;
        node = Node.create(name, objectId, metadataId, contentType, bbox);
        return node;
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
        data.write(nodeRef.getMetadataId().getRawValue());
        data.writeUTF(nodeRef.getParentPath());
    }

    public static void writeFeatureType(RevFeatureType object, DataOutput data) throws IOException {
        writeName(object.getName(), data);

        ImmutableList<PropertyDescriptor> descriptors = object.sortedDescriptors();
        writeUnsignedVarInt(descriptors.size(), data);

        for (PropertyDescriptor desc : object.type().getDescriptors()) {
            writeProperty(desc, data);
        }
    }

    public static RevFeatureType readFeatureType(ObjectId id, DataInput in) throws IOException {
        return readFeatureType(id, in, DEFAULT_FEATURETYPE_FACTORY);
    }

    public static RevFeatureType readFeatureType(ObjectId id, DataInput in,
            FeatureTypeFactory typeFactory) throws IOException {

        Name name = readName(in);
        int propertyCount = readUnsignedVarInt(in);
        List<AttributeDescriptor> attributes = new ArrayList<AttributeDescriptor>();
        for (int i = 0; i < propertyCount; i++) {
            attributes.add(readAttributeDescriptor(in, typeFactory));
        }
        SimpleFeatureType ftype = typeFactory.createSimpleFeatureType(name, attributes, null,
                false, Collections.<Filter> emptyList(), BasicFeatureTypes.FEATURE, null);
        return new RevFeatureTypeImpl(id, ftype);
    }

    private static Name readName(DataInput in) throws IOException {
        String namespace = in.readUTF();
        String localPart = in.readUTF();
        return new NameImpl(namespace.length() == 0 ? null : namespace,
                localPart.length() == 0 ? null : localPart);
    }

    private static AttributeType readAttributeType(DataInput in, FeatureTypeFactory typeFactory)
            throws IOException {
        final Name name = readName(in);
        final byte typeTag = in.readByte();
        final FieldType type = FieldType.valueOf(typeTag);
        if (Geometry.class.isAssignableFrom(type.getBinding())) {
            final boolean isCRSCode = in.readBoolean(); // as opposed to a raw WKT string
            final String crsText = in.readUTF();
            final CoordinateReferenceSystem crs;
            try {
                if (isCRSCode) {
                    if ("urn:ogc:def:crs:EPSG::0".equals(crsText)) {
                        crs = null;
                    } else {
                        boolean forceLongitudeFirst = crsText.startsWith("EPSG:");
                        crs = CRS.decode(crsText, forceLongitudeFirst);
                    }
                } else {
                    crs = CRS.parseWKT(crsText);
                }
            } catch (FactoryException e) {
                throw new RuntimeException(e);
            }
            return typeFactory.createGeometryType(name, type.getBinding(), crs, false, false,
                    Collections.<Filter> emptyList(), null, null);
        } else {
            return typeFactory.createAttributeType(name, type.getBinding(), false, false,
                    Collections.<Filter> emptyList(), null, null);
        }
    }

    private static AttributeDescriptor readAttributeDescriptor(DataInput in,
            FeatureTypeFactory typeFactory) throws IOException {
        final Name name = readName(in);
        final boolean nillable = in.readBoolean();
        final int minOccurs = in.readInt();
        final int maxOccurs = in.readInt();
        final AttributeType type = readAttributeType(in, typeFactory);
        if (type instanceof GeometryType)
            return typeFactory.createGeometryDescriptor((GeometryType) type, name, minOccurs,
                    maxOccurs, nillable, null);
        else
            return typeFactory.createAttributeDescriptor(type, name, minOccurs, maxOccurs,
                    nillable, null);
    }

    private static void writeName(Name name, DataOutput data) throws IOException {
        final String ns = name.getNamespaceURI();
        final String lp = name.getLocalPart();
        data.writeUTF(ns == null ? "" : ns);
        data.writeUTF(lp);
    }

    private static void writePropertyType(PropertyType type, DataOutput data) throws IOException {
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
                    // check that what we are writing is actually a valid EPSG code and we will be
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

    private static void writeProperty(PropertyDescriptor attr, DataOutput data) throws IOException {
        writeName(attr.getName(), data);
        data.writeBoolean(attr.isNillable());
        data.writeInt(attr.getMinOccurs());
        data.writeInt(attr.getMaxOccurs());
        writePropertyType(attr.getType(), data);
    }

}
