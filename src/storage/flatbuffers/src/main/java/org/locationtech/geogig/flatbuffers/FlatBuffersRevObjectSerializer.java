/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.RevObjectSerializer;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.FlatBufferBuilder.ByteBufferFactory;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class FlatBuffersRevObjectSerializer implements RevObjectSerializer {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = capacity -> ByteBuffer
            .allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);

    static final ThreadLocal<FlatBufferBuilder> WRITE_BUFFERS = ThreadLocal
            .withInitial(() -> new FlatBufferBuilder(32 * 1024, BYTE_BUFFER_FACTORY));

    private final FlatBuffers flatBuffers = new FlatBuffers();

    private @Getter @Setter boolean lengthPrefixed;

    public FlatBuffersRevObjectSerializer() {
        this(false);
    }

    public FlatBuffersRevObjectSerializer(boolean lengthPrefixed) {
        this.lengthPrefixed = lengthPrefixed;
    }

    public @Override boolean supportsStreaming() {
        return isLengthPrefixed();
    }

    public @Override String getDisplayName() {
        return "Flat Buffers";
    }

    public @Override void write(@NonNull RevObject o, @NonNull OutputStream out)
            throws IOException {

        final ByteBuffer dataBuffer;
        if (o instanceof FBRevObject) {
            dataBuffer = ((FBRevObject<?>) o).getTable().getByteBuffer().duplicate();
        } else {
            FlatBufferBuilder fbb = WRITE_BUFFERS.get();
            fbb.clear();
            flatBuffers.encode(o, fbb);
            dataBuffer = fbb.dataBuffer();
        }
        byte[] array;
        int off, len = dataBuffer.remaining();
        if (dataBuffer.hasArray()) {
            array = dataBuffer.array();
            off = dataBuffer.position();
        } else {
            off = 0;
            array = new byte[len];
            dataBuffer.get(array);
        }
        if (isLengthPrefixed()) {
            // write size in little endian (FlatBuffers is LE exclusively, so follow suite)
            out.write((len >>> 0) & 0xFF);
            out.write((len >>> 8) & 0xFF);
            out.write((len >>> 16) & 0xFF);
            out.write((len >>> 24) & 0xFF);
        }
        out.write(array, off, len);
    }

    public @Override RevObject read(@Nullable ObjectId id, InputStream in) throws IOException {
        final byte[] dataBuffer;
        if (isLengthPrefixed()) {
            // size in little endian (FlatBuffers is LE exclusively, so follow suite)
            int size = in.read() + (in.read() << 8) + (in.read() << 16) + (in.read() << 24);
            if (size < 0) {
                throw new EOFException();
            }
            dataBuffer = new byte[size];
            ByteStreams.readFully(in, dataBuffer);
        } else {
            dataBuffer = ByteStreams.toByteArray(in);
        }
        return flatBuffers.decode(id, dataBuffer, 0, dataBuffer.length);
    }

    public @Override RevObject read(@Nullable ObjectId id, byte[] data, int offset, int length)
            throws IOException {
        int padding = 0;
        if (isLengthPrefixed()) {
            padding = Integer.BYTES;
            int b1 = ((int) data[offset]) & 0xFF;
            int b2 = ((int) data[offset + 1]) & 0xFF;
            int b3 = ((int) data[offset + 2]) & 0xFF;
            int b4 = ((int) data[offset + 3]) & 0xFF;
            int size = b1 + (b2 << 8) + (b3 << 16) + (b4 << 24);
            Preconditions.checkArgument(size == length - Integer.BYTES);
        }
        return flatBuffers.decode(id, data, offset + padding, length - padding);
    }
}
