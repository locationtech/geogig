package org.locationtech.geogig.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.hash.HashCode;

/**
 * A set of utility methods to work with revision objects
 *
 * 
 * @since 1.0
 */
public class RevObjects {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * Utility method to quickly hash a String and create an ObjectId out of the string SHA-1 hash.
     * <p>
     * Note this method is to hash a string, and is used for testing, not to convert the string
     * representation of an ObjectId. Use {@link ObjectId#valueOf(String)} for that purpose.
     * </p>
     * 
     * @param strToHash
     * @return the {@code ObjectId} generated from the string
     */
    public static ObjectId forString(final String strToHash) {
        Preconditions.checkNotNull(strToHash);
        HashCode hashCode = ObjectId.HASH_FUNCTION.hashString(strToHash, Charset.forName("UTF-8"));
        return ObjectId.createNoClone(hashCode.asBytes());
    }

    public static StringBuilder toString(final ObjectId id, final int byteLength,
            StringBuilder target) {
        Preconditions.checkNotNull(id);
        Preconditions.checkArgument(byteLength > 0 && byteLength <= ObjectId.NUM_BYTES);

        StringBuilder sb = target == null ? new StringBuilder(2 * byteLength) : target;
        byte b;
        for (int i = 0; i < byteLength; i++) {
            b = (byte) id.byteN(i);
            sb.append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
        }
        return sb;
    }

    /**
     * @return an iterator over the <b>direct</b> {@link RevTree#trees() trees} and
     *         {@link RevTree#features() feature} children collections, in the order mandated by the
     *         provided {@code comparator}
     */
    public static Iterator<Node> children(RevTree tree, Comparator<Node> comparator) {
        checkNotNull(comparator);
        ImmutableList<Node> trees = tree.trees().or(ImmutableList.<Node> of());
        ImmutableList<Node> features = tree.features().or(ImmutableList.<Node> of());
        if (trees.isEmpty()) {
            return features.iterator();
        }
        if (features.isEmpty()) {
            return trees.iterator();
        }
        return Iterators.mergeSorted(ImmutableList.of(trees.iterator(), features.iterator()),
                comparator);
    }

}
