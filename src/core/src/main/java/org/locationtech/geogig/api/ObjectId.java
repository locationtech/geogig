/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.UnsignedBytes;

/**
 * A {@link RevObject} identifier backed by a hash function (SHA1 for instance)
 */
public final class ObjectId implements Comparable<ObjectId>, Serializable {

    private static final long serialVersionUID = -2445723120477753654L;

    /**
     * A "natural order" {@link Ordering comparator}
     */
    public static final Ordering<ObjectId> NATURAL_ORDER = Ordering.<ObjectId> natural();

    /**
     * ObjectId instance that represents a NULL id.
     */
    public static final ObjectId NULL;

    /**
     * Hash function to create object ids out of its contents (SHA-1)
     */
    public static final HashFunction HASH_FUNCTION;

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
        return (hashCode[0] & 0xFF)//
                | ((hashCode[1] & 0xFF) << 8)//
                | ((hashCode[2] & 0xFF) << 16)//
                | ((hashCode[3] & 0xFF) << 24);
    }

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * @return a human friendly representation of this SHA1
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(2 * NUM_BYTES);
        byte b;
        for (int i = 0; i < NUM_BYTES; i++) {
            b = hashCode[i];
            sb.append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
        }
        return sb.toString();
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
     * Converts a {@code String} representation of a byte code into a byte array.
     * 
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
        return compare(left, right);
    }

    public static int compare(byte[] left, byte[] right) {
        return UnsignedBytes.lexicographicalComparator().compare(left, right);
    }

    /**
     * @return a raw byte array of the hash code for this object id. Changes to the returned array
     *         do not affect this object.
     */
    public byte[] getRawValue() {
        return hashCode.clone();
    }

    public void getRawValue(byte[] target) {
        System.arraycopy(hashCode, 0, target, 0, NUM_BYTES);
    }

    public void getRawValue(byte[] target, int size) {
        System.arraycopy(hashCode, 0, target, 0, size);
    }

    /**
     * Utility method to quickly hash a String and create an ObjectId out of the string SHA-1 hash.
     * <p>
     * Note this method is to hash a string, not to convert the string representation of an
     * ObjectId. Use {@link #valueOf(String)} for that purpose.
     * </p>
     * 
     * @param strToHash
     * @return the {@code ObjectId} generated from the string
     */
    public static ObjectId forString(final String strToHash) {
        Preconditions.checkNotNull(strToHash);
        HashCode hashCode = HASH_FUNCTION.hashString(strToHash, Charset.forName("UTF-8"));
        return new ObjectId(hashCode.asBytes(), false);
    }

    /**
     * Returns the value of this ObjectId's internal hash at the given index without having to go
     * through {@link #getRawValue()} and hence create excessive defensive copies of the byte array.
     * 
     * @param index the index of the byte inside this objectid's internal hash to return
     * @return the byte at the given index as an integer
     */
    public int byteN(int index) {
        int b = this.hashCode[index] & 0xFF;
        return b;
    }
}
