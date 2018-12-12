/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.data.retrieve;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.internal.ObjectStoreDiffObjectIterator;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.FeatureTypeFactory;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Function;

/**
 * This is the main entry class for retrieving features from GeoGIG.
 *
 * It comes in 3 flavors; a) getGeoGIGFeatures - (low level) this returns FeatureInfos for the
 * requested NodeRefs b) getGeoToolsFeatures - (high level) this returns SimpleFeatures for the
 * requested NodeRefs. The FeatureType Metadata is retrieved from the ObjectDB to construct the
 * Features.
 *
 * c) getGeoToolsFeatures w/Schema - (high level) this returns SimpleFeatures for the requested
 * NodeRefs. It ignores the FeatureType Metadata and uses the supplied schema to construct features.
 */
public class BulkFeatureRetriever {
    static final String FLATTENED_ATTNAME_PREFIX_NEW = "new_";

    static final String FLATTENED_ATTNAME_PREFIX_OLD = "old_";

    public static final String DIFF_FEATURE_CHANGETYPE_ATTNAME = "geogig.changeType";

    private ObjectStore odb;

    private ObjectStore leftDb;

    public BulkFeatureRetriever(ObjectStore db) {
        this(db, db);
    }

    public BulkFeatureRetriever(ObjectStore leftDb, ObjectStore rightDb) {
        checkNotNull(leftDb);
        checkNotNull(rightDb);
        this.leftDb = leftDb;
        this.odb = rightDb;
    }

    /**
     * Given a bunch of NodeRefs, create FeatureInfos for them. FeatureInfo contains the actual GIG
     * feature, and its metadata (i.e. FeatureTypeId + path (including name))
     * 
     * @param refs
     * @return
     */
    public AutoCloseableIterator<ObjectInfo<RevFeature>> getGeoGIGFeatures(Iterator<NodeRef> refs) {
        AutoCloseableIterator<ObjectInfo<RevFeature>> objects;

        AutoCloseableIterator<NodeRef> closeableRefs = AutoCloseableIterator.fromIterator(refs);
        objects = odb.getObjects(closeableRefs, BulkOpListener.NOOP_LISTENER, RevFeature.class);

        return new AutoCloseableIterator<ObjectInfo<RevFeature>>() {

            @Override
            public void close() {
                objects.close();
                closeableRefs.close();
            }

            @Override
            public boolean hasNext() {
                return objects.hasNext();
            }

            @Override
            public ObjectInfo<RevFeature> next() {
                return objects.next();
            }
        };
    }

    /**
     * Given a bunch of NodeRefs, create SimpleFeatures from the results. The result might be mixed
     * FeatureTypes
     *
     * This retrieves FeatureType info from the ObjectDatabase as needed.
     *
     * @see BulkFeatureRetriever#getGeoGIGFeatures
     *
     * @param refs
     * @return
     */
    public AutoCloseableIterator<SimpleFeature> getGeoToolsFeatures(Iterator<NodeRef> refs) {
        AutoCloseableIterator<ObjectInfo<RevFeature>> fis = getGeoGIGFeatures(refs);
        MultiFeatureTypeBuilder builder = new MultiFeatureTypeBuilder(odb);
        return AutoCloseableIterator.transform(fis, builder);
    }

    /**
     * Given a bunch of NodeRefs, create SimpleFeatures from the results. This builds a particular
     * FeatureType from the ObjectDatabase.
     *
     * This DOES NOT retrieves FeatureType info from the ObjectDatabase.
     *
     * @param refs list of node refs to fetch {@link RevFeature}s for
     * @param nativeType the feature type the features adhere to
     * @param typeNameOverride in case the resulting feature type needs to be renamed (e.g. to
     *        change the namespace URI, and/or the local name)
     * @param geometryFactory the geometry factory to create geometry attributes with
     * @return
     */
    public AutoCloseableIterator<SimpleFeature> getGeoToolsFeatures(
            AutoCloseableIterator<NodeRef> refs, RevFeatureType nativeType,
            @Nullable Name typeNameOverride, GeometryFactory geometryFactory) {

        // builder for this particular schema
        FeatureBuilder featureBuilder = new FeatureBuilder(nativeType, typeNameOverride);

        // function that converts the FeatureInfo a feature of the given schema
        Function<ObjectInfo<RevFeature>, SimpleFeature> funcBuildFeature = (input -> MultiFeatureTypeBuilder
                .build(featureBuilder, input, geometryFactory));

        AutoCloseableIterator<ObjectInfo<RevFeature>> fis = getGeoGIGFeatures(refs);

        return AutoCloseableIterator.transform(fis, funcBuildFeature);
    }

