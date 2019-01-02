package org.locationtech.geogig.tempstorage.rocksdb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.internal.DAG;
import org.locationtech.geogig.model.internal.DAG.STATE;
import org.locationtech.geogig.model.internal.DAGNode;
import org.locationtech.geogig.model.internal.DAGNode.DirectDAGNode;
import org.locationtech.geogig.model.internal.DAGNode.LazyDAGNode;
import org.locationtech.geogig.model.internal.DAGNode.TreeDAGNode;
import org.locationtech.geogig.model.internal.NodeId;
import org.locationtech.geogig.model.internal.TreeId;
import org.locationtech.geogig.storage.datastream.DataStreamValueSerializerV2;
import org.locationtech.geogig.storage.datastream.FormatCommonV2_2;
import org.locationtech.geogig.storage.datastream.Varint;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import lombok.experimental.UtilityClass;

final @UtilityClass class DAGSerializer {

    private static final byte MAGIC_DIRECT = 7;

    private static final byte MAGIC_LAZY_FEATURE = 9;

    private static final byte MAGIC_LAZY_TREE = 11;

    public static void encode(DAGNode node, DataOutput output) throws IOException {

        if (node instanceof DirectDAGNode) {
            output.writeByte(MAGIC_DIRECT);
            FormatCommonV2_2.INSTANCE.writeNode(((DirectDAGNode) node).getNode(), output);
        } else {
            LazyDAGNode ln = (LazyDAGNode) node;
            if (ln instanceof TreeDAGNode) {
                output.writeByte(MAGIC_LAZY_TREE);
            } else {
                output.writeByte(MAGIC_LAZY_FEATURE);
            }
            final int leafRevTreeId = ln.leafRevTreeId();
            final int nodeIndex = ln.nodeIndex();
            Varint.writeUnsignedVarInt(leafRevTreeId, output);
            Varint.writeUnsignedVarInt(nodeIndex, output);
        }

    }

    public static DAGNode decode(DataInput in) throws IOException {
        final byte magic = in.readByte();
        switch (magic) {
        case MAGIC_DIRECT: {
            Node node = FormatCommonV2_2.INSTANCE.readNode(in);
            return DAGNode.of(node);
        }
        case MAGIC_LAZY_TREE: {
            int treeCacheId = Varint.readUnsignedVarInt(in);
            int nodeIndex = Varint.readUnsignedVarInt(in);
            DAGNode node = DAGNode.treeNode(treeCacheId, nodeIndex);
            return node;
        }
        case MAGIC_LAZY_FEATURE: {
            int treeCacheId = Varint.readUnsignedVarInt(in);
            int nodeIndex = Varint.readUnsignedVarInt(in);
            DAGNode node = DAGNode.featureNode(treeCacheId, nodeIndex);
            return node;
        }
        }
        throw new IllegalArgumentException("Invalid magic number, expected 7 or 9, got " + magic);
    }

    public static void serialize(DAG dag, DataOutput out) throws IOException {
        final ObjectId treeId = dag.originalTreeId();
        final STATE state = dag.getState();
        final long childCount = dag.getTotalChildCount();

        treeId.writeTo(out);
        out.writeByte(state.ordinal());
        out.writeLong(childCount);

        out.writeInt(dag.numChildren());
        out.writeShort(dag.numBuckets());

        dag.forEachChild(nodeid -> DAGSerializer.write(nodeid, out));

        dag.forEachBucket(tid -> {
            byte[] bucketIndicesByDepth = tid.bucketIndicesByDepth;
            try {
                out.writeShort(bucketIndicesByDepth.length);
                out.write(bucketIndicesByDepth);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static DAG deserialize(TreeId id, DataInput in) throws IOException {
        final ObjectId treeId = ObjectId.readFrom(in);
        final STATE state = STATE.values()[in.readByte() & 0xFF];
        final long childCount = in.readLong();

        final int childrenSize = in.readInt();
        final int bucketSize = in.readShort();

        Map<String, NodeId> children = ImmutableMap.of();
        Set<TreeId> buckets = ImmutableSet.of();

        if (childrenSize > 0) {
            children = new HashMap<>();
            for (int i = 0; i < childrenSize; i++) {
                NodeId nid = DAGSerializer.read(in);
                children.put(nid.name(), nid);
            }
        }
        if (bucketSize > 0) {
            buckets = new HashSet<>();
            for (int i = 0; i < bucketSize; i++) {
                final int len = in.readShort();
                final byte[] bucketIndicesByDepth = new byte[len];
                in.readFully(bucketIndicesByDepth);
                buckets.add(new TreeId(bucketIndicesByDepth));
            }
        }

        return new DAG(id, treeId, childCount, state, children, buckets);
    }

    public static void write(NodeId id, DataOutput out) {
        checkNotNull(id);
        checkNotNull(out);

        final String name = id.name();
        @Nullable
        final Object value = id.value();

        final FieldType valueType = FieldType.forValue((Object) id.value());
        try {
            out.writeUTF(name);
            out.writeByte(valueType.ordinal());
            DataStreamValueSerializerV2.INSTANCE.encode(valueType, value, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static NodeId read(DataInput in) throws IOException {
        final String name = in.readUTF();
        FieldType type = FieldType.valueOf(in.readUnsignedByte());
        final Object val = DataStreamValueSerializerV2.INSTANCE.decode(type, in);
        return new NodeId(name, val);
    }
}
