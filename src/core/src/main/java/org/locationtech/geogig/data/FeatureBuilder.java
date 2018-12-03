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

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.identity.FeatureIdVersionedImpl;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.FeatureId;

import com.google.common.base.Preconditions;

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

    private SimpleFeatureType featureType;

    /**
     * Constructs a new {@code FeatureBuilder} with the given {@link RevFeatureType feature type}.
     * 
     * @param type the feature type of the features that will be built
     */
    public FeatureBuilder(RevFeatureType type) {
        this(type, null);
    }

    /**
     * @param type the native type of the features being built
     * @param typeNameOverride if provided, the resulting feature type for the features will be
     *        renamed as this
     */
    public FeatureBuilder(RevFeatureType type, @Nullable Name typeNameOverride) {
        this.type = type;
        this.attNameToRevTypeIndex = GeogigSimpleFeature.buildAttNameToRevTypeIndex(type);
        SimpleFeatureType nativeType = (SimpleFeatureType) type.type();
        if (null == typeNameOverride) {
            this.featureType = nativeType;
        } else {
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.init(nativeType);
            builder.setName(typeNameOverride);
            this.featureType = builder.buildFeatureType();
        }
    }

    public RevFeatureType getType() {
        return type;
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

        GeogigSimpleFeature feature = new GeogigSimpleFeature(revFeature, featureType, fid,
                attNameToRevTypeIndex);
        return feature;
    }

    public Feature build(final String id, final RevFeature revFeature,
            final @Nullable GeometryFactory geometryFactory) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(revFeature);

        final FeatureId fid = new LazyVersionedFeatureId(id, revFeature.getId());

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
