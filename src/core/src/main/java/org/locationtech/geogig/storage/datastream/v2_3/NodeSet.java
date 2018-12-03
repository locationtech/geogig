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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.v2_3.InternalDataOutput.stream;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.storage.datastream.Varints;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class NodeSet {

    static final NodeSet EMPTY_FEATURES = new NodeSet(NodesetHeader.EMPTY, DataBuffer.EMTPY,
            TYPE.FEATURE);

    static final NodeSet EMPTY_TREES = new NodeSet(NodesetHeader.EMPTY, DataBuffer.EMTPY,
            TYPE.TREE);

    final NodesetHeader header;

    final DataBuffer data;

    private final TYPE nodeType;

    private Supplier<CoordinateSequence> boundsSequence = new Supplier<CoordinateSequence>() {

        private SoftReference<CoordinateSequence> seq;

        @Override
        public CoordinateSequence get() {
            if (seq == null || seq.get() == null) {
                CoordinateSequence coords = parseBoundsCoordinates();
                seq = new SoftReference<CoordinateSequence>(coords);
            }
            return seq.get();
        }
    };

    public NodeSet(NodesetHeader header, DataBuffer data, TYPE nodeType) {
        this.header = header;
        this.data = data;
        this.nodeType = nodeType;
    }

    private static class NodeFlags {

        static final NodeFlags EMPTY = new NodeFlags(0);

        private static final int BITS_PER_NODE = 4;

        private static final int METADATA_PRESENT_BIT = 0;

        private static final int BOUNDS_PRESENT_BIT = 1;

        private static final int BOUNDS_IS_POINT_BIT = 2;

        private static final int EXTRA_DATA_PRESENT_BIT = 3;

        private final BitSet bitset;

        private NodeFlags(final int numNodes) {
            this.bitset = new BitSet(BITS_PER_NODE * numNodes);
        }

        public NodeFlags(BitSet bitset) {
            this.bitset = bitset;
        }

        static NodeFlags parse(DataInput in, int numNodes) throws IOException {
            if (numNodes == 0) {
                return EMPTY;
            }
            int logicalSize = in.readInt();
            final int length = (int) Math.ceil(numNodes / 2D);
            byte[] bitsetData = new byte[length];
            in.readFully(bitsetData, 0, logicalSize);
            BitSet bitset = BitSet.valueOf(bitsetData);
            return new NodeFlags(bitset);
        }

        private int bitIndex(int nodeIndex) {
            return BITS_PER_NODE * nodeIndex;
        }

        boolean metadataPresent(int nodeIndex) {
            return bitset.get(METADATA_PRESENT_BIT + bitIndex(nodeIndex));
        }

        void metadataPresent(int nodeIndex, boolean present) {
            bitset.set(METADATA_PRESENT_BIT + bitIndex(nodeIndex), present);
        }

        boolean boundsPresent(int nodeIndex) {
            return bitset.get(BOUNDS_PRESENT_BIT + bitIndex(nodeIndex));
        }

        void boundsPresent(int nodeIndex, boolean present) {
            bitset.set(BOUNDS_PRESENT_BIT + bitIndex(nodeIndex), present);
        }

        boolean isPoint(int nodeIndex) {
            return bitset.get(BOUNDS_IS_POINT_BIT + bitIndex(nodeIndex));
        }

        void isPoint(int nodeIndex, boolean pointBounds) {
            bitset.set(BOUNDS_IS_POINT_BIT + bitIndex(nodeIndex), pointBounds);
        }

        boolean extraDataPresent(int nodeIndex) {
            return bitset.get(EXTRA_DATA_PRESENT_BIT + bitIndex(nodeIndex));
        }

        void extraDataPresent(int nodeIndex, boolean present) {
            bitset.set(EXTRA_DATA_PRESENT_BIT + bitIndex(nodeIndex), present);
        }
    }

    /**
     * <pre>
     * {@code
     *  NodeSet = <HEADER>,[DATA]
     *  
     * HEADER = <int>, // size of this header
     *          <int>, //size, number of nodes
     *          <byte[]>, // node flags (4 bits per node)
     *          <uvarint>, //size in bytes of node data section
     *          <uvarint>, //size in bytes of objectids array
     *          <uvarint>, //size in bytes of bounds section
     *          <uvarint>; //size in bytes of extra data section
     *  
     *  DATA =  <uvarint[]>,  //node name indexes on string table
     *          <objectid[]>, //node object ids
     *          <bitset>, //metadata ids presence bitset
     *          [objectid[]], //metadata ids (only those present)
     *          (bounds and extra data TBD)
     * NODE =   <uvarint>, //name index
     *          <uvarint>, //objectid index
     *          [uvarint], //metadataid index
     *          [uvarint], //bounds index
     *          [uvarint]; //extra data relative offset
     * }
     * </pre>
     */
    public static void encode(DataOutput out, final List<Node> nodes, StringTable stringTable)
            throws IOException {
        final int size = nodes.size();

        if (size == 0) {
            out.writeInt(Integer.BYTES);
            return;
        }

        InternalDataOutput header = stream(32);
        InternalDataOutput nodeData = stream(size * 8);
        InternalDataOutput inlineExtraData = stream(size * 32);
        Index<ObjectId> uniqueIds = new Index<>();
        InternalDataOutput objectIds = stream(size * ObjectId.NUM_BYTES);

        header.writeInt(0);// fake header size

        NodeFlags flags = new NodeFlags(size);
        Envelope bounds = new Envelope();

        List<Coordinate> boundsCoords = new ArrayList<>(2 * size);

        for (int nodeIndex = 0; nodeIndex < size; nodeIndex++) {
            final Node node = nodes.get(nodeIndex);
            Preconditions.checkArgument(node != null);

            final int nameIndex, oidIndex, mdIdIndex, boundsIndex, extraDataRelOffset;

            nameIndex = stringTable.add(node.getName());
            checkState(nameIndex > -1);
            oidIndex = objectIdIndex(node.getObjectId(), uniqueIds, objectIds);
            Optional<ObjectId> metadataId = node.getMetadataId();

            flags.metadataPresent(nodeIndex, metadataId.isPresent());
            mdIdIndex = metadataId.isPresent()
                    ? objectIdIndex(metadataId.get(), uniqueIds, objectIds)
                    : -1;

            bounds.setToNull();
            node.expand(bounds);
            flags.boundsPresent(nodeIndex, !bounds.isNull());
            if (bounds.isNull()) {
                boundsIndex = -1;
            } else {
                boundsIndex = boundsCoords.size();
                boolean isPoint = bounds.getWidth() == 0D && bounds.getHeight() == 0D;
                flags.isPoint(nodeIndex, isPoint);
                boundsCoords.add(new Coordinate(bounds.getMinX(), bounds.getMinY()));
                if (!isPoint) {
                    boundsCoords.add(new Coordinate(bounds.getMaxX(), bounds.getMaxY()));
                }
            }

            final Map<String, Object> extraData = node.getExtraData();
            if (extraData.isEmpty()) {
                flags.extraDataPresent(nodeIndex, false);
                extraDataRelOffset = -1;
            } else {
                flags.extraDataPresent(nodeIndex, true);
                extraDataRelOffset = inlineExtraData.size();
                ExtraData.encode(extraData, inlineExtraData, stringTable);
            }

            // * NODE = <uvarint>, //name index
            // * <uvarint>, //objectid index
            // * [uvarint], //metadataid index
            // * [uvarint], //bounds index
            // * [uvarint]; //extra data relative offset
            writeUnsignedVarInt(nameIndex, nodeData);
            writeUnsignedVarInt(oidIndex, nodeData);
            if (metadataId.isPresent()) {
                writeUnsignedVarInt(mdIdIndex, nodeData);
            }
            if (!bounds.isNull()) {
                writeUnsignedVarInt(boundsIndex, nodeData);
            }
            if (!extraData.isEmpty()) {
                writeUnsignedVarInt(extraDataRelOffset, nodeData);
            }
        }

        // * HEADER = <int>, // size of this header
        // * <int>, //size, number of nodes
        // * <int>, //logical size of flags
        // * <byte[]>, // node flags (4 bits per node)
        // * <uvarint>, //size in bytes of node data section
        // * <uvarint>, //size in bytes of objectids array
        // * <uvarint>, //size in bytes of bounds section
        // * <uvarint>, //size in bytes of extra data section
        header.writeInt(size);
        final byte[] flagsData = flags.bitset.toByteArray();
        header.writeInt(flagsData.length);
        header.write(flagsData);
        int nodeDataSize = nodeData.size();
        writeUnsignedVarInt(nodeDataSize, header);
        int oidSectionSize = objectIds.size();
        writeUnsignedVarInt(oidSectionSize, header);
        // bounds
        FloatPackedCoordinateSequence boundsSeq = new FloatPackedCoordinateSequence(2,
                boundsCoords);
        InternalDataOutput boundsStream = stream(boundsSeq.size() * 4);
        int[][] allOrdinates = boundsSeq.toSerializedForm();
        int[] xordinates = allOrdinates[0];
        int[] yordinates = allOrdinates[1];
        Varints.writeSignedIntArray(xordinates, boundsStream);
        Varints.writeSignedIntArray(yordinates, boundsStream);
        final int coordsSectionSzie = boundsStream.size();
        writeUnsignedVarInt(coordsSectionSzie, header);// bounds

        writeUnsignedVarInt(inlineExtraData.size(), header);// extra data

        // reset header's first 4 bytes to header section size
        final int headerSize = header.size();
        header.reset().writeInt(headerSize);
        header.setSize(headerSize);

        // write sections
        header.writeTo(out);
        nodeData.writeTo(out);
        objectIds.writeTo(out);
        boundsStream.writeTo(out);
        inlineExtraData.writeTo(out);
    }

    private static int objectIdIndex(ObjectId objectId, Index<ObjectId> uniqueIds,
            InternalDataOutput objectIds) throws IOException {

        final boolean exists = -1 != uniqueIds.indexOf(objectId);
        final int oidIdx = uniqueIds.getOrAdd(objectId);
        if (!exists) {
            objectId.writeTo(objectIds);
        }
        return oidIdx;
    }

    public static NodeSet decode(final DataBuffer data, final int offset, final TYPE type) {
        checkNotNull(data);
        checkArgument(offset >= 0);
        checkArgument(type == TYPE.FEATURE || type == TYPE.TREE);

        NodesetHeader header = NodesetHeader.decode(data, offset);

        if (header.numNodes == 0) {
            return TYPE.FEATURE.equals(type) ? NodeSet.EMPTY_FEATURES : NodeSet.EMPTY_TREES;
        }

        return new NodeSet(header, data, type);
    }

    CoordinateSequence parseBoundsCoordinates() {
        final int boundsSectionSize = header.boundsSize;
        if (0 == boundsSectionSize) {
            return FloatPackedCoordinateSequence.EMPTY_2D;
        }
        final int boundsOffset = header.boundsOffset();
        DataInput in = data.asDataInput(boundsOffset);
        int[] x;
        int[] y;
        try {
            x = Varints.readSignedIntArray(in);
            y = Varints.readSignedIntArray(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int[][] coords = new int[][] { x, y };
        return new FloatPackedCoordinateSequence(coords);
    }

    // * HEADER = <int>, // size of this header
    // * <int>, //size, number of nodes
    // * <byte[]>, // node flags (4 bits per node)
    // * <uvarint>, //size in bytes of node data section
    // * <uvarint>, //size in bytes of objectids array
    // * <uvarint>, //size in bytes of bounds section
    // * <uvarint>, //size in bytes of extra data section
    static final class NodesetHeader {
        private static final NodesetHeader EMPTY = new NodesetHeader();

        private int numNodes;

        private int nodeDataSectionSize;

        private int objectIdsSize;

        private int boundsSize;

        @SuppressWarnings("unused")
        private int extraDataSize;

        private int headerSize;

        private int offset;

        private NodeFlags nodeFlags;

        public static NodesetHeader decode(DataBuffer data, int offset) {
            DataInput in = data.asDataInput(offset);
            try {
                final int headerSize = in.readInt();
                if (headerSize == Integer.BYTES) {
                    return EMPTY;
                }
                NodesetHeader header = new NodesetHeader();
                header.offset = offset;
                header.headerSize = headerSize;
                header.numNodes = in.readInt();
                header.nodeFlags = NodeFlags.parse(in, header.numNodes);
                header.nodeDataSectionSize = readUnsignedVarInt(in);
                header.objectIdsSize = readUnsignedVarInt(in);
                header.boundsSize = readUnsignedVarInt(in);
                header.extraDataSize = readUnsignedVarInt(in);
                return header;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public int nodeDataSectionOffset() {
            return offset + headerSize;
        }

        public int objectIdsOffset() {
            return nodeDataSectionOffset() + nodeDataSectionSize;
        }

        public int boundsOffset() {
            return objectIdsOffset() + objectIdsSize;
        }

        public int extraDataOffset() {
            return boundsOffset() + boundsSize;
        }
    }

    public int size() {
        return header.numNodes;
    }

    public TYPE getType() {
        return nodeType;
    }

    public ObjectId getObjectId(int objectIdIndex) {
        Preconditions.checkArgument(objectIdIndex > -1);
        final int offset = header.objectIdsOffset() + ObjectId.NUM_BYTES * objectIdIndex;
        return data.getObjectId(offset);
    }

    public Optional<ObjectId> getMetadataId(final int objectIdIndex) {
        Optional<ObjectId> mdId = Optional.absent();
        if (objectIdIndex > -1) {
            mdId = Optional.of(getObjectId(objectIdIndex));
        }
        return mdId;
    }

    public Optional<Envelope> getBounds(final int nodeIndex, final int boundsIndex) {
        if (-1 == boundsIndex) {
            return Optional.absent();
        }
        checkState(header.nodeFlags.boundsPresent(nodeIndex));
        final boolean isPoint = header.nodeFlags.isPoint(nodeIndex);

        CoordinateSequence coordSeq = boundsSequence.get();
        double minx = coordSeq.getOrdinate(boundsIndex, 0);
        double miny = coordSeq.getOrdinate(boundsIndex, 1);
        double maxx = isPoint ? minx : coordSeq.getOrdinate(boundsIndex + 1, 0);
        double maxy = isPoint ? miny : coordSeq.getOrdinate(boundsIndex + 1, 1);
        Envelope env = new Envelope(minx, maxx, miny, maxy);
        return Optional.of(env);
    }

    public Map<String, Object> getExtraData(final int nodeExtraDataRelOffset) {
        if (nodeExtraDataRelOffset < 0) {
            return ImmutableMap.of();
        }
        Map<String, Object> extraData;
        try {
            extraData = ExtraData.decode(this, nodeExtraDataRelOffset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return extraData;
    }

    public @Nullable Object getExtraData(final int nodeExtraDataRelOffset, final String key) {
        if (nodeExtraDataRelOffset < 0) {
            return null;
        }
        try {
            return ExtraData.get(this, nodeExtraDataRelOffset, key);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ImmutableList<Node> build() {
        ImmutableList.Builder<Node> builder = ImmutableList.builder();
        final int size = size();
        final int dataOffset = header.nodeDataSectionOffset();
        DataInput in = data.asDataInput(dataOffset);
        try {
            for (int i = 0; i < size; i++) {
                Node node = buildNode(i, in);
                builder.add(node);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return builder.build();
    }

    public String getName(final int nameIndex) {
        StringTable stringTable = data.getStringTable().get();
        String name = stringTable.get(nameIndex);
        return name;
    }

    private Node buildNode(final int nodeIndex, final DataInput in) throws IOException {
        int nameIndex = readUnsignedVarInt(in);
        checkState(nameIndex > -1);
        int oidIndex = readUnsignedVarInt(in);
        checkState(nameIndex > -1);
        int mdIdIndex = -1;
        int boundsIndex = -1;
        int extraDataRelOffset = -1;
        NodeFlags flags = header.nodeFlags;
        if (flags.metadataPresent(nodeIndex)) {
            mdIdIndex = readUnsignedVarInt(in);
        }
        if (flags.boundsPresent(nodeIndex)) {
            boundsIndex = readUnsignedVarInt(in);
        }
        if (flags.extraDataPresent(nodeIndex)) {
            extraDataRelOffset = readUnsignedVarInt(in);
        }
        return new LazyNode(this, nodeIndex, nameIndex, oidIndex, mdIdIndex, boundsIndex,
                extraDataRelOffset);
    }

}
