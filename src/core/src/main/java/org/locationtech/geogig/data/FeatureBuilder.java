/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.data;

import java.util.Map;

import org.geotools.filter.identity.FeatureIdVersionedImpl;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.impl.RevFeatureTypeBuilder;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.identity.FeatureId;

import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * Provides a method of building features from {@link RevFeature} objects that have the type
 * specified by the given {@link RevFeatureType}.
 * 
 * @see RevFeatureType
 * @see RevFeature
 * @see Feature
 */
public class FeatureBuilder {

    private Map<String, Integer> attNameToRevTypeIndex;

    private RevFeatureType type;

    /**
     * Constructs a new {@code FeatureBuilder} with the given {@link RevFeatureType feature type}.
     * 
     * @param type the feature type of the features that will be built
     */
    public FeatureBuilder(RevFeatureType type) {
        this.type = type;
        this.attNameToRevTypeIndex = GeogigSimpleFeature.buildAttNameToRevTypeIndex(type);
    }

    public RevFeatureType getType() {
        return type;
    }

    /**
     * Constructs a new {@code FeatureBuilder} with the given {@link SimpleFeatureType feature type}
     * .
     * 
     * @param type the feature type of the features that will be built
     */
    public FeatureBuilder(SimpleFeatureType type) {
        this(RevFeatureTypeBuilder.build(type));
    }

    /**
     * Builds a {@link Feature} from the provided {@link RevFeature}.
     * 
     * @param id the id of the new feature
     * @param revFeature the {@code RevFeature} with the property values for the feature
     * @return the constructed {@code Feature}
     */
    public Feature build(final String id, final RevFeature revFeature) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(revFeature);

        final FeatureId fid = new LazyVersionedFeatureId(id, revFeature.getId());

        SimpleFeatureType featureType = (SimpleFeatureType) type.type();

        GeogigSimpleFeature feature = new GeogigSimpleFeature(revFeature, featureType, fid,
                attNameToRevTypeIndex);
        return feature;
    }

    public Feature build(final String id, final RevFeature revFeature,
            final GeometryFactory geometryFactory) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(revFeature);

        final FeatureId fid = new LazyVersionedFeatureId(id, revFeature.getId());

        SimpleFeatureType featureType = (SimpleFeatureType) type.type();

        GeogigSimpleFeature feature = new GeogigSimpleFeature(revFeature, featureType, fid,
                attNameToRevTypeIndex, geometryFactory);
        return feature;
    }

    private static class LazyVersionedFeatureId extends FeatureIdVersionedImpl {

        private ObjectId version;

        public LazyVersionedFeatureId(String fid, ObjectId version) {
            super(fid, null);
            this.version = version;
        }

        @Override
        public String getFeatureVersion() {
            return version.toString();
        }

        @Override
        public String getRid() {
            return new StringBuilder(getID()).append(VERSION_SEPARATOR).append(getFeatureVersion())
                    .toString();
        }
    }
}
