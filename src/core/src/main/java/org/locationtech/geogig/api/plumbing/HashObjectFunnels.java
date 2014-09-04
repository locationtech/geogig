/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map.Entry;
import java.util.UUID;

import org.geotools.referencing.CRS;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevPerson;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.storage.FieldType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
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
    private static final byte[] NULL_BYTE_CODE = { 0x60, (byte) 0xe5, 0x6d, 0x08, (byte) 0xd3,
            0x08, 0x53, (byte) 0xb7, (byte) 0x84, 0x07, 0x77 };

    public static Funnel<RevCommit> commitFunnel() {
        return CommitFunnel.INSTANCE;
    }

    public static Funnel<RevTree> treeFunnel() {
        return TreeFunnel.INSTANCE;
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

    private static final class CommitFunnel implements Funnel<RevCommit> {
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

    private static final class TreeFunnel implements Funnel<RevTree> {

        private static final long serialVersionUID = 1L;

        private static final TreeFunnel INSTANCE = new TreeFunnel();

        @Override
        public void funnel(RevTree from, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.TREE, into);
            if (from.trees().isPresent()) {
                ImmutableList<Node> trees = from.trees().get();
                Node ref;
                for (int i = 0; i < trees.size(); i++) {
                    ref = trees.get(i);
                    NodeFunnel.funnel(ref, into);
                }
            }
            if (from.features().isPresent()) {
                ImmutableList<Node> children = from.features().get();
                Node ref;
                for (int i = 0; i < children.size(); i++) {
                    ref = children.get(i);
                    NodeFunnel.funnel(ref, into);
                }
            }
            if (from.buckets().isPresent()) {
                ImmutableSortedMap<Integer, Bucket> buckets = from.buckets().get();
                for (Entry<Integer, Bucket> entry : buckets.entrySet()) {
                    Funnels.integerFunnel().funnel(entry.getKey(), into);
                    ObjectIdFunnel.funnel(entry.getValue().id(), into);
                }
            }
        }
    };

    private static final class FeatureFunnel implements Funnel<RevFeature> {

        private static final long serialVersionUID = 1L;

        private static final FeatureFunnel INSTANCE = new FeatureFunnel();

        @Override
        public void funnel(RevFeature from, PrimitiveSink into) {
            RevObjectTypeFunnel.funnel(TYPE.FEATURE, into);

            for (Optional<Object> value : from.getValues()) {
                PropertyValueFunnel.funnel(value.orNull(), into);
            }
        }
    };

    private static final class FeatureTypeFunnel implements Funnel<RevFeatureType> {
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

    private static final class TagFunnel implements Funnel<RevTag> {
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
        public void funnel(final Object value, PrimitiveSink into) {
            if (value == null) {
                NullFunnel.funnel(value, into);
            } else if (value instanceof String) {
                StringFunnel.funnel((CharSequence) value, into);
            } else if (value instanceof Boolean) {
                into.putBoolean(((Boolean) value).booleanValue());
            } else if (value instanceof Byte) {
                into.putByte(((Byte) value).byteValue());
            } else if (value instanceof Double) {
                into.putDouble(((Double) value).doubleValue());
            } else if (value instanceof BigDecimal) {
                String bdString = ((BigDecimal) value).toEngineeringString();
                StringFunnel.funnel(bdString, into);
            } else if (value instanceof Float) {
                into.putFloat(((Float) value).floatValue());
            } else if (value instanceof Integer) {
                into.putInt(((Integer) value).intValue());
            } else if (value instanceof BigInteger) {
                byte[] bigBytes = ((BigInteger) value).toByteArray();
                into.putBytes(bigBytes);
            } else if (value instanceof Long) {
                into.putLong(((Long) value).longValue());
            } else if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                into.putInt(length);
                for (int i = 0; i < length; i++) {
                    Object arrayElem = Array.get(value, i);
                    PropertyValueFunnel.funnel(arrayElem, into);
                }
            } else if (value instanceof java.util.UUID) {
                UUID uuid = (UUID) value;
                long most = uuid.getMostSignificantBits();
                long least = uuid.getLeastSignificantBits();
                into.putLong(most);
                into.putLong(least);
            } else if (value instanceof Geometry) {
                GeometryFunnel.funnel((Geometry) value, into);
            } else if (value instanceof Serializable) {
                OutputStream byteOutput = Funnels.asOutputStream(into);
                try {
                    ObjectOutput objectOut = new ObjectOutputStream(byteOutput);
                    objectOut.writeObject(value);
                    objectOut.close();
                    byteOutput.close();
                } catch (IOException shouldntHappen) {
                    throw Throwables.propagate(shouldntHappen);
                }
            } else {
                StringFunnel.funnel((CharSequence) value.getClass().getName(), into);
                StringFunnel.funnel((CharSequence) value.toString(), into);
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
            into.putInt(type.getTextTag());
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

}
