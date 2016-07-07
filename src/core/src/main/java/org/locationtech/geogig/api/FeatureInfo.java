/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import org.opengis.feature.Feature;

/**
 * A class to compactly store information about a feature, including its path and feature type. This
 * is to be used in the context of applying patches or performing a merge operation, where this type
 * of information is needed.
 * 
 */
public class FeatureInfo {

    private Feature feature;

    private ObjectId featureTypeId;

    private String path;

    public FeatureInfo(Feature feature, ObjectId featureTypeId, String path) {
        this.path = path;
        this.feature = feature;
        this.featureTypeId = featureTypeId;
    }

    /**
     * The feature
     */
    public Feature getFeature() {
        return feature;
    }

    /**
     * The id of the {@link RevFeatureType feature type} of the feature
     */
    public ObjectId getFeatureTypeId() {
        return featureTypeId;
    }

    /**
     * The path to where the feature is to be added
     */
    public String getPath() {
        return path;
    }

}