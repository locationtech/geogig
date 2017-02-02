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

import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.repository.AutoCloseableIterator;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
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

    int nodeFetchSize = 10_000;

    int featureFetchSize = 201;

    int featureSize = featureFetchSize / 5;

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
    public AutoCloseableIterator<FeatureInfo> getGeoGIGFeatures(Iterator<NodeRef> refs, boolean wrapResultInBackgroundingIterator) {
        // this will get the refs (from the tree) in the background
        BackgroundingIterator<NodeRef> featureRefs = new BackgroundingIterator<NodeRef>(refs,
                nodeFetchSize);
        // this partitions the featureids into groups for bulk retrieving from the DB
        AutoCloseableIterator<List<NodeRef>> partition = AutoCloseableIterator.partition(featureRefs, featureFetchSize);

        // used to get a group of features from the DB
        BulkObjectDatabaseFeatureRetriever bulkFeatureRetriever = new BulkObjectDatabaseFeatureRetriever(
                odb);

        AutoCloseableIterator<Iterator<FeatureInfo>> transformed = AutoCloseableIterator.transform(partition,
                bulkFeatureRetriever);
        // simplify from Iterator<Iterator<SF>> to Iterator<SF>
        AutoCloseableIterator<FeatureInfo> allFeatures = AutoCloseableIterator.concat(transformed);

        if (wrapResultInBackgroundingIterator)
            return new BackgroundingIterator<>(allFeatures, featureSize);
        else
            return allFeatures;
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
    public Iterator<SimpleFeature> getGeoToolsFeatures(Iterator<NodeRef> refs) {
        AutoCloseableIterator<FeatureInfo> fis = getGeoGIGFeatures(refs,false);
        MultiFeatureTypeBuilder builder = new MultiFeatureTypeBuilder(odb);
        AutoCloseableIterator<SimpleFeature> result = AutoCloseableIterator.transform(fis, builder);
        return new BackgroundingIterator<>(result, featureSize);
    }

    /**
     * Given a bunch of NodeRefs, create SimpleFeatures from the results. This builds a particular
     * FeatureType from the ObjectDatabase.
     *
     * This DOES NOT retrieves FeatureType info from the ObjectDatabase.
     *
     * @param refs
     * @param schema
     * @return
     */
    public AutoCloseableIterator<SimpleFeature> getGeoToolsFeatures(
            AutoCloseableIterator<NodeRef> refs, SimpleFeatureType schema,
            GeometryFactory geometryFactory) {
        // builder for this particular schema
        FeatureBuilder featureBuilder = new FeatureBuilder(schema);

        // function that converts the FeatureInfo a feature of the given schema
        Function<FeatureInfo, SimpleFeature> funcBuildFeature = (input -> MultiFeatureTypeBuilder
                .build(featureBuilder, input, geometryFactory));

        AutoCloseableIterator<FeatureInfo> fis = getGeoGIGFeatures(refs,false);
        AutoCloseableIterator<SimpleFeature> result = AutoCloseableIterator.transform(fis, funcBuildFeature);
        final BackgroundingIterator<SimpleFeature> backgroundingIterator = new BackgroundingIterator<>(
                result, featureSize);

        return new AutoCloseableIterator<SimpleFeature>() {

            @Override
            public void close() {
                try {
                    backgroundingIterator.close();
                } finally {
                    refs.close();
                }
            }

            @Override
            public boolean hasNext() {
                return backgroundingIterator.hasNext();
            }

            @Override
            public SimpleFeature next() {
                return backgroundingIterator.next();
            }
        };
    }
}
