/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import java.sql.SQLException;
import java.util.Map;

import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.data.ForwardingFeatureIterator;
import org.locationtech.geogig.geotools.plumbing.ForwardingFeatureIteratorProvider;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 * Provides a forwarding feature iterator to update all incoming features to use the feature ids
 * specified in the fid mapping table, if a mapping table exists in the geopackage.
 */
public class GeoPkgForwardingFeatureIteratorProvider extends ForwardingFeatureIteratorProvider {

    private final GeopkgGeogigMetadata metadata;

    public GeoPkgForwardingFeatureIteratorProvider(final GeopkgGeogigMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * @param iterator the feature iterator to forward
     * @param featureType the feature type of the features in the iterator
     * @return a new feature iterator which transforms the feature ids of imported features
     */
    @SuppressWarnings("rawtypes")
    @Override
    public FeatureIterator forwardIterator(FeatureIterator iterator,
            SimpleFeatureType featureType) {
        try {
            Map<String, String> fidMappings = metadata.getFidMappings(featureType.getTypeName());
            return new GeoPkgFidReplacer(iterator, fidMappings, featureType);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Transforms all incoming features to use the feature ids specified in the fid mapping table,
     * if a mapping table exists in the geopackage.
     */
    private static class GeoPkgFidReplacer extends ForwardingFeatureIterator<SimpleFeature> {

        private SimpleFeatureType featureType;

        private final Map<String, String> fidMappings;

        @SuppressWarnings("unchecked")
        public GeoPkgFidReplacer(@SuppressWarnings("rawtypes") FeatureIterator iterator,
                final Map<String, String> fidMappings, SimpleFeatureType featureType) {
            super(iterator);
            this.fidMappings = fidMappings;
            this.featureType = featureType;

        }

        @Override
        public SimpleFeature next() {
            SimpleFeature next = super.next();
            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
            builder.init(next);
            String oldFeatureId = next.getIdentifier().getID();
            String featureId;
            if (fidMappings.containsKey(oldFeatureId)) {
                featureId = fidMappings.get(oldFeatureId);
            } else {
                featureId = SimpleFeatureBuilder.createDefaultFeatureId();
            }
            return builder.buildFeature(featureId);
        }
    }
}