/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.readUnsignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarLong;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarLong;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public final class Varints {

    private Varints() {
        //
    }

    public static void writeSignedIntArray(int[] values, DataOutput out) throws IOException {

        final int length = values.length;
        writeLength(values, out);

        for (int i = 0; i < length; i++) {
            writeSignedVarInt(values[i], out);
        }
    }

    public static int[] readSignedIntArray(DataInput in) throws IOException {
        final int length = readLength(in);
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = readSignedVarInt(in);
        }
        return values;
    }

    public static void writeSignedLongArray(long[] values, DataOutput out) throws IOException {

        final int length = writeLength(values, out);

        for (int i = 0; i < length; i++) {
            writeSignedVarLong(values[i], out);
        }
    }

    public static long[] readSignedLongArray(DataInput in) throws IOException {
        final int length = readLength(in);
        long[] values = new long[length];
        for (int i = 0; i < length; i++) {
            values[i] = readSignedVarLong(in);
        }
        return values;
    }

    public static void writeUnignedIntArray(int[] values, DataOutput out) throws IOException {

        final int length = values.length;
        writeLength(values, out);

        for (int i = 0; i < length; i++) {
            writeUnsignedVarInt(values[i], out);
        }
    }

    public static int[] readUnsignedIntArray(DataInput in, AtomicInteger outBytesRead) throws IOException {
        final int length = readLength(in);
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = readUnsignedVarInt(in);
        }
        return values;
    }

    public static void writeUnsignedLongArray(long[] values, DataOutput out) throws IOException {

        final int length = writeLength(values, out);

        for (int i = 0; i < length; i++) {
            writeUnsignedVarLong(values[i], out);
        }
    }

    public static long[] readUnsignedLongArray(DataInput in) throws IOException {
        final int length = readLength(in);
        long[] values = new long[length];
        for (int i = 0; i < length; i++) {
            values[i] = readUnsignedVarLong(in);
        }
        return values;
    }

    public static void writeIntArrayDeltaEncoded(int[] values, DataOutput out) throws IOException {

        final int length = values.length;
        writeLength(values, out);

        int prev = 0;
        for (int i = 0; i < length; i++) {
            int value = values[i];
            int delta = value - prev;
            prev = value;
            writeSignedVarInt(delta, out);
        }
    }

    public static void writeIntArrayDeltaEncoded(DataOutput out, int[] values, int offset,
            int length) throws IOException {

        int prev = 0;
        final int max = offset + length;
        for (int i = offset; i < max; i++) {
            int value = values[i];
            int delta = value - prev;
            prev = value;
            writeSignedVarInt(delta, out);
        }
    }

    public static int[] readIntArrayDeltaEncoded(DataInput in) throws IOException {
        final int length = readLength(in);

        int[] values = new int[length];

        int prev = 0;

        for (int i = 0; i < length; i++) {
            int delta = readSignedVarInt(in);
            int value = prev + delta;
            values[i] = value;
            prev = value;
        }
        return values;
    }

    public static void readIntArrayDeltaEncoded(DataInput in, int[] values, final int offset,
            final int length) throws IOException {

        int prev = 0;

        final int max = offset + length;
        for (int i = offset; i < max; i++) {
            int delta = readSignedVarInt(in);
            int value = prev + delta;
            values[i] = value;
            prev = value;
        }
    }

    public static void writeLongArrayDeltaEncoded(long[] values, DataOutput out)
            throws IOException {

        final int length = writeLength(values, out);

        long prev = 0;
        for (int i = 0; i < length; i++) {
            long value = values[i];
            long delta = value - prev;
            prev = value;
            writeSignedVarLong(delta, out);
        }
    }

    public static void writeLongArrayDeltaEncoded(DataOutput out, long[] values, int offset,
            int length) throws IOException {

        long prev = 0;
        final int max = offset + length;
        for (int i = offset; i < max; i++) {
            long value = values[i];
            long delta = value - prev;
            prev = value;
            writeSignedVarLong(delta, out);
        }
    }

    public static long[] readLongArrayDeltaEncoded(DataInput in) throws IOException {
        final int length = readLength(in);

        long[] values = new long[length];

        long prev = 0;

        for (int i = 0; i < length; i++) {
            long delta = readSignedVarLong(in);
            long value = prev + delta;
            values[i] = value;
            prev = value;
        }
        return values;
    }

    public static void readLongArrayDeltaEncoded(DataInput in, long[] values, int offset,
            int length) throws IOException {

        long prev = 0;

        final int max = offset + length;
        for (int i = offset; i < max; i++) {
            long delta = readSignedVarLong(in);
            long value = prev + delta;
            values[i] = value;
            prev = value;
        }
    }

    private static int readLength(DataInput in) throws IOException {
        final int length = readUnsignedVarInt(in);
        return length;
    }

    /**
     * Writes the length of the array
     * 
     * @return the number of bytes written
     * @throws IOException
     */
    private static int writeLength(int[] values, DataOutput out) throws IOException {
        final int length = values.length;

        return writeUnsignedVarInt(length, out);
    }

    private static int writeLength(long[] values, DataOutput out) throws IOException {
        final int length = values.length;

        writeUnsignedVarInt(length, out);
        return length;
    }

}
