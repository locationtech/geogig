/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.storage;

import java.io.Serializable;

import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevTree;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.google.common.primitives.UnsignedLong;

/**
 * Implements storage order of {@link Node} based on the non cryptographic 64-bit <a
 * href="http://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function">FNV-1a</a>
 * variation of the "Fowler/Noll/Vo" hash algorithm.
 * <p>
 * This class mandates in which order {@link Node nodes} are stored inside {@link RevTree trees},
 * hence defining the prescribed order in which tree elements are traversed, regardless of in how
 * many subtrees a given tree is split into.
 * <p>
 * The resulting structure where a given node (identified by its name) always falls on the same
 * bucket (subtree) for a given subtree depth makes it possible to compute diffs between two trees
 * very quickly, by traversing both trees in parallel and finding both bucket and node differences
 * and skipping equal bucket trees, as two buckets at the same depth with the same contents will
 * always hash out to the same {@link ObjectId}.
 * <p>
 * The FNV-1 hash for a node name is computed as in the following pseudo-code:
 * 
 * <pre>
 * <code>
 * hash = FNV_offset_basis
 * for each octet_of_data to be hashed
 *      hash = hash × FNV_prime
 *      hash = hash XOR octet_of_data
 * return hash
 * </code>
 * </pre>
 * 
 * Where {@code FNV_offset_basis} is the 64-bit literal {@code 0xcbf29ce484222325}, and
 * {@code FNV_prime} is the 64-bit literal {@code 0x100000001b3}.
 * <p>
 * To compute the node name hash, each two-byte char in the node name produces two
 * {@code octet_of_data}, in big-endian order.
 * <p>
 * This hash function proved to be extremely fast while maintaining a good distribution and low
 * collision rate, and is widely used by the computer science industry as explained <a
 * href="http://www.isthe.com/chongo/tech/comp/fnv/index.html#FNV-param">here</a> when speed is
 * needed in contrast to cryptographic security.
 * 
 * @since 0.6
 */
public final class NodePathStorageOrder extends Ordering<String> implements Serializable {

    private static final long serialVersionUID = -685759544293388523L;

    private static final FNV1a64bitHash hashOrder = new FNV1a64bitHash();

    @Override
    public int compare(String p1, String p2) {
        return hashOrder.compare(p1, p2);
    }

    /**
     * Computes the bucket index that corresponds to the given node name at the given depth.
     * 
     * @return and Integer between zero and {@link RevTree#MAX_BUCKETS} minus one
     */
    public Integer bucket(final String nodeName, final int depth) {

        final int byteN = hashOrder.byteN(nodeName, depth);
        Preconditions.checkState(byteN >= 0);
        Preconditions.checkState(byteN < 256);

        final int maxBuckets = RevTree.MAX_BUCKETS;

        final int bucket = (byteN * maxBuckets) / 256;
        return Integer.valueOf(bucket);
    }

    public UnsignedLong hashCodeLong(String name) {
        UnsignedLong fnv = FNV1a64bitHash.fnv(name);
        return fnv;
    }

    /**
     * The FNV-1a hash function used as {@link Node} storage order.
     */
    private static class FNV1a64bitHash implements Serializable {

        private static final long serialVersionUID = -1931193743208260766L;

        private static final UnsignedLong FNV64_OFFSET_BASIS = UnsignedLong
                .valueOf("14695981039346656037");

        private static final UnsignedLong FNV64_PRIME = UnsignedLong.valueOf("1099511628211");

        public int compare(final String p1, final String p2) {
            UnsignedLong hash1 = fnv(p1);
            UnsignedLong hash2 = fnv(p2);
            return hash1.compareTo(hash2);
        }

        private static UnsignedLong fnv(CharSequence chars) {
            final int length = chars.length();

            UnsignedLong hash = FNV64_OFFSET_BASIS;

            for (int i = 0; i < length; i++) {
                char c = chars.charAt(i);
                byte b1 = (byte) (c >> 8);
                byte b2 = (byte) c;
                hash = update(hash, b1);
                hash = update(hash, b2);
            }
            return hash;
        }

        private static UnsignedLong update(UnsignedLong hash, final byte octet) {
            // it's ok to use the signed long value here, its a bitwise operation anyways, and its
            // on the lower byte of the long value
            final long longValue = hash.longValue();
            final long bits = longValue ^ octet;

            // System.err.println("hash : " + Long.toBinaryString(longValue));
            // System.err.println("xor  : " + Long.toBinaryString(bits));
            // System.err.println("octet: " + Integer.toBinaryString(octet));

            // convert back to unsigned long
            hash = UnsignedLong.fromLongBits(bits);
            // multiply by prime
            hash = hash.times(FNV64_PRIME);
            return hash;
        }

        /**
         * Returns the Nth unsigned byte in the hash of {@code nodeName} where N is given by
         * {@code depth}
         */
        public int byteN(final String nodeName, final int depth) {
            Preconditions.checkArgument(depth < 8, "depth too deep: %s", Integer.valueOf(depth));

            final long longBits = fnv(nodeName).longValue();

            final int displaceBits = 8 * (7 - depth);// how many bits to right shift longBits to get
                                                     // the byte N

            final int byteN = ((byte) (longBits >> displaceBits)) & 0xFF;
            return byteN;
        }
    }
}