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
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.collect.Iterators;
import org.locationtech.geogig.repository.FeatureInfo;

public class BulkFeatureRetriever {
    ObjectDatabase odb;

    int nodeFetchSize = 10_000;

    int featureFetchSize = nodeFetchSize / 10;

    int featureSize = featureFetchSize / 5;

    public BulkFeatureRetriever(ObjectDatabase odb) {
        this.odb = odb;
    }

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
        Iterator<FeatureInfo> allFeatures = Iterators.concat(transformed); // simplify from
                                                                           // Iterator<Iterator<SF>>
                                                                           // to Iterator<SF>

        return new BackgroundingIterator<FeatureInfo>(allFeatures, featureSize);
    }

    public Iterator<SimpleFeature> getGeoToolsFeatures(Iterator<NodeRef> refs) {
        Iterator<FeatureInfo> fis = getGeoGIGFeatures(refs);
        MultiFeatureTypeBuilder builder = new MultiFeatureTypeBuilder(odb);
        Iterator<SimpleFeature> result = Iterators.transform(fis, builder);
        return new BackgroundingIterator<SimpleFeature>(result, featureSize);
    }
}
