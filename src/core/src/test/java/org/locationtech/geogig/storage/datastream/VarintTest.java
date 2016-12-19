/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import static com.google.common.io.ByteStreams.newDataInput;
import static com.google.common.io.ByteStreams.newDataOutput;
import static org.junit.Assert.assertEquals;
import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarLong;

import java.io.IOException;

import org.junit.Test;

import com.google.common.io.ByteArrayDataOutput;

public class VarintTest {

    @Test
    public void writeSignedVarIntTest() throws IOException {
        checkSignedVarInt(Integer.MIN_VALUE);
        checkSignedVarInt(Integer.MAX_VALUE);
        checkSignedVarInt(0);
        checkSignedVarInt(1001);
        checkSignedVarInt(-1001);
    }

    private void checkSignedVarInt(final int value) throws IOException {
        ByteArrayDataOutput out = newDataOutput();
        writeSignedVarInt(value, out);
        int read = readSignedVarInt(newDataInput(out.toByteArray()));
        assertEquals(value, read);
    }

    @Test
    public void writeUnsignedVarIntTest() throws IOException {
        checkUnsignedVarInt(Integer.MAX_VALUE);
        checkUnsignedVarInt(0);
        checkUnsignedVarInt(1001);
    }

    private void checkUnsignedVarInt(final int value) throws IOException {
        ByteArrayDataOutput out = newDataOutput();
        writeUnsignedVarInt(value, out);
        int read = readUnsignedVarInt(newDataInput(out.toByteArray()));
        assertEquals(value, read);
    }

    @Test
    public void writeSignedVarLongTest() throws IOException {
        checkSignedVarLong(Long.MIN_VALUE);
        checkSignedVarLong(Long.MAX_VALUE);
        checkSignedVarLong(0);
        checkSignedVarLong(1001);
        checkSignedVarLong(-1001);
    }

    private void checkSignedVarLong(final long value) throws IOException {
        ByteArrayDataOutput out = newDataOutput();
        writeSignedVarLong(value, out);
        long read = readSignedVarLong(newDataInput(out.toByteArray()));
        assertEquals(value, read);
    }

    @Test
    public void writeUnsignedVarLongTest() throws IOException {
        checkUnsignedVarLong(Long.MAX_VALUE);
        checkUnsignedVarLong(0);
        checkUnsignedVarLong(1001);
    }

    private void checkUnsignedVarLong(final long value) throws IOException {
        ByteArrayDataOutput out = newDataOutput();
        writeUnsignedVarLong(value, out);
        long read = readUnsignedVarLong(newDataInput(out.toByteArray()));
        assertEquals(value, read);
    }
}
