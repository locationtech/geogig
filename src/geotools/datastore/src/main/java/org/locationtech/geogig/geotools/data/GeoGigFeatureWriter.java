/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import java.io.IOException;
import java.util.NoSuchElementException;

import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.WorkingTree;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Preconditions;

/**
 *
 */
class GeoGigFeatureWriter implements FeatureWriter<SimpleFeatureType, SimpleFeature> {

    private final FeatureReader<SimpleFeatureType, SimpleFeature> reader;

    private final WorkingTree workingTree;

    private final String typePath;

    private final ObjectId featureTypeId;

    private SimpleFeature last;

    private GeoGigFeatureWriter(final FeatureReader<SimpleFeatureType, SimpleFeature> reader,
            final NodeRef typeRef, final WorkingTree workingTree) {
        this.reader = reader;
        this.typePath = typeRef.path();
        this.featureTypeId = typeRef.getMetadataId();
        this.workingTree = workingTree;
    }

    public static GeoGigFeatureWriter create(
            final FeatureReader<SimpleFeatureType, SimpleFeature> reader, final NodeRef typeRef,
            final WorkingTree workingTree) {
        return new GeoGigFeatureWriter(reader, typeRef, workingTree);
    }

    public static GeoGigFeatureWriter createAppendable(
            FeatureReader<SimpleFeatureType, SimpleFeature> reader, NodeRef typeRef,
            WorkingTree workingTree) {
        return new GeoGigFeatureWriter(new InfiniteFeatureReader(reader), typeRef, workingTree);
    }

    @Override
    public SimpleFeatureType getFeatureType() {
        return reader.getFeatureType();
    }

    @Override
    public boolean hasNext() throws IOException {
        return reader.hasNext();
    }

    @Override
    public SimpleFeature next() throws IOException {
        this.last = reader.next();
        return last;
    }

    @Override
    public void remove() throws IOException {
        Preconditions.checkState(last != null, "next() hasn't been called");
        String path = typePath;
        String featureId = last.getID();
        workingTree.delete(path, featureId);
    }

    @Override
    public void write() throws IOException {
        Preconditions.checkState(last != null, "next() hasn't been called");
        String parentTreePath = typePath;
        RevFeature feature = RevFeature.builder().build(last);
        String path = NodeRef.appendChild(parentTreePath, last.getID());
        FeatureInfo fi = FeatureInfo.insert(feature, featureTypeId, path);
        workingTree.insert(fi);
    }

    @Override
    public void close() throws IOException {
        //
    }

    private static final class InfiniteFeatureReader
            implements FeatureReader<SimpleFeatureType, SimpleFeature> {

        private FeatureReader<SimpleFeatureType, SimpleFeature> reader;

        private SimpleFeatureBuilder newFeaturesBuilder;

        public InfiniteFeatureReader(FeatureReader<SimpleFeatureType, SimpleFeature> reader) {
            this.reader = reader;
            this.newFeaturesBuilder = new SimpleFeatureBuilder(reader.getFeatureType());
        }

        @Override
        public boolean hasNext() throws IOException {
            return reader.hasNext();
        }

        @Override
        public SimpleFeatureType getFeatureType() {
            return reader.getFeatureType();
        }

        @Override
        public SimpleFeature next()
                throws IOException, IllegalArgumentException, NoSuchElementException {
            if (reader.hasNext()) {
                return reader.next();
            }
            SimpleFeature feature = this.newFeaturesBuilder.buildFeature(null);
            return feature;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
