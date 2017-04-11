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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;

import com.google.common.base.Function;
import com.vividsolutions.jts.geom.GeometryFactory;

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
    ObjectStore odb;

    public BulkFeatureRetriever(ObjectStore odb) {
        this.odb = odb;
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
        objects = odb.getObjects(refs, BulkOpListener.NOOP_LISTENER, RevFeature.class);

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
        AutoCloseableIterator<SimpleFeature> result = AutoCloseableIterator.transform(fis, builder);
        return result;
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

        AutoCloseableIterator<SimpleFeature> result = AutoCloseableIterator.transform(fis,
                funcBuildFeature);

        return result;
    }
}
