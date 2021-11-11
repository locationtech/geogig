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

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.Name;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.common.hash.Hasher;
import com.google.common.hash.PrimitiveSink;

import lombok.NonNull;

/**
 * Hashes a RevObject and returns the ObjectId.
 * 
 * @see RevObject
 * @see ObjectId
 * 
 * @since 1.0
 */
public class HashObjectFunnels {

    // This random byte code is used to represent null in hashing. This is intended to be something
    // that would be unlikely to duplicated by accident with real data. Changing this will cause all
    // objects that contain null values to hash differently.
    private static final byte[] NULL_BYTE_CODE = { 0x60, (byte) 0xe5, 0x6d, 0x08, (byte) 0xd3, 0x08,
            0x53, (byte) 0xb7, (byte) 0x84, 0x07, 0x77 };

    public static Funnel<RevCommit> commitFunnel() {
        return CommitFunnel.INSTANCE;
    }

    public static Funnel<RevTree> treeFunnel() {
        return TreeFunnel.INSTANCE;
    }

    public static Funnel<Object> valueFunnel() {
        return PropertyValueFunnel;
    }

    public static Funnel<RevFeature> featureFunnel() {
        return FeatureFunnel.INSTANCE;
    }

    public static Funnel<RevTag> tagFunnel() {
        return TagFunnel.INSTANCE;
    }

    public static Funnel<RevFeatureType> featureTypeFunnel() {
        return FeatureTypeFunnel.INSTANCE;
    }

    /**
     * Convenience method to hash a {@link RevFeature} out of its property values
     * 
     * @param into expected to be {@link ObjectId#HASH_FUNCTION} in order to compute the SHA-1 hash
     * @param values the {@link RevFeature} actual values, not {@code Optional}s
     */
    public static void feature(@NonNull PrimitiveSink into, @NonNull List<Object> values) {
        FeatureFunnel.INSTANCE.funnelValues(values, into);
    }

    /**
     * Convenience method to hash a {@link RevTree} out of its values
     * 
     * @param into expected to be {@link ObjectId#HASH_FUNCTION} in order to compute the SHA-1 hash
     * @param trees the tree's {@link RevTree#trees() contained tree nodes}
     * @param features the tree's {@link RevTree#trees() contained feature nodes}
     * @param buckets the tree's {@link RevTree#trees() contained bucket pointers}
     */
    public static void tree(@NonNull PrimitiveSink into, @NonNull List<Node> trees,
            @NonNull List<Node> features, @NonNull Iterable<Bucket> buckets) {
        TreeFunnel.INSTANCE.funnel(into, trees, features, buckets);
    }

