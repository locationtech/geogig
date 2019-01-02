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

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Comparator;
import java.util.Iterator;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.locationtech.geogig.model.impl.Float32Bounds;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * A set of utility methods to work with revision objects
 *
 * 
 * @since 1.0
 */
public @UtilityClass class RevObjects {

    /**
     * An identifier for a null coordinate reference system.
     */
    public static final String NULL_CRS_IDENTIFIER = "urn:ogc:def:crs:EPSG::0";

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private static final String DEFAULT_INSTANCE_ENV_ARG = "RevObjectFactory";

    private static RevObjectFactory DEFAULT_FACTORY_INSTANCE;

    /**
     * Implements the {@code RevObjectFactory} lookup mechanism described in
     * {@link RevObjectFactory}'s javadocs
     */
    static RevObjectFactory lookupDefaultFactory() {
        if (DEFAULT_FACTORY_INSTANCE == null) {
            RevObjectFactory defaultInstance = new ServiceFinder()
                    .systemProperty(DEFAULT_INSTANCE_ENV_ARG)
                    .environmentVariable(DEFAULT_INSTANCE_ENV_ARG)
                    .lookupDefaultService(RevObjectFactory.class);
            DEFAULT_FACTORY_INSTANCE = defaultInstance;
        }
        return DEFAULT_FACTORY_INSTANCE;
    }

    public static Envelope boundsOf(@NonNull RevTree tree) {
        Envelope env = new Envelope();
        tree.forEachBucket(bucket -> bucket.expand(env));
        tree.forEachTree(n -> n.expand(env));
        tree.forEachFeature(n -> n.expand(env));
        return env;
    }

    public static String toString(@NonNull ObjectId id) {
        return RevObjects
                .toString(id, ObjectId.NUM_BYTES, new StringBuilder(2 * ObjectId.NUM_BYTES))
                .toString();
    }

    public static String toShortString(@NonNull ObjectId id) {
        return RevObjects.toString(id, 8, new StringBuilder(16)).toString();
    }

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

    public static boolean equals(@NonNull RevPerson p1, @NonNull RevPerson person) {
        return equal(p1.getName(), person.getName()) && equal(p1.getEmail(), person.getEmail())
                && p1.getTimestamp() == person.getTimestamp()
                && p1.getTimeZoneOffset() == person.getTimeZoneOffset();
    }

    public static int hashCode(@NonNull RevPerson p) {
        return Objects.hashCode(p.getName(), p.getEmail(), p.getTimestamp(), p.getTimeZoneOffset());
    }

    public static String toString(@NonNull RevPerson p) {
        return String.format("%s[%s]", p.getClass().getSimpleName(), toShortString(p));
    }

    public static String toShortString(@NonNull RevPerson p) {
        return String.format("\"%s\" <%s>, time: %d, tz: %d", p.getName().orNull(),
                p.getEmail().orNull(), p.getTimestamp(), p.getTimeZoneOffset());
    }

    public static boolean equals(@NonNull RevObject object, @Nullable Object o) {
        return (o instanceof RevObject) && object.getId().equals(((RevObject) o).getId());
    }

    public static boolean equals(@NonNull Bucket bucket, @Nullable Object o) {
        return (o instanceof Bucket) && bucket.getIndex() == ((Bucket) o).getIndex()
                && bucket.getObjectId().equals(((Bucket) o).getObjectId());
    }

    public static int hashCode(@NonNull Bucket bucket) {
        return 31 * bucket.getIndex() + bucket.getObjectId().hashCode();
    }

    public static String toString(@NonNull Bucket bucket) {
        Envelope env = bucket.bounds().orNull();
        String bounds = env == null ? null : env.toString();
        return String.format("%s[index: %d, tree:%s, bounds: %s]", //
                bucket.getClass().getSimpleName(), //
                bucket.getIndex(), //
                toShortString(bucket.getObjectId()), //
                bounds);
    }

    public static int hashCode(@NonNull Node node) {
        return 17 ^ node.getType().hashCode() * node.getName().hashCode()
                * node.getObjectId().hashCode();
    }

    /**
     * Equality check based on {@link #getName() name}, {@link #getType() type}, and
     * {@link #getObjectId() objectId}; {@link #getMetadataId()} is NOT part of the equality check.
     */
    public static boolean equals(@NonNull Node node, @Nullable Object o) {
        if (o instanceof Node) {
            Node r = (Node) o;
            return node.getType().equals(r.getType()) && node.getName().equals(r.getName())
                    && node.getObjectId().equals(r.getObjectId());
        }
        return false;
    }

    public static String toString(@NonNull Node node) {
        Envelope env = node.bounds().orNull();
        String bounds = env == null ? null : env.toString();
        return String.format("%s[%s -> %s, type: %s, md id: %s, bounds: %s]", //
                node.getClass().getSimpleName(), //
                node.getName(), //
                toShortString(node.getObjectId()), //
                node.getType(), //
                (node.getMetadataId().isPresent() ? toShortString(node.getMetadataId().get())
                        : "NULL"), //
                bounds);
    }

    public static int hashCode(@NonNull RevObject o) {
        return RevObjects.h1(o.getId());
    }

    public static String toString(@NonNull RevCommit c) {
        return String.format("%s(%s)[tree:%s, parents:%s, msg:%s, author:%s, committer:%s]",
                c.getClass().getSimpleName(), //
                toShortString(c.getId()), //
                toShortString(c.getTreeId()), //
                c.getParentIds().stream().map(RevObjects::toShortString)
                        .collect(Collectors.toList()), //
                c.getMessage(), //
                toShortString(c.getAuthor()), //
                toShortString(c.getCommitter()));
    }

    public static String toString(@NonNull ValueArray v) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (int i = 0; i < v.size(); i++) {
            Optional<Object> value = v.get(i);
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            String valueString = String.valueOf(value.orNull());
            builder.append(valueString.substring(0, Math.min(10, valueString.length())));
        }
        builder.append(']');
        return builder.toString();
    }

    public static String toString(@NonNull RevFeature f) {
        return String.format("%s(%s)%s", //
                f.getClass().getSimpleName(), //
                toShortString(f.getId()), //
                toString(((ValueArray) f)));
    }

    public static String toString(@NonNull RevFeatureType ft) {
        StringBuilder builder = new StringBuilder(ft.getClass().getSimpleName());
        builder.append('(').append(toShortString(ft.getId())).append(")[");
        boolean first = true;
        for (PropertyDescriptor desc : ft.descriptors()) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append(desc.getName().getLocalPart());
            builder.append(": ");
            Class<?> binding = desc.getType().getBinding();
            FieldType fieldType = FieldType.forBinding(binding);
            builder.append(binding.getSimpleName());
            builder.append("->");
            builder.append(fieldType);
        }
        builder.append(']');
        return builder.toString();
    }

    public static String toString(@NonNull RevTag t) {
        return String.format("%s(%s)[commit:%s, message:%s, tagger:%s]",
                t.getClass().getSimpleName(), //
                toShortString(t.getId()), //
                toShortString(t.getCommitId()), //
                t.getMessage(), //
                toShortString(t.getTagger()));
    }

    public static String toString(@NonNull RevTree tree) {
        String stree = String.format(
                "%s(%s)[size:%,d, tree nodes:%,d, feature nodes:%,d, subtrees:%,d, buckets: %,d]",
                tree.getClass().getSimpleName(), //
                toShortString(tree.getId()), //
                tree.size(), //
                tree.treesSize(), //
                tree.featuresSize(), //
                tree.numTrees(), //
                tree.bucketsSize());

        StringBuilder sb = new StringBuilder(stree);
        sb.append("\n[\ntrees:");
        tree.forEachTree(n -> sb.append('\n').append(n));
        sb.append("\nfeatures:");
        tree.forEachFeature(n -> sb.append('\n').append(n));
        sb.append("\nbuckets:");
        tree.forEachBucket(b -> sb.append("\n").append(b));
        sb.append("\n]");
        return sb.toString();
    }

    public static @Nullable Envelope makePrecise(@Nullable Envelope bounds) {
        Envelope float32Bounds = Float32Bounds.valueOf(bounds).asEnvelope();
        return float32Bounds.isNull() ? null : float32Bounds;
    }
}