    public AutoCloseableIterator<DiffObjectInfo<RevFeature>> getDiffFeatures(
            Iterator<DiffEntry> refs) {
        AutoCloseableIterator<DiffObjectInfo<RevFeature>> objects;

        final AutoCloseableIterator<DiffEntry> closeableRefs = AutoCloseableIterator
                .fromIterator(refs);
        objects = getDiffObjects(refs, RevFeature.class);

        return new AutoCloseableIterator<DiffObjectInfo<RevFeature>>() {
            public @Override void close() {
                objects.close();
                closeableRefs.close();
            }

            public @Override boolean hasNext() {
                return objects.hasNext();
            }

            public @Override DiffObjectInfo<RevFeature> next() {
                return objects.next();
            }
        };
    }

    private AutoCloseableIterator<DiffObjectInfo<RevFeature>> getDiffObjects(
            Iterator<DiffEntry> refs, Class<RevFeature> type) {
        if (leftDb == null || leftDb == odb) {
            return odb.getDiffObjects(refs, type);
        }
        return new ObjectStoreDiffObjectIterator<>(refs, type, leftDb, odb);
    }

    public AutoCloseableIterator<SimpleFeature> getGeoToolsDiffFeatures(//@formatter:off
            AutoCloseableIterator<DiffEntry> refs, 
            RevFeatureType nativeType, Name typeName,
            boolean flattenSchema, 
            @Nullable GeometryFactory geometryFactory) {//@formatter:on

        SimpleFeatureType diffType;
        if (flattenSchema) {
            diffType = buildFlattenedDiffFeatureType(typeName,
                    (SimpleFeatureType) nativeType.type());
        } else {
            diffType = buildDiffFeatureType(typeName, (SimpleFeatureType) nativeType.type());
        }
        return getGeoToolsDiffFeatures(refs, nativeType, diffType, geometryFactory);
    }

    public AutoCloseableIterator<SimpleFeature> getGeoToolsDiffFeatures(//@formatter:off
            AutoCloseableIterator<DiffEntry> refs, 
            RevFeatureType nativeType,
            SimpleFeatureType diffType, 
            @Nullable GeometryFactory geometryFactory) {//@formatter:on

        boolean flattenedType = !isDiffFeatureType(diffType);
        Function<DiffObjectInfo<RevFeature>, SimpleFeature> builder;
        if (flattenedType) {
            builder = new DiffFeatureFlattenedBuilder(diffType, nativeType);
        } else {

            // builder for the "old" and "new" versions of each feature
            FeatureBuilder featureBuilder = new FeatureBuilder(nativeType, null);

            builder = new DiffFeatureBuilder(diffType, featureBuilder, geometryFactory);
        }
        AutoCloseableIterator<DiffObjectInfo<RevFeature>> fis = getDiffFeatures(refs);

        return AutoCloseableIterator.transform(fis, builder);
    }

    private boolean isDiffFeatureType(SimpleFeatureType type) {
        return isFeatureDescriptor(type.getDescriptor("old"))
                && isFeatureDescriptor(type.getDescriptor("new"));
    }

    private boolean isFeatureDescriptor(AttributeDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        AttributeType type = descriptor.getType();
        boolean isFeature = type instanceof FeatureType;
        return isFeature;
    }

    public static SimpleFeatureType buildFlattenedDiffFeatureType(Name typeName,
            SimpleFeatureType nativeFeatureType) {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(typeName);
        builder.add(DIFF_FEATURE_CHANGETYPE_ATTNAME, Integer.class);
        List<AttributeDescriptor> atts = nativeFeatureType.getAttributeDescriptors();
        for (AttributeDescriptor att : atts) {
            String name = att.getLocalName();
            Class<?> binding = att.getType().getBinding();
            if (att instanceof GeometryDescriptor) {
                CoordinateReferenceSystem crs = ((GeometryDescriptor) att)
                        .getCoordinateReferenceSystem();
                builder.add(FLATTENED_ATTNAME_PREFIX_OLD + name, binding, crs);
                builder.add(FLATTENED_ATTNAME_PREFIX_NEW + name, binding, crs);
            } else {
                builder.add(FLATTENED_ATTNAME_PREFIX_OLD + name, binding);
                builder.add(FLATTENED_ATTNAME_PREFIX_NEW + name, binding);
            }
        }

        SimpleFeatureType diffFeatureType = builder.buildFeatureType();
        return diffFeatureType;

    }

    public static SimpleFeatureType buildDiffFeatureType(Name typeName,
            SimpleFeatureType nativeFeatureType) {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        FeatureTypeFactory typeFactory = builder.getFeatureTypeFactory();
        builder.add(DIFF_FEATURE_CHANGETYPE_ATTNAME, Integer.class);

        AttributeDescriptor oldValDescriptor;
        AttributeDescriptor newValDescriptor;
        oldValDescriptor = typeFactory.createAttributeDescriptor(nativeFeatureType,
                new NameImpl("old"), 1, 1, true, null);
        newValDescriptor = typeFactory.createAttributeDescriptor(nativeFeatureType,
                new NameImpl("new"), 1, 1, true, null);

        builder.add(oldValDescriptor);
        builder.add(newValDescriptor);
        builder.setName(typeName);
        SimpleFeatureType diffFeatureType = builder.buildFeatureType();
        return diffFeatureType;
    }

}
