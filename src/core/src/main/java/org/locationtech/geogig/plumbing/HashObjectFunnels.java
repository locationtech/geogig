/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.common.hash.PrimitiveSink;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateFilter;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Hashes a RevObject and returns the ObjectId.
 * 
 * @see RevObject
 * @see ObjectId
 */
class HashObjectFunnels {

    // This random byte code is used to represent null in hashing. This is intended to be something
    // that would be unlikely to duplicated by accident with real data. Changing this will cause all
    // objects that contain null values to hash differently.
    private static final byte[] NULL_BYTE_CODE = { 0x60, (byte) 0xe5, 0x6d, 0x08, (byte) 0xd3, 0x08,
            0x53, (byte) 0xb7, (byte) 0x84, 0x07, 0x77 };

    public static CommitFunnel commitFunnel() {
        return CommitFunnel.INSTANCE;
    }

    public static TreeFunnel treeFunnel() {
        return TreeFunnel.INSTANCE;
    }

    public static FeatureFunnel featureFunnel() {
        return FeatureFunnel.INSTANCE;
    }

    public static TagFunnel tagFunnel() {
        return TagFunnel.INSTANCE;
    }

    public static FeatureTypeFunnel featureTypeFunnel() {
        return FeatureTypeFunnel.INSTANCE;
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

        @Override
        public void funnel(T from, PrimitiveSink into) {
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

        @Override
        public void funnel(Object from, PrimitiveSink into) {
            Funnels.byteArrayFunnel().funnel(NULL_BYTE_CODE, into);
        }
    };

    private static final Funnel<CharSequence> StringFunnel = Funnels.unencodedCharsFunnel();

    private static final Funnel<CharSequence> NullableStringFunnel = NullableFunnel
            .of(StringFunnel);

    private static final Funnel<RevObject.TYPE> RevObjectTypeFunnel = new Funnel<RevObject.TYPE>() {

        private static final long serialVersionUID = 1L;

        @Override
        public void funnel(RevObject.TYPE from, PrimitiveSink into) {
            Funnels.integerFunnel().funnel(from.value(), into);
        }
    };

    private static final Funnel<ObjectId> ObjectIdFunnel = new Funnel<ObjectId>() {

        private static final long serialVersionUID = 1L;

        @Override
        public void funnel(ObjectId from, PrimitiveSink into) {
            Funnels.byteArrayFunnel().funnel(from.getRawValue(), into);
        }
    };

    static final class CommitFunnel implements Funnel<RevCommit> {
        private static final long serialVersionUID = -1L;

        private static final CommitFunnel INSTANCE = new CommitFunnel();

        @Override
        public void funnel(RevCommit from, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.COMMIT, into);
            ObjectIdFunnel.funnel(from.getId(), into);
            ObjectIdFunnel.funnel(from.getTreeId(), into);
            for (ObjectId parentId : from.getParentIds()) {
                ObjectIdFunnel.funnel(parentId, into);
            }
            NullableStringFunnel.funnel(from.getMessage(), into);
            PersonFunnel.funnel(from.getAuthor(), into);
            PersonFunnel.funnel(from.getCommitter(), into);
        }
    };

    static final class TreeFunnel implements Funnel<RevTree> {

        private static final long serialVersionUID = 1L;

        private static final TreeFunnel INSTANCE = new TreeFunnel();

        @Override
        public void funnel(RevTree from, PrimitiveSink into) {
            Optional<ImmutableList<Node>> treesOp = from.trees();
            Optional<ImmutableList<Node>> featuresOp = from.features();
            Optional<ImmutableSortedMap<Integer, Bucket>> bucketsOp = from.buckets();
            funnel(into, treesOp, featuresOp, bucketsOp);
        }

        public void funnel(PrimitiveSink into, Optional<ImmutableList<Node>> treesOp,
                Optional<ImmutableList<Node>> featuresOp,
                Optional<ImmutableSortedMap<Integer, Bucket>> bucketsOp) {

            RevObjectTypeFunnel.funnel(TYPE.TREE, into);
            if (treesOp.isPresent()) {
                ImmutableList<Node> trees = treesOp.get();
                Node ref;
                for (int i = 0; i < trees.size(); i++) {
                    ref = trees.get(i);
                    NodeFunnel.funnel(ref, into);
                }
            }
            if (featuresOp.isPresent()) {
                ImmutableList<Node> children = featuresOp.get();
                Node ref;
                for (int i = 0; i < children.size(); i++) {
                    ref = children.get(i);
                    NodeFunnel.funnel(ref, into);
                }
            }
            if (bucketsOp.isPresent()) {
                ImmutableSortedMap<Integer, Bucket> buckets = bucketsOp.get();
                for (Entry<Integer, Bucket> entry : buckets.entrySet()) {
                    Funnels.integerFunnel().funnel(entry.getKey(), into);
                    ObjectIdFunnel.funnel(entry.getValue().getObjectId(), into);
                }
            }
        }
    };

    static final class FeatureFunnel implements Funnel<RevFeature> {

