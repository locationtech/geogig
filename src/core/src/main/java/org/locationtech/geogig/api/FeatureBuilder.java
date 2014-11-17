/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api;

import java.util.Map;

import org.geotools.filter.identity.FeatureIdVersionedImpl;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.identity.FeatureId;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableList;

/**
 * Provides a method of building features from {@link RevFeature} objects that have the type
 * specified by the given {@link RevFeatureType}.
 * 
 * @see RevFeatureType
 * @see RevFeature
 * @see Feature
 */
public class FeatureBuilder {

    private FeatureType featureType;

    private Map<String, Integer> attNameToRevTypeIndex;

    private BiMap<Integer, Integer> typeToRevTypeIndex;

    private RevFeatureType type;

    /**
     * Constructs a new {@code FeatureBuilder} with the given {@link RevFeatureType feature type}.
     * 
     * @param type the feature type of the features that will be built
     */
    public FeatureBuilder(RevFeatureType type) {
        this.type = type;
        this.featureType = type.type();
        this.attNameToRevTypeIndex = GeogigSimpleFeature.buildAttNameToRevTypeIndex(type);
        this.typeToRevTypeIndex = GeogigSimpleFeature.buildTypeToRevTypeIndex(type);
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
        this(RevFeatureTypeImpl.build(type));
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

        final String version = revFeature.getId().toString();
        final FeatureId fid = new FeatureIdVersionedImpl(id, version);

        ImmutableList<Optional<Object>> values = revFeature.getValues();
        GeogigSimpleFeature feature = new GeogigSimpleFeature(values,
                (SimpleFeatureType) featureType, fid, attNameToRevTypeIndex, typeToRevTypeIndex);
        return feature;
    }

}
