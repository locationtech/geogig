/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.UnsignedInts;
import com.google.common.primitives.UnsignedLongs;

/**
 * A unique identifier for a {@link RevObject}, which is created by passing a {@link HashFunction}
 * to {@link HashObjectFunnels}.
 * 
 * @apiNote the {@code ObjectId} effectively encloses a 20-byte byte array which is the
 *          <a href="https://en.wikipedia.org/wiki/SHA-1">SHA-1</a> hash resulting
 * 
 * @since 1.0
 */
@SuppressWarnings("deprecation")
public final class ObjectId implements Comparable<ObjectId>, Serializable {

    private static final long serialVersionUID = -2445723120477753654L;

    /**
     * A "natural order" {@link Ordering comparator}
     */
    public static final Ordering<ObjectId> NATURAL_ORDER = Ordering.<ObjectId> natural();

    /**
     * ObjectId instance that represents a NULL id.
     * 
     * @apiNote the NULL object is defined as the one where all its 20 bytes are zero.
     */
    public static final ObjectId NULL;

    /**
     * Hash function to create object ids out of its contents (SHA-1)
     */
    public static final HashFunction HASH_FUNCTION;

    /**
     * A constant with decimal value {@code 20}, defining the prescribed size of the internal byte
     * array that composes an object id
     */
    public static final int NUM_BYTES;

    private static final int NUM_CHARS;
    static {
        HASH_FUNCTION = Hashing.sha1();

        NUM_BYTES = HASH_FUNCTION.bits() / 8;

        NUM_CHARS = 2 * NUM_BYTES;

        NULL = new ObjectId(new byte[20]);
    }

    final int h1;

    final long h2, h3;

    /**
     * Constructs a new object id with the given byte code.
     * 
     * @param raw the byte code to use
     */
    public ObjectId(byte[] raw) {
        Preconditions.checkArgument(raw.length >= NUM_BYTES, "expected a byte[%s], got byte[%s]",
                NUM_BYTES, raw.length);
        this.h1 = readH1(raw);
        this.h2 = readH2(raw);
        this.h3 = readH3(raw);
    }

    private ObjectId(int h1, long h2, long h3) {
        this.h1 = h1;
        this.h2 = h2;
        this.h3 = h3;
    }

    private static int readH1(byte[] raw) {
        int h = byteN(raw, 0) << 24;
        h |= byteN(raw, 1) << 16;
        h |= byteN(raw, 2) << 8;
        h |= byteN(raw, 3) << 0;
        return h;
    }

    private static long readH2(byte[] raw) {
        return readLong(raw, 4);
    }

    private static long readH3(byte[] raw) {
        return readLong(raw, 12);
    }

    private static long readLong(byte[] raw, int offset) {
        long h = ((long) byteN(raw, offset)) << 56;
        h |= ((long) byteN(raw, offset + 1)) << 48;
        h |= ((long) byteN(raw, offset + 2)) << 40;
        h |= ((long) byteN(raw, offset + 3)) << 32;
        h |= ((long) byteN(raw, offset + 4)) << 24;
        h |= ((long) byteN(raw, offset + 5)) << 16;
        h |= ((long) byteN(raw, offset + 6)) << 8;
        h |= ((long) byteN(raw, offset + 7)) << 0;
        return h;
    }

    /**
     * @deprecated use #create
     */
    public static ObjectId createNoClone(byte[] rawHash) {
        return new ObjectId(rawHash);
    }

    public static ObjectId create(byte[] raw) {
        return new ObjectId(raw);
    }

    public static ObjectId create(int h1, long h2, long h3) {
        return new ObjectId(h1, h2, h3);
    }

    /**
     * @return whether or not this object id represents the {@link #NULL} object id
     */
    public boolean isNull() {
        return NULL.equals(this);
    }

    /**
     * Determines if this object id is the same as the given object id.
     * 
     * @param o the object id to compare against
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof ObjectId) {
            ObjectId i = (ObjectId) o;
            return this == o || (h1 == i.h1 && h2 == i.h2 && h3 == i.h3);
        }
        return false;
    }

    /**
     * @return a hash code based on the contents of the byte array.
     */
    @Override
    public int hashCode() {
        return 31 ^ (h1 == 0 ? 1 : h1);
    }

