package org.locationtech.geogig.tempstorage.rocksdb;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.model.internal.DAGNode;
import org.locationtech.geogig.model.internal.NodeId;
import org.locationtech.geogig.tempstorage.rocksdb.DAGSerializer;
import org.locationtech.jts.geom.Envelope;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class DAGSerializerTest {

    @Test
    public void lazyFeatureNodeEncodeDecode() throws IOException {
        DAGNode node = DAGNode.featureNode(5, 511);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        DAGSerializer.encode(node, out);

        DAGNode decoded = DAGSerializer.decode(ByteStreams.newDataInput(out.toByteArray()));
        assertEquals(node, decoded);
    }

    @Test
    public void testEncodeCanonicalNodeIds() throws Exception {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        List<NodeId> nodes = new ArrayList<>();
        int size = 10;
        for (int i = 0; i < size; i++) {
            NodeId n = new NodeId("node-" + i);
            nodes.add(n);
            DAGSerializer.write(n, out);
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(out.toByteArray());

        List<NodeId> decoded = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            NodeId n = DAGSerializer.read(in);
            decoded.add(n);
        }
        assertEquals(nodes, decoded);
    }

    @Test
    public void testEncodeQuadTreeNodeIds() throws Exception {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        List<NodeId> nodes = new ArrayList<>();
        int size = 10;
        for (int i = 0; i < size; i++) {
            Envelope env = new Envelope(i, i + 1, i, i + 1);
            NodeId n = new NodeId("node-" + i, env);
            nodes.add(n);
            DAGSerializer.write(n, out);
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(out.toByteArray());

        List<NodeId> decoded = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            NodeId n = DAGSerializer.read(in);
            decoded.add(n);
        }
        assertEquals(nodes, decoded);
    }
}
