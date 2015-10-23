package org.locationtech.geogig.storage.mapdb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.mapdb.Serializer;

import com.google.common.base.Throwables;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;

class NodeSerializer extends Serializer<Node> {

    public static final Serializer<Node> INSTANCE = new NodeSerializer();

    private NodeSerializer() {
        // force usage of singleton instance
    }

    @Override
    public Node deserialize(DataInput in, int available) throws IOException {
        final int size = in.readInt();
        byte[] buff = new byte[size];
        in.readFully(buff);

        ByteArrayInputStream bin = new ByteArrayInputStream(buff);
        LZFInputStream cin = new LZFInputStream(bin);

        Node node;
        try (ObjectInputStream oin = new ObjectInputStream(cin)) {
            node = (Node) oin.readObject();
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
        return node;
    }

    // static AtomicInteger count = new AtomicInteger();
    //
    // static AtomicInteger totalSize = new AtomicInteger();
    //
    // public static int average() {
    // return totalSize.get() / count.get();
    // }

    @Override
    public void serialize(DataOutput out, Node node) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        LZFOutputStream cout = new LZFOutputStream(bout);
        new ObjectOutputStream(cout).writeObject(node);
        cout.close();

        byte[] byteArray = bout.toByteArray();
        int length = byteArray.length;
        out.writeInt(length);
        out.write(byteArray);
        // count.incrementAndGet();
        // totalSize.addAndGet(length);
    }

}
