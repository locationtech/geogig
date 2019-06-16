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

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureType.FeatureTypeBuilder;
import org.locationtech.geogig.feature.FeatureTypes;
import org.locationtech.geogig.feature.Name;
import org.locationtech.geogig.feature.PropertyDescriptor;
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

import lombok.NonNull;

/**
 * This is the main entry class for retrieving features from GeoGIG.
 *
 * It comes in 3 flavors; a) getGeoGIGFeatures - (low level) this returns FeatureInfos for the
 * requested NodeRefs b) getGeoToolsFeatures - (high level) this returns Features for the requested
 * NodeRefs. The FeatureType Metadata is retrieved from the ObjectDB to construct the Features.
 *
 * c) getGeoToolsFeatures w/Schema - (high level) this returns Features for the requested NodeRefs.
 * It ignores the FeatureType Metadata and uses the supplied schema to construct features.
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

    public BulkFeatureRetriever(@NonNull ObjectStore leftDb, @NonNull ObjectStore rightDb) {
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

            public @Override void close() {
                objects.close();
                closeableRefs.close();
            }

            public @Override boolean hasNext() {
                return objects.hasNext();
            }

            public @Override ObjectInfo<RevFeature> next() {
                return objects.next();
            }
        };
    }

    /**
     * Given a bunch of NodeRefs, create Features from the results. The result might be mixed
     * FeatureTypes
     *
     * This retrieves FeatureType info from the ObjectDatabase as needed.
     *
     * @see BulkFeatureRetriever#getGeoGIGFeatures
     *
     * @param refs
     * @return
     */
    public AutoCloseableIterator<Feature> getGeoToolsFeatures(Iterator<NodeRef> refs) {
        AutoCloseableIterator<ObjectInfo<RevFeature>> fis = getGeoGIGFeatures(refs);
        MultiFeatureTypeBuilder builder = new MultiFeatureTypeBuilder(odb);
        return AutoCloseableIterator.transform(fis, builder);
    }

    /**
     * Given a bunch of NodeRefs, create Features from the results. This builds a particular
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
    public AutoCloseableIterator<Feature> getGeoToolsFeatures(AutoCloseableIterator<NodeRef> refs,
            RevFeatureType nativeType, @Nullable Name typeNameOverride,
            GeometryFactory geometryFactory) {

        // function that converts the FeatureInfo a feature of the given schema
        Function<ObjectInfo<RevFeature>, Feature> funcBuildFeature = info -> Feature
                .build(info.node().getName(), nativeType, info.object());
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

    public AutoCloseableIterator<Feature> getGeoToolsDiffFeatures(//@formatter:off
            AutoCloseableIterator<DiffEntry> refs, 
            RevFeatureType nativeType, Name typeName,
            boolean flattenSchema, 
            @Nullable GeometryFactory geometryFactory) {//@formatter:on

        FeatureType diffType;
        if (flattenSchema) {
            diffType = buildFlattenedDiffFeatureType(typeName, (FeatureType) nativeType.type());
        } else {
            diffType = buildDiffFeatureType(typeName, (FeatureType) nativeType.type());
        }
        return getGeoToolsDiffFeatures(refs, nativeType, diffType, geometryFactory);
    }

    public AutoCloseableIterator<Feature> getGeoToolsDiffFeatures(//@formatter:off
            AutoCloseableIterator<DiffEntry> refs, 
            RevFeatureType nativeType,
            FeatureType diffType, 
            @Nullable GeometryFactory geometryFactory) {//@formatter:on

        boolean flattenedType = !isDiffFeatureType(diffType);
        Function<DiffObjectInfo<RevFeature>, Feature> builder;
        if (flattenedType) {
            builder = new DiffFeatureFlattenedBuilder(diffType, nativeType.type());
        } else {
            builder = new DiffFeatureBuilder(diffType, nativeType.type(), geometryFactory);
        }
        AutoCloseableIterator<DiffObjectInfo<RevFeature>> fis = getDiffFeatures(refs);

        return AutoCloseableIterator.transform(fis, builder);
    }

    private boolean isDiffFeatureType(FeatureType type) {
        return isFeatureDescriptor(type.getDescriptor("old"))
                && isFeatureDescriptor(type.getDescriptor("new"));
    }

    private boolean isFeatureDescriptor(PropertyDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        Class<?> binding = descriptor.getBinding();
        return Feature.class.equals(binding);
    }

    public static FeatureType buildFlattenedDiffFeatureType(Name typeName,
            FeatureType nativeFeatureType) {

        FeatureTypeBuilder builder = FeatureType.builder().name(typeName);
        builder.add(DIFF_FEATURE_CHANGETYPE_ATTNAME, Integer.class);
        List<PropertyDescriptor> atts = nativeFeatureType.getDescriptors();
        for (PropertyDescriptor att : atts) {
            String name = att.getLocalName();
            Class<?> binding = att.getBinding();
            if (att.isGeometryDescriptor()) {
                CoordinateReferenceSystem crs = att.coordinateReferenceSystem();
                builder.add(FLATTENED_ATTNAME_PREFIX_OLD + name, binding, crs);
                builder.add(FLATTENED_ATTNAME_PREFIX_NEW + name, binding, crs);
            } else {
                builder.add(FLATTENED_ATTNAME_PREFIX_OLD + name, binding);
                builder.add(FLATTENED_ATTNAME_PREFIX_NEW + name, binding);
            }
        }

        FeatureType diffFeatureType = builder.build();
        return diffFeatureType;

    }

    public static FeatureType buildDiffFeatureType(Name typeName, FeatureType nativeFeatureType) {

        FeatureTypeBuilder builder = FeatureTypes.builder(nativeFeatureType);
        builder.add(DIFF_FEATURE_CHANGETYPE_ATTNAME, Integer.class);

        PropertyDescriptor oldValDescriptor;
        Name oldName = new Name("old");
        oldValDescriptor = PropertyDescriptor.builder().name(oldName).typeName(oldName)
                .binding(Feature.class).minOccurs(1).maxOccurs(1).nillable(true).build();

        PropertyDescriptor newValDescriptor;
        Name newName = new Name("new");
        newValDescriptor = PropertyDescriptor.builder().name(newName).typeName(newName)
                .binding(Feature.class).minOccurs(1).maxOccurs(1).nillable(true).build();

        builder.add(oldValDescriptor);
        builder.add(newValDescriptor);
        builder.name(typeName);
        FeatureType diffFeatureType = builder.build();
        return diffFeatureType;
    }

}
