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

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.datastream.DataStreamValueSerializerV2;
import org.locationtech.geogig.storage.datastream.FormatCommonV2;
import org.locationtech.geogig.storage.datastream.FormatCommonV2_2;

/**
 * Format common v2.1, differs from {@link FormatCommonV2 v2} only in {@link RevFeature}
 * serialization by adding a tail with value offsets, to allow parsing of individual attribute
 * values.
 * <p>
 * Format for features:
 * 
 * <pre>
 * <code>
 * <HEADER><DATA>
 * 
 * <HEADER>:
 * - unsigned varint: number of attributes
 * - unsigned varint: size of <DATA>
 * - unsigned varint[number of attributes]: attribute offsets (starting form zero at <DATA>, not including the header)
 * 
 * <DATA>:
 * - byte[]: attribute data, as of {@link DataStreamValueSerializerV2#write(Object, DataOutput)}
 * </code>
 * </pre>
 */
class FormatCommonV2_3 extends FormatCommonV2_2 {

    public static final FormatCommonV2_3 INSTANCE = new FormatCommonV2_3();

    private static final class InternalByteArrayOutputStream extends ByteArrayOutputStream {
        InternalByteArrayOutputStream() {
            super(1024);
        }

        public byte[] intenal() {
            return super.buf;
        }
    }

    @Override
    public void writeTree(RevTree tree, DataOutput data) throws IOException {
        InternalByteArrayOutputStream out = new InternalByteArrayOutputStream();
        RevTreeFormat.encode(tree, new DataOutputStream(out));
        int size = out.size();
        data.writeInt(size);
        data.write(out.intenal(), 0, size);
    }

    @Override
    public RevTree readTree(@Nullable ObjectId id, DataInput in) throws IOException {
        final int size = in.readInt();
        byte[] data = new byte[size];
        in.readFully(data);
        return readTree(id, data, 0, size);
    }

    public RevTree readTree(@Nullable ObjectId id, byte[] data, int offset, int length)
            throws IOException {
        RevTree tree = RevTreeFormat.decode(id, data, offset, length);
        // {
        // ByteArrayOutputStream out = new ByteArrayOutputStream();
        // DataStreamSerializationFactoryV2.INSTANCE.write(tree, out);
        //
        // RevTree read = (RevTree) DataStreamSerializationFactoryV2.INSTANCE.read(null,
        // new ByteArrayInputStream(out.toByteArray()));
        //
        // Preconditions.checkState(tree.features().equals(read.features()));
        // Preconditions.checkState(tree.trees().equals(read.trees()));
        // Preconditions.checkState(tree.buckets().equals(read.buckets()));
        // Preconditions.checkState(tree.size() == read.size());
        // Preconditions.checkState(tree.numTrees() == read.numTrees());
        // }
        return tree;
    }
}
