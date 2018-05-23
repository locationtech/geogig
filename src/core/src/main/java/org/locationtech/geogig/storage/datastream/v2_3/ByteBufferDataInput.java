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
import java.io.EOFException;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Adapts a {@link ByteBuffer} as a {@link DataInput}
 *
 */
class ByteBufferDataInput implements DataInput {

    private ByteBuffer buff;

    public ByteBufferDataInput(ByteBuffer buff, int offset, int limit) {
        this.buff = buff.duplicate();
        this.buff.order(ByteOrder.BIG_ENDIAN);
        ((Buffer) this.buff).position(offset);
        ((Buffer) this.buff).limit(limit);
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        try {
            buff.get(b, off, len);
        } catch (IndexOutOfBoundsException | BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public int skipBytes(int n) throws IOException {
        int remaining = buff.remaining();
        if (0 == remaining) {
            throw new EOFException();
        }
        int skip = Math.min(remaining, n);
        buff.position(buff.position() + skip);
        return skip;
    }

    @Override
    public boolean readBoolean() throws IOException {
        try {
            return buff.get() != 0;
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public byte readByte() throws IOException {
        try {
            return buff.get();
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public int readUnsignedByte() throws IOException {
        try {
            return buff.get() & 0xFF;
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public short readShort() throws IOException {
        try {
            return buff.getShort();
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public int readUnsignedShort() throws IOException {
        try {
            return buff.getShort() & 0xFFFF;
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public char readChar() throws IOException {
        try {
            return buff.getChar();
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public int readInt() throws IOException {
        try {
            return buff.getInt();
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public long readLong() throws IOException {
        try {
            return buff.getLong();
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public float readFloat() throws IOException {
        try {
            return buff.getFloat();
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public double readDouble() throws IOException {
        try {
            return buff.getDouble();
        } catch (BufferUnderflowException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public String readLine() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String readUTF() throws IOException {
        throw new UnsupportedOperationException();
    }

}