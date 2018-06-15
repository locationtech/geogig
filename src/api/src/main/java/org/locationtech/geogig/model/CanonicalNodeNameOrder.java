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

import java.io.Serializable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.google.common.primitives.UnsignedLong;

/**
 * Defines storage order of {@link RevTree tree} {@link Node nodes} based on the non cryptographic
 * 64-bit
 * <a href="http://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function">FNV-1a</a>
 * variation of the "Fowler/Noll/Vo" hash algorithm.
 * <p>
 * This class mandates in which order {@link Node nodes} are stored inside {@link RevTree trees},
 * hence defining the canonical order in which tree elements are traversed, regardless of in how
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
 * collision rate, and is widely used by the computer science industry as explained
 * <a href="http://www.isthe.com/chongo/tech/comp/fnv/index.html#FNV-param">here</a> when speed is
 * needed in contrast to cryptographic security.
 * 
 * <p>
 * The table bellow shows the {@link RevTree} structure and capacity in its canonical form as
 * defined by this class, for the first eight levels of depth, after which {@code buckets per node}
 * is always {@code 2} and {@code #features per leaf tree} always {@code 256}.
 * <p>
 * This structure is fixed and intends to balance out the growth of the trees as feature nodes are
 * added to provide for a good maximum capacity without too many levels of nesting, nor too few at
 * the cost of an exponential growth on the number of internal tree nodes.
 * 
 * <pre>
 * <code>
 * Depth #buckets    Leaf trees     Total # of     #features    Max #features
 *       per node                   tree objects   per leaf 
 *                                                 tree
 * --------------------------------------------------------------------------
 * 0       32              32               33     512                 16,384
 * 1       32           1,024            1,056     512                524,288
 * 2       32          32,768           33,792     512             16,777,216
 * 3        8         262,144          294,912     256             67,108,864
 * 4        8       2,097,152        2,359,296     256            536,870,912
 * 5        4       8,388,608       10,485,760     256          2,147,483,648
 * 6        4      33,554,432       41,943,040     256          8,589,934,592
 * 7        2      67,108,864      100,663,296     256         17,179,869,184
 * </code>
 * </pre>
 * 
 * The {@code "#buckets per node"} column (which matches the {@link #maxBucketsForLevel} method)
 * represents in how many subtrees a leaf tree that reached it's maximum capacity is split into at
 * every depth index (where the leaf tree maximum capacity is given by the
 * {@code "#features per leaf tree"} column, matching the method {@link #normalizedSizeLimit}).
 * <p>
 * The {@code "Leaf trees"} column represents how many leaf trees result from splitting the tree at
 * that level of nesting.
 * <p>
 * The {@code "Max #features"} column shows what's the maximum number of feature nodes a tree split
 * at that level of nesting can contain.
 * 
 * @since 1.0
 */
public final class CanonicalNodeNameOrder extends Ordering<String> implements Serializable {

    private static final long serialVersionUID = -685759544293388523L;

    private static final FNV1a64bitHash hashOrder = new FNV1a64bitHash();

    public static final CanonicalNodeNameOrder INSTANCE = new CanonicalNodeNameOrder();

    @Override
    public int compare(String p1, String p2) {
        return hashOrder.compare(p1, p2);
    }

    public int compare(final long longBits1, final String p1, final long longBits2, String p2) {
        return hashOrder.compare(longBits1, p1, longBits2, p2);
    }

    /**
     * Returns the canonical max size of a leaf tree for the given depth index; hard limit, can't be
     * changed or would affect the hash of trees.
     * <p>
     * The combination of {@link #maxBucketsForLevel(int) maxBucketsForLevel(int depthIndex)} and
     * {@code normalizedSizeLimit(int depthIndex)} defines the maximum capacity for a tree of a
     * given depth.
     * 
     * @param depthIndex the depth index (starting at zero) of a leaf for which to return the
     *        allowed maximum number of feature nodes before needing to split it into the number of
     *        buckets given by {@link #maxBucketsForLevel} for the same depth.
     * 
     * @return {@code 512} for depth indexes {@code 0} to {@code 2}, and {@code 256} for depth
     *         indexes bigger than {@code 2}
     * @since 1.0
     */
    public static int normalizedSizeLimit(final int depthIndex) {
        Preconditions.checkArgument(depthIndex > -1,
                "depthIndex must be a positive integer or zero");
        switch (depthIndex) {
        case 0:
        case 1:
        case 2:
            return 512;
        default:
            return 256;
        }
    }

    /**
     * Returns a positive integer defining in how many <i>bucket trees</i> a <i>leaf tree</i> that
     * has reached its {@link #getNormalizedSizeLimit maximum capacity} is split into, at the given
     * depth index.
     * <p>
     * E.g., if a feature tree is to be split into buckets, then this method must be called with
     * {@code depthIndex == 0}; a bucket tree that's a direct child of a feature tree with
     * {@code depthIndex == 1}, and so forth.
     * <p>
     * The combination of {@code maxBucketsForLevel(int depthIndex)} and
     * {@link #normalizedSizeLimit(int) normalizedSizeLimit(int depthIndex)} defines the maximum
     * capacity of a tree of a given depth.
     * 
     * @param depthIndex the depth index (starting at zero) of a leaf tree that needs to be split
     *        into buckets.
     * @return {@code 32} for depth indexes {@code 0} to {@code 2}; {@code 8} for depth indexes
     *         {@code 3} and {@code 4}; {@code 4} for depth indexes {@code 5} and {@code 6}; and
     *         {@code 2} for any depth index bigger than 6.
     * @since 1.0
     */
    public static int maxBucketsForLevel(final int depthIndex) {
        Preconditions.checkArgument(depthIndex > -1,
                "depthIndex must be a positive integer or zero (%s)", depthIndex);
        switch (depthIndex) {
        case 0:
        case 1:
        case 2:
            return 32;
        case 3:
        case 4:
            return 8;
        case 5:
        case 6:
            return 4;
        default:
            return 2;
        }
    }

