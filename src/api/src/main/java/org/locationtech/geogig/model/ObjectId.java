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
import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.UnsignedBytes;

/**
 * A unique identifier for a {@link RevObject}, which is created by passing a {@link HashFunction}
 * to {@link HashObjectFunnels}.
 * 
 * @apiNote the {@code ObjectId} effectively encloses a 20-byte byte array which is the
 *          <a href="https://en.wikipedia.org/wiki/SHA-1">SHA-1</a> hash resulting
 * 
 * @since 1.0
 */
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

    private final byte[] hashCode;

    /**
     * Constructs a new {@code NULL} object id.
     */
    public ObjectId() {
        this.hashCode = NULL.hashCode;
    }

    /**
     * Constructs a new object id with the given byte code.
     * 
     * @param raw the byte code to use
     */
    public ObjectId(byte[] raw) {
        this(raw, true);
    }

    private ObjectId(byte[] raw, boolean cloneArg) {
        Preconditions.checkNotNull(raw);
        Preconditions.checkArgument(raw.length == NUM_BYTES, "expected a byte[%s], got byte[%s]",
                NUM_BYTES, raw.length);
        this.hashCode = cloneArg ? raw.clone() : raw;
    }

    public static ObjectId createNoClone(byte[] rawHash) {
        return new ObjectId(rawHash, false);
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
        if (o == this) {
            return true;
        }
        if (!(o instanceof ObjectId)) {
            return false;
        }
        return Arrays.equals(hashCode, ((ObjectId) o).hashCode);
    }

    /**
     * @return a hash code based on the contents of the byte array.
     */
    @Override
    public int hashCode() {
        return 17 ^ ((hashCode[0] & 0xFF)//
                | ((hashCode[1] & 0xFF) << 8)//
                | ((hashCode[2] & 0xFF) << 16)//
                | ((hashCode[3] & 0xFF) << 24));
    }

    /**
     * @return a human friendly representation of this SHA1
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return RevObjects.toString(this, NUM_BYTES, new StringBuilder(2 * NUM_BYTES)).toString();
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

        // this is perhaps the worse way of doing this...

        final byte[] raw = new byte[NUM_BYTES];
        final int radix = 16;
        for (int i = 0; i < NUM_BYTES; i++) {
            raw[i] = (byte) Integer.parseInt(hash.substring(2 * i, 2 * i + 2), radix);
        }
        return new ObjectId(raw, false);
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
        byte[] left = this.hashCode;
        byte[] right = o.hashCode;
        return UnsignedBytes.lexicographicalComparator().compare(left, right);
    }

    /**
     * @return a raw byte array of the hash code for this object id. Changes to the returned array
     *         do not affect this object.
     */
    public byte[] getRawValue() {
        return hashCode.clone();
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
        System.arraycopy(hashCode, 0, target, 0, length);
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
        int b = this.hashCode[index] & 0xFF;
        return b;
    }

    public static ObjectId readFrom(DataInput in) throws IOException {
        byte[] rawid = new byte[ObjectId.NUM_BYTES];
        in.readFully(rawid);
        return new ObjectId(rawid);
    }

    public void writeTo(DataOutput out) throws IOException {
        out.write(this.hashCode);
    }
}
