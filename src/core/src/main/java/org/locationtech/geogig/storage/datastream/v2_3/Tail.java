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

import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * <pre>
 * {@code
 * 
 *  TAIL =      <uvarint>, (* offset of trees nodeset, zero = missing *)
 *              <uvarint>, (* offset of features nodeset, zero = missing *)
 *              <uvarint>, (* offset of bucketset, zero = missing*)
 *              <uvarint>, (* offset of stringtable *)
 *              <int4>; (* offset of this tail *)
 * }
 * </pre>
 *
 */
class Tail {

    private final int offsetOfTreesNodeset;

    private final int offsetOfFeaturesNodeset;

    private final int offsetOfBuckets;

    private final int offsetOfStringTable;

    private final int offsetOfTail;

    private Tail(int offsetOfTreesNodeset, //
            int offsetOfFeaturesNodeset, //
            int offsetOfBuckets, //
            int offsetOfStringTable, //
            int offsetOfTail) {
        this.offsetOfTreesNodeset = offsetOfTreesNodeset;
        this.offsetOfFeaturesNodeset = offsetOfFeaturesNodeset;
        this.offsetOfBuckets = offsetOfBuckets;
        this.offsetOfStringTable = offsetOfStringTable;
        this.offsetOfTail = offsetOfTail;

    }

    public static void encode(DataOutput out, //
            int offsetOfTreesNodeset, //
            int offsetOfFeaturesNodeset, //
            int offsetOfBuckets, //
            int offsetOfStringTable, //
            int offsetOfTail) {
        try {
            writeUnsignedVarInt(offsetOfTreesNodeset, out);
            writeUnsignedVarInt(offsetOfFeaturesNodeset, out);
            writeUnsignedVarInt(offsetOfBuckets, out);
            writeUnsignedVarInt(offsetOfStringTable, out);
            out.writeInt(offsetOfTail);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Tail decode(ByteBuffer data) {
        final int offsetOfTail = Tail.offsetOfTail(data);
        final int limit = data.capacity() - 1;
        DataInput in = RevTreeFormat.asDataInput(data, offsetOfTail, limit);
        try {
            int offsetOfTreesNodeset = readUnsignedVarInt(in);
            int offsetOfFeaturesNodeset = readUnsignedVarInt(in);
            int offsetOfBuckets = readUnsignedVarInt(in);
            int offsetOfStringTable = readUnsignedVarInt(in);
            return new Tail(offsetOfTreesNodeset, offsetOfFeaturesNodeset, offsetOfBuckets,
                    offsetOfStringTable, offsetOfTail);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int offsetOfTail(ByteBuffer data) {
        final int offsetOfOffsetOfTail = data.capacity() - Integer.BYTES;
        final int offsetOfTail = data.getInt(offsetOfOffsetOfTail);
        return offsetOfTail;
    }

    public int getOffsetOfTreesNodeset() {
        return offsetOfTreesNodeset;
    }

    public int getOffsetOfFeaturesNodeset() {
        return offsetOfFeaturesNodeset;
    }

    public int getOffsetOfBuckets() {
        return offsetOfBuckets;
    }

    public int getOffsetOfStringTable() {
        return offsetOfStringTable;
    }

    public int getOffsetOfTail() {
        return offsetOfTail;
    }
}
