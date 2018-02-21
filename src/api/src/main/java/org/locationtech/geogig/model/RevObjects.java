/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Comparator;
import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

/**
 * A set of utility methods to work with revision objects
 *
 * 
 * @since 1.0
 */
public class RevObjects {

    /**
     * An identifier for a null coordinate reference system.
     */
    public static final String NULL_CRS_IDENTIFIER = "urn:ogc:def:crs:EPSG::0";

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * Creates a hexadecimal encoding representation of the first {@code numBytes} bytes of the
     * SHA-1 hashcode represented by {@code id} and appends it to {@code target}.
     * 
     * @pre {@code 0 < numBytes <= 20 }
     * @param id the {@code ObjectId} representing the SHA-1 hash to encode as an hex string
     * @param numBytes
     * @param target the {@code StringBuilder} where to append the string representation of the
     *        {@link ObjectId}
     * @return {@code target}
     */
    public static StringBuilder toString(final ObjectId id, final int numBytes,
            StringBuilder target) {

        Preconditions.checkNotNull(id);
        Preconditions.checkArgument(numBytes > 0 && numBytes <= ObjectId.NUM_BYTES);

        StringBuilder sb = target == null ? new StringBuilder(2 * numBytes) : target;
        int b;
        for (int i = 0; i < numBytes; i++) {
            b = id.byteN(i);
            sb.append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
        }
        return sb;
    }

    /**
     * Creates and returns an iterator of the joint lists of {@link RevTree#trees() trees} and
     * {@link RevTree#features() features} of the given {@code RevTree} whose iteration order is
     * given by the provided {@code comparator}.
     * 
     * @return an iterator over the <b>direct</b> {@link RevTree#trees() trees} and
     *         {@link RevTree#features() feature} children collections, in the order mandated by the
     *         provided {@code comparator}
     */
    public static Iterator<Node> children(RevTree tree, Comparator<Node> comparator) {
        checkNotNull(comparator);
        if (tree.treesSize() == 0) {
            return tree.features().iterator();
        }
        if (tree.featuresSize() == 0) {
            return tree.trees().iterator();
        }
        ImmutableList<Node> trees = tree.trees();
        ImmutableList<Node> features = tree.features();
        return Iterators.mergeSorted(ImmutableList.of(trees.iterator(), features.iterator()),
                comparator);
    }

    public static int h1(ObjectId id) {
        return id.h1;
    }

    public static long h2(ObjectId id) {
        return id.h2;
    }

    public static long h3(ObjectId id) {
        return id.h3;
    }

    public static int h1(String hash) {
        Preconditions.checkArgument(hash.length() >= 8);
        //@formatter:off
        int h1 = toInt(
                byteN(hash, 0), 
                byteN(hash, 1), 
                byteN(hash, 2), 
                byteN(hash, 3));
        //@formatter:on
        return h1;
    }

    public static long h2(String hash) {
        Preconditions.checkArgument(hash.length() >= 24);
        //@formatter:off
        long h2 = toLong(
                byteN(hash, 4), 
                byteN(hash, 5), 
                byteN(hash, 6), 
                byteN(hash, 7),
                byteN(hash, 8), 
                byteN(hash, 9), 
                byteN(hash, 10), 
                byteN(hash, 11));
        //@formatter:on
        return h2;
    }

    public static long h3(String hash) {
        Preconditions.checkArgument(hash.length() >= 40);
        //@formatter:off
        long h3 = toLong(
                byteN(hash, 12), 
                byteN(hash, 13), 
                byteN(hash, 14), 
                byteN(hash, 15),
                byteN(hash, 16), 
                byteN(hash, 17), 
                byteN(hash, 18), 
                byteN(hash, 19));
        //@formatter:on
        return h3;
    }

    private static int toInt(byte b1, byte b2, byte b3, byte b4) {
        int i = b1 & 0xFF;
        i = (i << 8) | b2 & 0xFF;
        i = (i << 8) | b3 & 0xFF;
        i = (i << 8) | b4 & 0xFF;
        return i;
    }

    private static long toLong(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7,
            byte b8) {
        long l = b1 & 0xFF;
        l = (l << 8) | b2 & 0xFF;
        l = (l << 8) | b3 & 0xFF;
        l = (l << 8) | b4 & 0xFF;
        l = (l << 8) | b5 & 0xFF;
        l = (l << 8) | b6 & 0xFF;
        l = (l << 8) | b7 & 0xFF;
        l = (l << 8) | b8 & 0xFF;
        return l;
    }

    /**
     * Custom implementation to extract a byte from an hex encoded string without incurring in
     * String.substring()
     */
    private static byte byteN(String hexString, int byteIndex) {
        char c1 = hexString.charAt(2 * byteIndex);
        char c2 = hexString.charAt(2 * byteIndex + 1);
        byte b1 = hexToByte(c1);
        byte b2 = hexToByte(c2);
        int b = b1 << 4;
        b |= b2;
        return (byte) b;
    }

    private static byte hexToByte(char c) {
        switch (c) {
        case '0':
            return 0;
        case '1':
            return 1;
        case '2':
            return 2;
        case '3':
            return 3;
        case '4':
            return 4;
        case '5':
            return 5;
        case '6':
            return 6;
        case '7':
            return 7;
        case '8':
            return 8;
        case '9':
            return 9;
        case 'a':
            return 10;
        case 'b':
            return 11;
        case 'c':
            return 12;
        case 'd':
            return 13;
        case 'e':
            return 14;
        case 'f':
            return 15;
        default:
            throw new IllegalArgumentException();
        }
    }

}
