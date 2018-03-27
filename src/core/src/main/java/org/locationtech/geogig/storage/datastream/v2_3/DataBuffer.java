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
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

import org.locationtech.geogig.model.ObjectId;

import com.google.common.annotations.VisibleForTesting;

class DataBuffer {

    public static final DataBuffer EMTPY = new DataBuffer(ByteBuffer.wrap(new byte[0]), null, null);

    private ByteBuffer raw;

    private Supplier<StringTable> stringTable;

    private final Tail tail;

    private final Header header;

    private final class StringTableSupplier implements Supplier<StringTable> {

        private SoftReference<StringTable> stringTableRef;

        @Override
        public StringTable get() {
            if (null == stringTableRef || null == stringTableRef.get()) {
                final int offset = tail.getOffsetOfStringTable();
                StringTable st = StringTable.EMPTY;
                if (offset > 0) {
                    DataInput in = RevTreeFormat.asDataInput(raw, offset, raw.capacity());
                    st = StringTable.decode(in);
                }
                stringTableRef = new SoftReference<StringTable>(st);
            }
            return stringTableRef.get();
        }

    }

    private DataBuffer(ByteBuffer raw, Header header, Tail tail) {
        this.raw = raw;
        this.header = header;
        this.tail = tail;
        this.stringTable = new StringTableSupplier();
    }

    @VisibleForTesting
    DataBuffer(ByteBuffer raw, StringTable stringTable) {
        this.raw = raw;
        this.stringTable = () -> stringTable;
        this.tail = null;
        this.header = null;
    }

    public void writeTo(DataOutput out) throws IOException {
        final int size = raw.limit();
        final byte[] array;
        if (raw.hasArray()) {
            array = raw.array();
        } else {
            array = new byte[size];
            raw.duplicate().get(array, 0, size);
        }
        out.write(array, 0, size);
    }

    @VisibleForTesting
    static DataBuffer wrap(byte[] buff, StringTable stringTable) {
        return new DataBuffer(ByteBuffer.wrap(buff), stringTable);
    }

    public static DataBuffer of(ByteBuffer data) {
        Header header = Header.parse(data);
        Tail tail = Tail.decode(data);
        return new DataBuffer(data, header, tail);
    }

    public static DataBuffer of(byte[] data, int offset, int length) {
        return of(ByteBuffer.wrap(data, offset, length));
    }

    public Supplier<StringTable> getStringTable() {
        return stringTable;
    }

    public long getLong(int offset) {
        long val = raw.getLong(offset);
        return val;
    }

    public int getInt(int offset) {
        int val = raw.getInt(offset);
        return val;
    }

    public int getUnsignedShort(int offset) {
        int val = raw.getShort(offset) & 0xFFFF;
        return val;
    }

    public Header header() {
        return header;
    }

    public Tail tail() {
        return tail;
    }

    public DataInput asDataInput(int offset) {
        return RevTreeFormat.asDataInput(raw, offset, raw.limit());
    }

    public DataInput asDataInput() {
        return RevTreeFormat.asDataInput(raw, 0, raw.limit());
    }

    public ObjectId getObjectId(final int offset) {
        // duplicate() instead of mark()/reset() to preserve thread safety
        ByteBuffer raw = this.raw.duplicate();
        raw.position(offset);
        int h1 = raw.getInt();
        long h2 = raw.getLong();
        long h3 = raw.getLong();
        return ObjectId.create(h1, h2, h3);
    }

    public void get(byte[] buff, final int offset) {
        // duplicate() instead of mark()/reset() to preserve thread safety
        ByteBuffer raw = this.raw.duplicate();
        raw.position(offset);
        raw.get(buff);
    }

    public int size() {
        return raw.capacity();
    }

    public ByteBuffer getByteBuffer(int offset, int length) {
        ByteBuffer b = this.raw.duplicate();
        b.position(offset).limit(offset + length);
        return b;
    }

}
