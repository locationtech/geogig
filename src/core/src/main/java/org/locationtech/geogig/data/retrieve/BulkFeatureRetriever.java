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

import com.google.common.base.Function;
import org.locationtech.geogig.data.FeatureBuilder;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.collect.Iterators;
import org.locationtech.geogig.repository.FeatureInfo;

public class BulkFeatureRetriever {
    ObjectDatabase odb;

    int nodeFetchSize = 10_000;

    int featureFetchSize = 201;

    int featureSize = featureFetchSize / 5;

    public BulkFeatureRetriever(ObjectDatabase odb) {
        this.odb = odb;
    }

    /**
     * Given a bunch of NodeRefs, create FeatureInfos for them.
     * FeatureInfo contains the actual GIG feature, and its metadata
     * (i.e. FeatureTypeId + path (including name))
     * @param refs
     * @return
     */
    public Iterator<FeatureInfo> getGeoGIGFeatures(Iterator<NodeRef> refs) {
        // this will get the refs (from the tree) in the background
        BackgroundingIterator<NodeRef> featureRefs = new BackgroundingIterator<NodeRef>(refs,
                nodeFetchSize);
        // this partitions the featureids into groups for bulk retrieving from the DB
        Iterator<List<NodeRef>> partition = Iterators.partition(featureRefs, featureFetchSize);

        // used to get a group of features from the DB
        BulkGeoGigFeatureRetriever bulkFeatureRetriever = new BulkGeoGigFeatureRetriever(odb);

        Iterator<Iterator<FeatureInfo>> transformed = Iterators.transform(partition,
                bulkFeatureRetriever);
        // simplify from Iterator<Iterator<SF>> to Iterator<SF>
        Iterator<FeatureInfo> allFeatures = Iterators.concat(transformed);

        return new BackgroundingIterator<>(allFeatures, featureSize);
    }

    /**
     * Given a bunch of NodeRefs, create SimpleFeatures from the results.
     * The result might be mixed FeatureTypes
     *
     * This retrieves FeatureType info from the ObjectDatabase as needed.
     *
     *  @see BulkFeatureRetriever#getGeoGIGFeatures
     *
     * @param refs
     * @return
     */
    public Iterator<SimpleFeature> getGeoToolsFeatures(Iterator<NodeRef> refs) {
        Iterator<FeatureInfo> fis = getGeoGIGFeatures(refs);
        MultiFeatureTypeBuilder builder = new MultiFeatureTypeBuilder(odb);
        Iterator<SimpleFeature> result = Iterators.transform(fis, builder);
        return new BackgroundingIterator<>(result, featureSize);
    }

    /**
     * Given a bunch of NodeRefs, create SimpleFeatures from the results.
     * This builds a particular FeatureType from the ObjectDatabase.
     *
     * This DOES NOT retrieves FeatureType info from the ObjectDatabase.
     *
     * @param refs
     * @param schema
     * @return
     */
    public Iterator<SimpleFeature> getGeoToolsFeatures(Iterator<NodeRef> refs, SimpleFeatureType schema) {
        //builder for this particular schema
        FeatureBuilder featureBuilder = new FeatureBuilder(schema);

        //function that converts the FeatureInfo a feature of the given schema
        Function<FeatureInfo, SimpleFeature> funcBuildFeature = (input -> MultiFeatureTypeBuilder.build(featureBuilder, input));

        Iterator<FeatureInfo> fis = getGeoGIGFeatures(refs);
        Iterator<SimpleFeature> result = Iterators.transform(fis, funcBuildFeature);
        return new BackgroundingIterator<>(result, featureSize);
    }
}
