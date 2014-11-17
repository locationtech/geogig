/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.plumbing.diff.FeatureDiff;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;

/**
 * Compares two features in the repository and returns a {code FeatureDiff} object representing it.
 * Checking is performed to ensure that the old and new features actually correspond to two versions
 * of the same feature and not to two unrelated features, so the corresponding NodeRefs have to
 * point to the same path.
 * 
 */
public class DiffFeature extends AbstractGeoGigOp<FeatureDiff> {

    private NodeRef oldNodeRef;

    private NodeRef newNodeRef;

    /**
     * @param oldNodeRef the ref that points to the "old" version of the feature to compare
     * @return {@code this}
     */
    public DiffFeature setOldVersion(Supplier<NodeRef> oldNodeRef) {
        this.oldNodeRef = oldNodeRef.get();
        return this;
    }

    /**
     * @param oldNodeRef the ref that points to the "old" version of the feature to compare
     * @return {@code this}
     */
    public DiffFeature setNewVersion(Supplier<NodeRef> newNodeRef) {
        this.newNodeRef = newNodeRef.get();
        return this;
    }

    /**
     * Finds differences between the two specified trees.
     * 
     * @return a FeatureDiff object with the differences between the specified features
     * @see FeatureDiff
     */
    @Override
    protected  FeatureDiff _call() throws IllegalArgumentException {
        checkNotNull(oldNodeRef, "old version not specified");
        checkNotNull(newNodeRef, "new version not specified");
        String oldPath = removeRef(oldNodeRef.path());
        String newPath = removeRef(newNodeRef.path());
        checkArgument(oldPath.equals(newPath),
                "old and new versions do not corespond to the same feature");

        Optional<RevFeature> oldFeature = command(RevObjectParse.class).setObjectId(
                oldNodeRef.getNode().getObjectId()).call(RevFeature.class);
        checkArgument(oldFeature.isPresent(), "Invalid reference: %s", oldNodeRef);

        Optional<RevFeature> newFeature = command(RevObjectParse.class).setObjectId(
                newNodeRef.getNode().getObjectId()).call(RevFeature.class);
        checkArgument(newFeature.isPresent(), "Invalid reference: %s", newNodeRef);

        Optional<RevFeatureType> oldFeatureType = command(RevObjectParse.class).setObjectId(
                oldNodeRef.getMetadataId()).call(RevFeatureType.class);
        checkArgument(oldFeatureType.isPresent(), "Invalid reference: %s", oldNodeRef);

        Optional<RevFeatureType> newFeatureType = command(RevObjectParse.class).setObjectId(
                newNodeRef.getMetadataId()).call(RevFeatureType.class);
        checkArgument(newFeatureType.isPresent(), "Invalid reference: %s", newNodeRef);

        return compare(oldFeature.get(), newFeature.get(), oldFeatureType.get(),
                newFeatureType.get());

    }

    private String removeRef(String path) {
        if (path.contains(":")) {
            return path.substring(path.indexOf(":") + 1);
        } else {
            return path;
        }
    }

    private FeatureDiff compare(RevFeature oldRevFeature, RevFeature newRevFeature,
            RevFeatureType oldRevFeatureType, RevFeatureType newRevFeatureType) {

        return new FeatureDiff(oldNodeRef.path(), newRevFeature, oldRevFeature, newRevFeatureType,
                oldRevFeatureType, false);
    }

}
