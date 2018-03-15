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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.datastream.Varint;

import com.google.common.base.Preconditions;

/**
 * {@code
 *  HEADER =    <byte>, (* literal byte 1, corresponding to RevObject.TYPE.TREE *) 
 *              <ushort>, (* size of header *)
 *              <uvarlong>, (*total size*)
 *              <uvarint>, (* recursive number of tree nodes *)
 *              <uvarint>, (*offset of contained tree nodes nodeset*)
 *              <uvarint>, (*offset of contained feature nodes nodeset*)
 *              <uvarint>, (*offset of contained bucketset*)
 *              <uvarint>; (*byte offset of string table, zero being the first byte of the header*)
 * }
 *
 */
class Header {

    public static final Header EMPTY = new Header(0, 0);

    private final long size;

    private final int trees;

    public Header(long size, int trees) {
        this.size = size;
        this.trees = trees;
    }

    public long size() {
        return size;
    }

    public int numTrees() {
        return trees;
    }

    public static void encode(DataOutput out, RevTree tree) throws IOException {
        // object type
        out.write(RevObject.TYPE.TREE.ordinal());
        final long totalSize = tree.size();
        final int totalSubtrees = tree.numTrees();
        Varint.writeUnsignedVarLong(totalSize, out);
        Varint.writeUnsignedVarInt(totalSubtrees, out);
    }

    public static Header parse(ByteBuffer data) {
        DataInput in = RevTreeFormat.asDataInput(data);
        try {
            final int type = in.readUnsignedByte();
            TYPE _type = TYPE.valueOf(type);
            Preconditions.checkArgument(TYPE.TREE.equals(_type));
            final long size = Varint.readUnsignedVarLong(in);
            final int trees = Varint.readUnsignedVarInt(in);
            return new Header(size, trees);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