    /**
     * @return a human friendly representation of this SHA1
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return RevObjects.toString(this);
    }

    /**
     * Converts a {@code String} representation of a hash code into an {@code ObjectId}.
     * 
     * @param hash the string to convert
     * @return the object id represented by its string form, this method is the inverse of
     *         {@link #toString()}
     */
    public static ObjectId valueOf(final String hash) {
        Preconditions.checkNotNull(hash);
        Preconditions.checkArgument(hash.length() == NUM_CHARS, hash,
                String.format("ObjectId.valueOf: Invalid hash string %s", hash));
        //@formatter:off
        int h1 = RevObjects.h1(hash);
        long h2 = RevObjects.h2(hash);
        long h3 = RevObjects.h3(hash);
        //@formatter:on
        return create(h1, h2, h3);
    }

    /**
     * Converts a (possibly partial) {@code String} representation of an {@link ObjectId} into a
     * byte array.
     * 
     * @pre {@code hash.length <= 20}
     * @pre all the characters in {@code hash} are hexadecimal digits
     * @param hash the string to convert
     * @return the byte array represented by its string form
     */
    public static byte[] toRaw(final String hash) {
        Preconditions.checkNotNull(hash);
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            if (-1 == Character.digit(c, 16)) {
                throw new IllegalArgumentException("At index " + i
                        + ": partialId is not a valid hash subsequence '" + hash + "'");
            }
        }

        final byte[] raw = new byte[hash.length() / 2];
        final int radix = 16;
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) Integer.parseInt(hash.substring(2 * i, 2 * i + 2), radix);
        }
        return raw;
    }

    /**
     * Implementation of {@link Comparable#compareTo(Object)} that compares the hash code bytes
     * treating them as unsigned bytes.
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(final ObjectId o) {
        if (this == o) {
            return 0;
        }
        int c = UnsignedInts.compare(h1, o.h1);
        if (c == 0) {
            c = UnsignedLongs.compare(h2, o.h2);
            if (c == 0) {
                c = UnsignedLongs.compare(h3, o.h3);
            }
        }
        return c;
    }

    /**
     * @return a raw byte array of the hash code for this object id. Changes to the returned array
     *         do not affect this object.
     */
    public byte[] getRawValue() {
        byte[] raw = new byte[NUM_BYTES];
        getRawValue(raw);
        return raw;
    }

    /**
     * Copies the 20 bytes of this objcetid's internal SHA-1 hash into {@code target}, starting at
     * {@code target}'s index zero.
     * 
     * @pre {@code 0 <= length <= 20}
     * @pre {@code target.length >= 20}
     * @param target the byte array where to copy the 20 bytes of this SHA-1 hash sequence.
     */
    public void getRawValue(byte[] target) {
        getRawValue(target, NUM_BYTES);
    }

    /**
     * Copies the first {@code length} bytes of this objcetid's internal SHA-1 hash into
     * {@code target}, starting at {@code target}'s index zero.
     * 
     * @pre {@code 0 <= length <= length}
     * @pre {@code target.length >= length}
     * @param target the byte array where to copy the specified number of bytes of this SHA-1 hash
     *        sequence.
     * @param length how many bytes
     */
    public void getRawValue(byte[] target, final int length) {
        Preconditions.checkArgument(length >= 0);
        Preconditions.checkArgument(length <= NUM_BYTES);
        Preconditions.checkArgument(target.length >= length);
        for (int i = 0; i < length; i++) {
            target[i] = (byte) byteN(i);
        }
    }

    /**
     * Returns the value of this ObjectId's internal hash at the given index without having to go
     * through {@link #getRawValue()} and hence create excessive defensive copies of the byte array.
     * 
     * @pre {@code 0 <= inded < 20}
     * @param index the index of the byte inside this objectid's internal hash to return
     * @return the byte at the given index as an integer
     */
    public int byteN(final int index) {
        Preconditions.checkArgument(index >= 0 && index < NUM_BYTES);
        long word;
        int byteOffset;

        if (index < 4) {
            word = h1;
            byteOffset = 3 - index;
        } else if (index < 12) {
            word = h2;
            byteOffset = 7 - (index - 4);
        } else {
            word = h3;
            byteOffset = 7 - (index - 12);
        }

        int byteN;
        int bitOffset = byteOffset * 8;
        byteN = (int) (word >>> bitOffset) & 0xFF;
        return byteN;
    }

    private static int byteN(final byte[] raw, final int index) {
        return raw[index] & 0xFF;
    }

    public static ObjectId readFrom(DataInput in) throws IOException {
        int h1 = in.readInt();
        long h2 = in.readLong();
        long h3 = in.readLong();
        return create(h1, h2, h3);
    }

    public void writeTo(DataOutput out) throws IOException {
        out.writeInt(h1);
        out.writeLong(h2);
        out.writeLong(h3);
    }
}