        private static final long serialVersionUID = 1L;

        private static final FeatureFunnel INSTANCE = new FeatureFunnel();

        @Override
        public void funnel(RevFeature from, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.FEATURE, into);
            from.forEach((v) -> PropertyValueFunnel.funnel(v, into));
        }

        public void funnelValues(List<Object> values, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.FEATURE, into);
            values.forEach((v) -> PropertyValueFunnel.funnel(v, into));
        }

        public void funnel(List<Optional<Object>> values, PrimitiveSink into) {
            funnelValues(Lists.transform(values, (v) -> v.orNull()), into);
        }
    };

    static final class FeatureTypeFunnel implements Funnel<RevFeatureType> {
        private static final long serialVersionUID = 1L;

        public static final FeatureTypeFunnel INSTANCE = new FeatureTypeFunnel();

        @Override
        public void funnel(RevFeatureType from, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.FEATURETYPE, into);

            ImmutableSet<PropertyDescriptor> featureTypeProperties = new DescribeFeatureType()
                    .setFeatureType(from).call();

            NameFunnel.funnel(from.getName(), into);

            for (PropertyDescriptor descriptor : featureTypeProperties) {
                PropertyDescriptorFunnel.funnel(descriptor, into);
            }
        }

    };

    static final class TagFunnel implements Funnel<RevTag> {
        private static final long serialVersionUID = 1L;

        public static final TagFunnel INSTANCE = new TagFunnel();

        @Override
        public void funnel(RevTag from, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.TAG, into);
            ObjectIdFunnel.funnel(from.getCommitId(), into);
            StringFunnel.funnel((CharSequence) from.getName(), into);
            StringFunnel.funnel((CharSequence) from.getMessage(), into);
            PersonFunnel.funnel(from.getTagger(), into);
        }
    };

    private static final Funnel<RevPerson> PersonFunnel = NullableFunnel
            .of(new Funnel<RevPerson>() {
                private static final long serialVersionUID = -1L;

                @Override
                public void funnel(RevPerson from, PrimitiveSink into) {
                    NullableStringFunnel.funnel(from.getName().orNull(), into);
                    NullableStringFunnel.funnel(from.getEmail().orNull(), into);
                    Funnels.longFunnel().funnel(from.getTimestamp(), into);
                    Funnels.integerFunnel().funnel(from.getTimeZoneOffset(), into);
                }
            });

    private static final Funnel<Node> NodeFunnel = new Funnel<Node>() {
        private static final long serialVersionUID = 1L;

        @Override
        public void funnel(Node ref, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(ref.getType(), into);
            StringFunnel.funnel((CharSequence) ref.getName(), into);
            ObjectIdFunnel.funnel(ref.getObjectId(), into);
            ObjectIdFunnel.funnel(ref.getMetadataId().or(ObjectId.NULL), into);
        }
    };

    private static final Funnel<Object> PropertyValueFunnel = new Funnel<Object>() {
        private static final long serialVersionUID = 1L;

        @Override
        public void funnel(final @Nullable Object value, PrimitiveSink into) {
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

        @Override
        public void funnel(final Geometry geom, final PrimitiveSink into) {

            CoordinateFilter filter = new CoordinateFilter() {

                final double scale = 1E9D;

                @Override
                public void filter(Coordinate coord) {
                    double x = Math.round(coord.x * scale) / scale;
                    double y = Math.round(coord.y * scale) / scale;
                    into.putDouble(x);
                    into.putDouble(y);
                }
            };
            geom.apply(filter);
        }
    };

    private static final Funnel<Name> NameFunnel = new Funnel<Name>() {
        private static final long serialVersionUID = 1L;

        @Override
        public void funnel(Name from, PrimitiveSink into) {
            NullableStringFunnel.funnel(from.getNamespaceURI(), into);
            StringFunnel.funnel((CharSequence) from.getLocalPart(), into);
        }
    };

    private static final Funnel<PropertyDescriptor> PropertyDescriptorFunnel = new Funnel<PropertyDescriptor>() {
        private static final long serialVersionUID = 1L;

        @Override
        public void funnel(PropertyDescriptor descriptor, PrimitiveSink into) {
            NameFunnel.funnel(descriptor.getName(), into);

            PropertyType attrType = descriptor.getType();
            NameFunnel.funnel(attrType.getName(), into);

            FieldType type = FieldType.forBinding(attrType.getBinding());
            into.putInt(type.getTag());
            into.putBoolean(descriptor.isNillable());

            into.putInt(descriptor.getMaxOccurs());
            into.putInt(descriptor.getMinOccurs());

            if (descriptor instanceof GeometryDescriptor) {
                CoordinateReferenceSystem crs;
                crs = ((GeometryDescriptor) descriptor).getCoordinateReferenceSystem();
                String srsName;
                if (crs == null) {
                    srsName = "urn:ogc:def:crs:EPSG::0";
                } else {
                    srsName = CRS.toSRS(crs);
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

        @Override
        public void funnel(Map<String, ?> map, final PrimitiveSink into) {
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

}
