/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;

import com.google.common.base.Preconditions;

/**
 * A class to compactly store information about a feature, including its path and feature type. This
 * is to be used in the context of applying patches or performing a merge operation, where this type
 * of information is needed.
 * 
 * @since 1.0
 */
public class FeatureInfo {

    private RevFeature feature;

    private ObjectId featureTypeId;    
 
    private String path;

    private boolean isDelete;

    /**
     * Constructs a new {@code FeatureInfo} with the provided parameters.
     * 
     * @param feature the feature object
     * @param featureTypeId the {@link ObjectId} of the feature's type
     * @param path the path of the feature
     */
    private FeatureInfo(RevFeature feature, ObjectId featureTypeId, String path, boolean isDelete) {
        this.path = path;
        this.feature = feature;
        this.featureTypeId = featureTypeId;
        this.isDelete = isDelete;      
    }     

    public boolean isDelete() {
        return isDelete;
    }  

    public static FeatureInfo insert(RevFeature feature, ObjectId featureTypeId, String path) {
        Preconditions.checkNotNull(feature);
        Preconditions.checkNotNull(featureTypeId);
        Preconditions.checkNotNull(path);
        return new FeatureInfo(feature, featureTypeId, path, false);
    }

    public static FeatureInfo delete(final String path) {
        Preconditions.checkNotNull(path);
        return new FeatureInfo(null, null, path, true);
    }

    /**
     * The feature
     */
    public RevFeature getFeature() {
        return feature;
    }
    
    public void setFeature(RevFeature f) {
        feature = f;
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
    
    public String getName() {
        return NodeRef.nodeFromPath(getPath());
    }

}