/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.locationtech.geogig.storage.datastream.Varint;

/**
 * Utility class to serialize and deserialize {@link NodeId} instances with {@link DAG#serialize}
 * and {@link DAG#deserialize} not having to know how to encode/decode the different kinds of
 * {@link NodeId} concrete classes.
 *
 */
class NodeIdIO {

    private static final byte CANONICAL = 0x01;

    private static final byte QUAD = 0x02;

    public static void write(NodeId id, DataOutput out) throws IOException {
        checkNotNull(id);
        checkNotNull(out);
        if (id instanceof CanonicalNodeId) {
            out.writeByte(CANONICAL);
            writeCanonical((CanonicalNodeId) id, out);
        } else if (id instanceof QuadTreeNodeId) {
            out.writeByte(QUAD);
            writeQuad((QuadTreeNodeId) id, out);
        } else {
            throw new IllegalArgumentException("Unknown nodeid type: " + id.getClass().getName());
        }
    }

    public static NodeId read(DataInput in) throws IOException {
        final int type = in.readUnsignedByte();
        switch (type) {
        case CANONICAL:
            return readCanonical(in);
        case QUAD:
            return readQuad(in);
        default:
            throw new IllegalArgumentException("Uknown id type identifier: " + type);
        }
    }

    private static void writeCanonical(CanonicalNodeId id, DataOutput out) throws IOException {
        out.writeUTF(id.name());
    }

    private static CanonicalNodeId readCanonical(DataInput in) throws IOException {
        String name = in.readUTF();
        return new CanonicalNodeId(name);
    }

    private static void writeQuad(QuadTreeNodeId id, DataOutput out) throws IOException {
        out.writeUTF(id.name());
        Quadrant[] quadrantsByDepth = id.quadrantsByDepth();
        writeQuadrants(quadrantsByDepth, out);
    }

    private static QuadTreeNodeId readQuad(DataInput in) throws IOException {
        String name = in.readUTF();
        Quadrant[] quadrantsByDepth = readQuadrants(in);
        return new QuadTreeNodeId(name, quadrantsByDepth);
    }

    static void writeQuadrants(Quadrant[] quadrantsByDepth, DataOutput out) throws IOException {
        final int numQuads = quadrantsByDepth.length;
        Varint.writeUnsignedVarInt(numQuads, out);

        if (numQuads == 0) {
            return;
        }

        final int bitsPerQuad = 2;
        final int quadsPerByte = Byte.SIZE / bitsPerQuad;
        final int numBytes = (int) Math.ceil((double) numQuads / quadsPerByte);

        byte[] data = new byte[numBytes];
        for (int i = 0; i < numQuads; i++) {
            final int byteN = i / quadsPerByte;
            final int shiftBy = bitsPerQuad * (i % quadsPerByte);

            final int bucketNumber = quadrantsByDepth[i].getBucketNumber();
            final int shiftedBucketNumber = bucketNumber << shiftBy;
            final int bucketByte = data[byteN];
            final int sharedBucketByte = bucketByte | shiftedBucketNumber;
            data[byteN] = (byte) sharedBucketByte;
        }
        out.write(data);
    }

    static Quadrant[] readQuadrants(DataInput in) throws IOException {
        final int numQuads = Varint.readUnsignedVarInt(in);
        if (numQuads == 0) {
            return new Quadrant[0];
        }

        Quadrant[] quads = new Quadrant[numQuads];
        final int bitsPerQuad = 2;
        final int quadsPerByte = Byte.SIZE / bitsPerQuad;
        final int numBytes = (int) Math.ceil((double) numQuads / quadsPerByte);

        final byte[] data = new byte[numBytes];
        in.readFully(data);

        for (int i = 0; i < numQuads; i++) {
            final int byteN = i / quadsPerByte;
            final int shiftBy = bitsPerQuad * (i % quadsPerByte);

            final int bucketByte = data[byteN];

            final int mask = 0b00000011 << shiftBy;
            final int unmasked = bucketByte & mask;
            final int bucketNumber = unmasked >>> shiftBy;

            Quadrant q = Quadrant.VALUES[bucketNumber];
            quads[i] = q;
        }

        return quads;
    }

}
