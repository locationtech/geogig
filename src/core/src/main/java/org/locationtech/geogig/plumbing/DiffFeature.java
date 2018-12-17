/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Supplier;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;

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

    public DiffFeature setOldVersion(NodeRef oldNodeRef) {
        this.oldNodeRef = oldNodeRef;
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

    public DiffFeature setNewVersion(NodeRef newNodeRef) {
        this.newNodeRef = newNodeRef;
        return this;
    }

    /**
     * Finds differences between the two specified trees.
     * 
     * @return a FeatureDiff object with the differences between the specified features
     * @see FeatureDiff
     */
    @Override
    protected FeatureDiff _call() throws IllegalArgumentException {
        checkNotNull(oldNodeRef, "old version not specified");
        checkNotNull(newNodeRef, "new version not specified");
        String oldPath = removeRef(oldNodeRef.path());
        String newPath = removeRef(newNodeRef.path());
        checkArgument(oldPath.equals(newPath),
                "old and new versions do not corespond to the same feature");

        Set<ObjectId> ids = Sets.newHashSet(oldNodeRef.getObjectId(), newNodeRef.getObjectId(),
                oldNodeRef.getMetadataId(), newNodeRef.getMetadataId());

        Map<ObjectId, RevObject> objects = Streams.stream(objectDatabase().getAll(ids))
                .collect(Collectors.toMap(RevObject::getId, revobj -> revobj));

        RevFeature oldFeature = (RevFeature) objects.get(oldNodeRef.getObjectId());
        checkArgument(oldFeature != null, "Invalid reference: %s", oldNodeRef);

        RevFeature newFeature = (RevFeature) objects.get(newNodeRef.getObjectId());
        checkArgument(newFeature != null, "Invalid reference: %s", newNodeRef);

        RevFeatureType oldFeatureType = (RevFeatureType) objects.get(oldNodeRef.getMetadataId());
        checkArgument(oldFeatureType != null, "Invalid reference: %s", oldNodeRef);

        RevFeatureType newFeatureType = (RevFeatureType) objects.get(newNodeRef.getMetadataId());
        checkArgument(newFeatureType != null, "Invalid reference: %s", newNodeRef);

        return compare(oldNodeRef.path(), oldFeature, newFeature, oldFeatureType, newFeatureType);

    }

    private String removeRef(String path) {
        final int separatorIndex = path.indexOf(':');
        if (separatorIndex > -1) {
            return path.substring(separatorIndex + 1);
        } else {
            return path;
        }
    }

    public static FeatureDiff compare(String path, RevFeature oldRevFeature,
            RevFeature newRevFeature, RevFeatureType oldRevFeatureType,
            RevFeatureType newRevFeatureType) {

        return new FeatureDiff(path, newRevFeature, oldRevFeature, newRevFeatureType,
                oldRevFeatureType, false);
    }

}