    public static @NonNull ObjectId hashTag(@NonNull String name, @NonNull ObjectId commitId,
            @NonNull String message, @NonNull RevPerson tagger) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        HashObjectFunnels.tag(hasher, name, commitId, message, tagger);
        return ObjectId.create(hasher.hash().asBytes());
    }

    public static @NonNull ObjectId hashFeature(@NonNull List<Object> values) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        HashObjectFunnels.feature(hasher, values);
        return ObjectId.create(hasher.hash().asBytes());
    }

    public static @NonNull ObjectId hashCommit(@NonNull ObjectId treeId,
            @NonNull List<ObjectId> parentIds, @NonNull RevPerson author,
            @NonNull RevPerson committer, @NonNull String commitMessage) {

        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        HashObjectFunnels.commit(hasher, treeId, parentIds, author, committer, commitMessage);
        return ObjectId.create(hasher.hash().asBytes());
    }

    public static @NonNull ObjectId hashFeatureType(@NonNull FeatureType featureType) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        HashObjectFunnels.featureType(hasher, featureType);
        return ObjectId.create(hasher.hash().asBytes());
    }

    public static @NonNull ObjectId hashTree(List<Node> trees, List<Node> features,
            Iterable<Bucket> buckets) {

        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        trees = trees == null ? Collections.emptyList() : trees;
        features = features == null ? Collections.emptyList() : features;
        buckets = buckets == null ? Collections.emptySet() : buckets;
        HashObjectFunnels.tree(hasher, trees, features, buckets);

        final byte[] rawKey = hasher.hash().asBytes();
        final ObjectId id = ObjectId.create(rawKey);

        return id;
    }

    private static final class NullableFunnel<T> implements Funnel<T> {

        private static final long serialVersionUID = -1L;

        private Funnel<T> nonNullableFunnel;

        /**
         * @param nonNullableFunnel
         */
        public NullableFunnel(Funnel<T> nonNullableFunnel) {
            this.nonNullableFunnel = nonNullableFunnel;
        }

        public @Override void funnel(T from, PrimitiveSink into) {
            if (from == null) {
                NullFunnel.funnel(from, into);
            } else {
                nonNullableFunnel.funnel(from, into);
            }
        }

        public static <T> Funnel<T> of(Funnel<T> nonNullableFunnel) {
            return new NullableFunnel<T>(nonNullableFunnel);
        }
    }

    private static final Funnel<Object> NullFunnel = new Funnel<Object>() {

        private static final long serialVersionUID = 1L;

        public @Override void funnel(Object from, PrimitiveSink into) {
            Funnels.byteArrayFunnel().funnel(NULL_BYTE_CODE, into);
        }
    };

    private static final Funnel<CharSequence> StringFunnel = Funnels.unencodedCharsFunnel();

    private static final Funnel<CharSequence> NullableStringFunnel = NullableFunnel
            .of(StringFunnel);

    private static final Funnel<RevObject.TYPE> RevObjectTypeFunnel = new Funnel<RevObject.TYPE>() {

        private static final long serialVersionUID = 1L;

        public @Override void funnel(RevObject.TYPE from, PrimitiveSink into) {
            Funnels.integerFunnel().funnel(from.value(), into);
        }
    };

    private static final Funnel<ObjectId> ObjectIdFunnel = new Funnel<ObjectId>() {

        private static final long serialVersionUID = 1L;

        public @Override void funnel(ObjectId from, PrimitiveSink into) {
            for (int i = 0; i < ObjectId.NUM_BYTES; i++) {
                into.putByte((byte) from.byteN(i));
            }
        }
    };

    public static @NonNull ObjectId hashValue(Object value) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        PropertyValueFunnel.funnel(value, hasher);
        final byte[] rawKey = hasher.hash().asBytes();
        final ObjectId id = ObjectId.create(rawKey);
        return id;
    }

    public static @NonNull ObjectId hashObject(@NonNull RevObject o) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        switch (o.getType()) {
        case COMMIT:
            commitFunnel().funnel((RevCommit) o, hasher);
            break;
        case FEATURE:
            featureFunnel().funnel((RevFeature) o, hasher);
            break;
        case FEATURETYPE:
            featureTypeFunnel().funnel((RevFeatureType) o, hasher);
            break;
        case TAG:
            tagFunnel().funnel((RevTag) o, hasher);
            break;
        case TREE:
            treeFunnel().funnel((RevTree) o, hasher);
            break;
        default:
            throw new IllegalArgumentException("Unknown revision object type: " + o.getType());
        }

        final byte[] rawKey = hasher.hash().asBytes();
        final ObjectId id = ObjectId.create(rawKey);
        return id;
    }

    private static final class CommitFunnel implements Funnel<RevCommit> {
        private static final long serialVersionUID = -1L;

        private static final CommitFunnel INSTANCE = new CommitFunnel();

        public @Override void funnel(RevCommit from, PrimitiveSink into) {
            funnel(into, from.getTreeId(), from.getParentIds(), from.getMessage(), from.getAuthor(),
                    from.getCommitter());
        }

        public void funnel(PrimitiveSink into, ObjectId treeId, List<ObjectId> parentIds,
                String message, RevPerson author, RevPerson committer) {
            RevObjectTypeFunnel.funnel(TYPE.COMMIT, into);

            // funnel ObjectId.NULL for backwards compatibility, since prior to geogig 1.2,
            // CommitBuilder was creating a fake commit with NULL as object id before computing the
            // final hash, and this CommitFunnel inadvertently funneling the commit id, which is
            // plain wrong, so if we don't keep funneling this null id commits will hash out
            // differently
            ObjectIdFunnel.funnel(ObjectId.NULL, into);

            ObjectIdFunnel.funnel(treeId, into);
            for (ObjectId parentId : parentIds) {
                ObjectIdFunnel.funnel(parentId, into);
            }
            NullableStringFunnel.funnel(message, into);
            PersonFunnel.funnel(author, into);
            PersonFunnel.funnel(committer, into);
        }
    };

    private static final class TreeFunnel implements Funnel<RevTree> {

        private static final long serialVersionUID = 1L;

        private static final TreeFunnel INSTANCE = new TreeFunnel();

        public @Override void funnel(RevTree from, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.TREE, into);
            from.forEachTree(n -> NodeFunnel.funnel(n, into));
            from.forEachFeature(n -> NodeFunnel.funnel(n, into));

            from.forEachBucket(bucket -> {
                Funnels.integerFunnel().funnel(bucket.getIndex(), into);
                ObjectIdFunnel.funnel(bucket.getObjectId(), into);
            });
        }

        public void funnel(PrimitiveSink into, List<Node> trees, List<Node> features,
                Iterable<Bucket> buckets) {

            RevObjectTypeFunnel.funnel(TYPE.TREE, into);
            trees.forEach(n -> NodeFunnel.funnel(n, into));
            features.forEach(n -> NodeFunnel.funnel(n, into));
            buckets.forEach(b -> {
                Funnels.integerFunnel().funnel(b.getIndex(), into);
                ObjectIdFunnel.funnel(b.getObjectId(), into);
            });
        }
    }

    private static final class FeatureFunnel implements Funnel<RevFeature> {

        private static final long serialVersionUID = 1L;

        private static final FeatureFunnel INSTANCE = new FeatureFunnel();

        public @Override void funnel(RevFeature from, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.FEATURE, into);
            from.forEach(v -> PropertyValueFunnel.funnel(v, into));
        }

        public void funnelValues(List<Object> values, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.FEATURE, into);
            values.forEach(v -> PropertyValueFunnel.funnel(v, into));
        }
    };

    private static final class FeatureTypeFunnel implements Funnel<RevFeatureType> {
        private static final long serialVersionUID = 1L;

        public static final FeatureTypeFunnel INSTANCE = new FeatureTypeFunnel();

        public @Override void funnel(RevFeatureType from, PrimitiveSink into) {
            funnel(into, from.type());
        }

        public void funnel(PrimitiveSink into, FeatureType type) {
            RevObjectTypeFunnel.funnel(TYPE.FEATURETYPE, into);

            Collection<PropertyDescriptor> featureTypeProperties = type.getDescriptors();

            NameFunnel.funnel(type.getName(), into);

            for (PropertyDescriptor descriptor : featureTypeProperties) {
                PropertyDescriptorFunnel.funnel(descriptor, into);
            }
        }
    };

    private static final class TagFunnel implements Funnel<RevTag> {
        private static final long serialVersionUID = 1L;

        public static final TagFunnel INSTANCE = new TagFunnel();

        public @Override void funnel(RevTag from, PrimitiveSink into) {
            funnel(into, from.getName(), from.getCommitId(), from.getMessage(), from.getTagger());
        }

        public void funnel(PrimitiveSink into, String name, ObjectId commitId, String message,
                RevPerson tagger) {
            RevObjectTypeFunnel.funnel(TYPE.TAG, into);
            ObjectIdFunnel.funnel(commitId, into);
            StringFunnel.funnel(name, into);
            StringFunnel.funnel(message, into);
            PersonFunnel.funnel(tagger, into);
        }
    };

    private static final Funnel<RevPerson> PersonFunnel = NullableFunnel
            .of(new Funnel<RevPerson>() {
                private static final long serialVersionUID = -1L;

                public @Override void funnel(RevPerson from, PrimitiveSink into) {
                    NullableStringFunnel.funnel(from.getName().orElse(null), into);
                    NullableStringFunnel.funnel(from.getEmail().orElse(null), into);
                    Funnels.longFunnel().funnel(from.getTimestamp(), into);
                    Funnels.integerFunnel().funnel(from.getTimeZoneOffset(), into);
                }
            });

    private static final Funnel<Node> NodeFunnel = new Funnel<Node>() {
        private static final long serialVersionUID = 1L;

        public @Override void funnel(Node ref, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(ref.getType(), into);
            StringFunnel.funnel((CharSequence) ref.getName(), into);
            ObjectIdFunnel.funnel(ref.getObjectId(), into);
            ObjectIdFunnel.funnel(ref.getMetadataId().orElse(ObjectId.NULL), into);
            Map<String, Object> extraData = ref.getExtraData();
            // consider extraData only if it's not empty to maintain backwards compatibility with
            // Geogig pre 1.1
            if (!extraData.isEmpty()) {
                PropertyValueFunnel.funnel(extraData, into);
            }
        }
    };

    private static final Funnel<Object> PropertyValueFunnel = new Funnel<Object>() {
        private static final long serialVersionUID = 1L;

        public @Override void funnel(final Object value, PrimitiveSink into) {
            final FieldType fieldType = FieldType.forValue(value);
            switch (fieldType) {
            case UNKNOWN:
                throw new IllegalArgumentException(String.format(
                        "Objects of type %s are not supported as property values (%s)",
                        value.getClass().getName(), value));
            case NULL:
                NullFunnel.funnel(value, into);
                break;
            case BOOLEAN:
                into.putBoolean(((Boolean) value).booleanValue());
                break;
            case BYTE:
                into.putByte(((Byte) value).byteValue());
                break;
            case SHORT:
                into.putShort(((Short) value).byteValue());
                break;
            case CHAR:
                into.putChar(((Character) value).charValue());
                break;
            case INTEGER:
                into.putInt(((Integer) value).intValue());
                break;
            case LONG:
                into.putLong(((Long) value).longValue());
                break;
            case FLOAT:
                into.putFloat(((Float) value).floatValue());
                break;
            case DOUBLE:
                into.putDouble(((Double) value).doubleValue());
                break;
            case STRING:
                StringFunnel.funnel((CharSequence) value, into);
                break;
            case BOOLEAN_ARRAY:
            case BYTE_ARRAY:
            case SHORT_ARRAY:
            case INTEGER_ARRAY:
            case LONG_ARRAY:
            case FLOAT_ARRAY:
            case DOUBLE_ARRAY:
            case STRING_ARRAY:
            case CHAR_ARRAY: {
                int length = Array.getLength(value);
                into.putInt(length);
                for (int i = 0; i < length; i++) {
                    Object arrayElem = Array.get(value, i);
                    PropertyValueFunnel.funnel(arrayElem, into);
                }
            }
                break;
            case POINT:
            case LINESTRING:
            case POLYGON:
            case MULTIPOINT:
            case MULTILINESTRING:
            case MULTIPOLYGON:
            case GEOMETRYCOLLECTION:
            case GEOMETRY:
                GeometryFunnel.funnel((Geometry) value, into);
                break;
            case UUID: {
                UUID uuid = (UUID) value;
                long most = uuid.getMostSignificantBits();
                long least = uuid.getLeastSignificantBits();
                into.putLong(most);
                into.putLong(least);
            }
                break;
            case BIG_INTEGER: {
                byte[] bigBytes = ((BigInteger) value).toByteArray();
                into.putBytes(bigBytes);
            }
                break;
            case BIG_DECIMAL: {
                BigDecimal bd = ((BigDecimal) value);
                BigInteger unscaledValue = bd.unscaledValue();
                int scale = bd.scale();
                byte[] bigBytes = unscaledValue.toByteArray();
                into.putBytes(bigBytes);
                into.putInt(scale);
            }
                break;
            case DATETIME:
            case DATE:
            case TIME:
            case TIMESTAMP:
                into.putLong(((java.util.Date) value).getTime());
                break;
            case MAP: {
                @SuppressWarnings("unchecked")
                Map<String, ?> map = (Map<String, ?>) value;
                MapPropertyFunnel.funnel(map, into);
            }
                break;
            case ENVELOPE_2D: {
                Envelope e = (Envelope) value;
                into.putDouble(e.getMinX());
                into.putDouble(e.getMaxX());
                into.putDouble(e.getMinY());
                into.putDouble(e.getMaxY());
            }
                break;
            default:
                throw new RuntimeException(
                        "Unexpected exception, all FieldType enum values shall be covered");
            }
        }
    };

    /**
     * Rounds geometry ordinates to 9 decimals before hashing them
     */
    private static final Funnel<Geometry> GeometryFunnel = new Funnel<Geometry>() {
        private static final long serialVersionUID = 1L;

        public @Override void funnel(final Geometry geom, final PrimitiveSink into) {

            CoordinateFilter filter = new CoordinateFilter() {

                static final double SCALE = 1E9D;

                public @Override void filter(Coordinate coord) {
                    double x = Math.round(coord.x * SCALE) / SCALE;
                    double y = Math.round(coord.y * SCALE) / SCALE;
                    into.putDouble(x);
                    into.putDouble(y);
                }
            };
            geom.apply(filter);
        }
    };

    private static final Funnel<Name> NameFunnel = new Funnel<Name>() {
        private static final long serialVersionUID = 1L;

        public @Override void funnel(Name from, PrimitiveSink into) {
            NullableStringFunnel.funnel(from.getNamespaceURI(), into);
            StringFunnel.funnel((CharSequence) from.getLocalPart(), into);
        }
    };

    private static final Funnel<PropertyDescriptor> PropertyDescriptorFunnel = new Funnel<PropertyDescriptor>() {
        private static final long serialVersionUID = 1L;

        public @Override void funnel(PropertyDescriptor descriptor, PrimitiveSink into) {

            final @NonNull Name name = descriptor.getName();
            final @NonNull Name attrTypeName = descriptor.getTypeName();
            final @NonNull Class<?> binding = descriptor.getBinding();
            final FieldType type = FieldType.forBinding(binding);

            NameFunnel.funnel(name, into);

            NameFunnel.funnel(attrTypeName, into);

            into.putInt(type.getTag());
            into.putBoolean(descriptor.isNillable());

            into.putInt(descriptor.getMaxOccurs());
            into.putInt(descriptor.getMinOccurs());

            if (descriptor.isGeometryDescriptor()) {
                CoordinateReferenceSystem crs = descriptor.coordinateReferenceSystem();
                String srsName = crs.getSrsIdentifier();
                if (srsName == null) {
                    srsName = crs.getWKT();
                }
                if (srsName == null) {
                    srsName = CoordinateReferenceSystem.NULL.getSrsIdentifier();
                }
                NullableStringFunnel.funnel(srsName, into);
            }
        }
    };

    /**
     * Funnels {@link Map}'s entries in sorted key order for consistent results regardless of
     * whether the argument map is sorted or not.
     */
    private static final Funnel<Map<String, ?>> MapPropertyFunnel = new Funnel<Map<String, ?>>() {
        private static final long serialVersionUID = 1L;

        public @Override void funnel(Map<String, ?> map, final PrimitiveSink into) {
            if (!(map instanceof SortedMap)) {
                map = new TreeMap<>(map);
            }

            String key;
            Object value;
            for (Map.Entry<String, ?> e : map.entrySet()) {
                key = e.getKey();
                value = e.getValue();
                StringFunnel.funnel(key, into);
                PropertyValueFunnel.funnel(value, into);
            }
        }
    };

    public static void tag(@NonNull PrimitiveSink into, @NonNull String name,
            @NonNull ObjectId commitId, @NonNull String message, @NonNull RevPerson tagger) {
        TagFunnel.INSTANCE.funnel(into, name, commitId, message, tagger);
    }

    public static void featureType(@NonNull PrimitiveSink into, @NonNull FeatureType featureType) {
        FeatureTypeFunnel.INSTANCE.funnel(into, featureType);
    }

    public static void commit(@NonNull PrimitiveSink into, @NonNull ObjectId treeId,
            @NonNull List<ObjectId> parentIds, @NonNull RevPerson author,
            @NonNull RevPerson committer, @NonNull String commitMessage) {
        CommitFunnel.INSTANCE.funnel(into, treeId, parentIds, commitMessage, author, committer);
    }

}
