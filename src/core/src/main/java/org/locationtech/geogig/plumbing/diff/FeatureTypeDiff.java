/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import org.locationtech.geogig.model.ObjectId;

/**
 * Defines the differences between 2 versions of the a given feature type
 * 
 */
public class FeatureTypeDiff {

    private String path;

    private ObjectId newFeatureType;

    private ObjectId oldFeatureType;

    public FeatureTypeDiff(String path, ObjectId oldFeatureType, ObjectId newFeatureType) {
        this.path = path;
        this.newFeatureType = newFeatureType == null ? ObjectId.NULL : newFeatureType;
        this.oldFeatureType = oldFeatureType == null ? ObjectId.NULL : oldFeatureType;
    }

    /**
     * The Id of the new version of the feature type
     * 
     * @return
     */
    public ObjectId getNewFeatureType() {
        return newFeatureType;
    }

    /**
     * The Id of the old version of the feature type
     * 
     * @return
     */
    public ObjectId getOldFeatureType() {
        return oldFeatureType;
    }

    /**
     * The feature type path
     * 
     * @return
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the reversed version of this difference
     * 
     * @return
     */
    public FeatureTypeDiff reversed() {
        return new FeatureTypeDiff(path, newFeatureType, oldFeatureType);
    }

    public String toString() {
        return path + "\t" + oldFeatureType.toString() + "\t" + newFeatureType.toString();
    }

}