    /**
     * Computes the bucket index (zero based) that corresponds to the given node name at the given
     * (zero based) depth.
     * 
     * @return and Integer between zero and {@link #maxBucketsForLevel
     *         maxBucketsForLevel(depthIndex)} minus one
     */
    public static Integer bucket(final String nodeName, final int depthIndex) {
        final long longBits = hashOrder.fnvBits(nodeName);
        final int bucket = hashOrder.bucket(longBits, depthIndex);
        return Integer.valueOf(bucket);
    }

    public static int[] allBuckets(final String nodeName) {
        final long longBits = hashOrder.fnvBits(nodeName);
        final int maxDepth = 8;
        int[] indices = new int[maxDepth];
        for (int i = 0; i < maxDepth; i++) {
            indices[i] = hashOrder.bucket(longBits, i);
        }
        return indices;
    }

    /**
     * Given a feature name's {@link #hashCodeLong(String) long hashcode}, computes the bucket index
     * that corresponds to the node name at the given depth.
     * 
     * @return and Integer between zero and {@link #maxBucketsForLevel
     *         maxBucketsForLevel(depthIndex)} minus one
     */
    public static int bucket(final long hashCodeLong, final int depthIndex) {
        final int bucket = hashOrder.bucket(hashCodeLong, depthIndex);
        return bucket;
    }

    /**
     * Computes the
     * <a href="http://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function">FNV-1a
     * </a> hash code for the given feature identifier, as an unsigned long.
     * 
     * @param featureIdentifier
     * @return an unsigned long representing the FNV-1a hash code for the given feature identifier.
     */
    public UnsignedLong hashCodeLong(String featureIdentifier) {
        UnsignedLong fnv = hashOrder.fnv(featureIdentifier);
        return fnv;
    }

    /**
     * The FNV-1a hash function used as {@link Node} storage order.
     * <p>
     * Should match the following formula:
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
     */
    private static class FNV1a64bitHash implements Serializable {

        private static final long serialVersionUID = -1931193743208260766L;

        private static final UnsignedLong FNV64_OFFSET_BASIS = UnsignedLong
                .valueOf("14695981039346656037");

        private static final UnsignedLong FNV64_PRIME = UnsignedLong.valueOf("1099511628211");

        public int compare(final String p1, final String p2) {
            final long longBits1 = fnvBits(p1);
            final long longBits2 = fnvBits(p2);

            return compare(longBits1, p1, longBits2, p2);
        }

        private int compare(final long longBits1, final String p1, final long longBits2,
                final String p2) {
            for (int i = 0; i < 8; i++) {
                int bucket1 = bucket(longBits1, i);
                int bucket2 = bucket(longBits2, i);
                if (bucket1 > bucket2) {
                    return 1;
                } else if (bucket2 > bucket1) {
                    return -1;
                }
            }
            if (!p1.equals(p2)) {
                // They fall on the same bucket all the way down to the last level. Fall back to
                // canonical string sorting
                return p1.compareTo(p2);
            }
            return 0;
        }

        private int bucket(long longBits, int depthIndex) {
            final int byteN = byteN(longBits, depthIndex);

            final int maxBuckets = CanonicalNodeNameOrder.maxBucketsForLevel(depthIndex);

            final int bucket = (byteN * maxBuckets) / 256;
            return bucket;
        }

        private UnsignedLong fnv(CharSequence chars) {
            long hash = fnvBits(chars);
            return UnsignedLong.fromLongBits(hash);
        }

        private long fnvBits(CharSequence chars) {
            final int length = chars.length();

            long hash = FNV64_OFFSET_BASIS.longValue();

            for (int i = 0; i < length; i++) {
                char c = chars.charAt(i);
                byte b1 = (byte) (c >> 8);
                byte b2 = (byte) c;
                hash = update(hash, b1);
                hash = update(hash, b2);
            }
            return hash;
        }

        private static long update(long hash, final byte octet) {
            // it's ok to use the signed long value here, its a bitwise operation anyways, and its
            // on the lower byte of the long value
            final long longValue = hash;
            final long bits = longValue ^ octet;

            // convert back to unsigned long
            // hash = UnsignedLong.fromLongBits(bits);
            // multiply by prime
            // /hash = hash.times(FNV64_PRIME);

            // this does the same than the above commented out block without an extra object
            // allocation per call
            return bits * FNV64_PRIME.longValue();

            // return hash;
        }

        /**
         * Returns the Nth unsigned byte in the hash of a given node name where N is given by
         * {@code depth}
         */
        private int byteN(final long longBits, final int depth) {
            Preconditions.checkArgument(depth < 8, "depth too deep: %s", Integer.valueOf(depth));
            final int displaceBits = 8 * (7 - depth);// how many bits to right shift longBits to get
                                                     // the byte N

            final int byteN = ((byte) (longBits >> displaceBits)) & 0xFF;
            return byteN;
        }
    }
}