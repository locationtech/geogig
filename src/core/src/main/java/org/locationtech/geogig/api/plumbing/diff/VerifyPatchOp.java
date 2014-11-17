/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.plumbing.diff;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.FeatureInfo;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.DepthSearch;
import org.locationtech.geogig.repository.WorkingTree;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Verifies if a patch can be applied to the current working tree
 * 
 * @see WorkingTree
 * @see Patch
 */
public class VerifyPatchOp extends AbstractGeoGigOp<VerifyPatchResults> {

    private Patch patch;

    private boolean reverse;

    /**
     * Sets the patch to verify
     * 
     * @param patch the patch to verify
     * @return {@code this}
     */
    public VerifyPatchOp setPatch(Patch patch) {
        this.patch = patch;
        return this;
    }

    /**
     * Sets whether to verify the original patch or its reversed version
     * 
     * @param reverse true if the patch should be verified in its reversed version
     * @return {@code this}
     */
    public VerifyPatchOp setReverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    /**
     * Executes the verify command
     * 
     * @return the result of checking if the patch can be applied
     */
    protected VerifyPatchResults _call() throws RuntimeException {
        Preconditions.checkArgument(patch != null, "No patch file provided");

        Patch patch = reverse ? this.patch.reversed() : this.patch;

        Patch toApply = new Patch();
        Patch toReject = new Patch();
        for (RevFeatureType ft : patch.getFeatureTypes()) {
            toApply.addFeatureType(ft);
            toReject.addFeatureType(ft);
        }
        String path;
        Optional<RevObject> obj;
        List<FeatureDiff> diffs = patch.getModifiedFeatures();
        for (FeatureDiff diff : diffs) {
            path = diff.getPath();
            String refSpec = Ref.WORK_HEAD + ":" + path;
            obj = command(RevObjectParse.class).setRefSpec(refSpec).call();
            if (!obj.isPresent()) {
                toReject.addModifiedFeature(diff);
                break;
            }
            RevFeature feature = (RevFeature) obj.get();
            DepthSearch depthSearch = new DepthSearch(stagingDatabase());
            Optional<NodeRef> noderef = depthSearch.find(workingTree().getTree(), path);
            RevFeatureType featureType = command(RevObjectParse.class)
                    .setObjectId(noderef.get().getMetadataId()).call(RevFeatureType.class).get();
            ImmutableList<PropertyDescriptor> descriptors = featureType.sortedDescriptors();
            Set<Entry<PropertyDescriptor, AttributeDiff>> attrDiffs = diff.getDiffs().entrySet();
            boolean ok = true;
            for (Iterator<Entry<PropertyDescriptor, AttributeDiff>> iterator = attrDiffs.iterator(); iterator
                    .hasNext();) {
                Entry<PropertyDescriptor, AttributeDiff> entry = iterator.next();
                AttributeDiff attrDiff = entry.getValue();
                PropertyDescriptor descriptor = entry.getKey();
                switch (attrDiff.getType()) {
                case ADDED:
                    if (descriptors.contains(descriptor)) {
                        ok = false;
                    }
                    break;
                case REMOVED:
                case MODIFIED:
                    if (!descriptors.contains(descriptor)) {
                        ok = false;
                        break;
                    }
                    for (int i = 0; i < descriptors.size(); i++) {
                        if (descriptors.get(i).equals(descriptor)) {
                            Optional<Object> value = feature.getValues().get(i);
                            if (!attrDiff.canBeAppliedOn(value)) {
                                ok = false;
                            }
                            break;
                        }
                    }
                }
            }
            if (!ok) {
                toReject.addModifiedFeature(diff);
            } else {
                toApply.addModifiedFeature(diff);
            }
        }
        List<FeatureInfo> added = patch.getAddedFeatures();
        for (FeatureInfo feature : added) {
            String refSpec = Ref.WORK_HEAD + ":" + feature.getPath();
            obj = command(RevObjectParse.class).setRefSpec(refSpec).call();
            if (obj.isPresent()) {
                toReject.addAddedFeature(feature.getPath(), feature.getFeature(),
                        feature.getFeatureType());
            } else {
                toApply.addAddedFeature(feature.getPath(), feature.getFeature(),
                        feature.getFeatureType());
            }

        }
        List<FeatureInfo> removed = patch.getRemovedFeatures();
        for (FeatureInfo feature : removed) {
            String refSpec = Ref.WORK_HEAD + ":" + feature.getPath();
            obj = command(RevObjectParse.class).setRefSpec(refSpec).call();
            if (!obj.isPresent()) {
                toReject.addRemovedFeature(feature.getPath(), feature.getFeature(),
                        feature.getFeatureType());
            } else {
                RevFeature revFeature = (RevFeature) obj.get();
                DepthSearch depthSearch = new DepthSearch(stagingDatabase());
                Optional<NodeRef> noderef = depthSearch.find(workingTree().getTree(),
                        feature.getPath());
                RevFeatureType revFeatureType = command(RevObjectParse.class)
                        .setObjectId(noderef.get().getMetadataId()).call(RevFeatureType.class)
                        .get();
                RevFeature patchRevFeature = RevFeatureBuilder.build(feature.getFeature());
                if (revFeature.equals(patchRevFeature)
                        && revFeatureType.equals(feature.getFeatureType())) {
                    toApply.addRemovedFeature(feature.getPath(), feature.getFeature(),
                            feature.getFeatureType());
                } else {
                    toReject.addRemovedFeature(feature.getPath(), feature.getFeature(),
                            feature.getFeatureType());
                }
            }
        }
        ImmutableList<FeatureTypeDiff> alteredTrees = patch.getAlteredTrees();
        for (FeatureTypeDiff diff : alteredTrees) {
            DepthSearch depthSearch = new DepthSearch(stagingDatabase());
            Optional<NodeRef> noderef = depthSearch.find(workingTree().getTree(), diff.getPath());
            ObjectId metadataId = noderef.isPresent() ? noderef.get().getMetadataId()
                    : ObjectId.NULL;
            if (Objects.equal(metadataId, diff.getOldFeatureType())) {
                toApply.addAlteredTree(diff);
            } else {
                toReject.addAlteredTree(diff);
            }
        }

        return new VerifyPatchResults(toApply, toReject);

    }

}
