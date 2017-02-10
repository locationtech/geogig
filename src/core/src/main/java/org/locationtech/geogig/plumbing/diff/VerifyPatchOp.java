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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.DepthSearch;
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

        Map<ObjectId, RevFeatureType> typeCache = patch.featureTypes();

        Patch toApply = new Patch(typeCache);
        Patch toReject = new Patch(typeCache);

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
            DepthSearch depthSearch = new DepthSearch(objectDatabase());
            Optional<NodeRef> noderef = depthSearch.find(workingTree().getTree(), path);
            RevFeatureType featureType = command(RevObjectParse.class)
                    .setObjectId(noderef.get().getMetadataId()).call(RevFeatureType.class).get();
            ImmutableList<PropertyDescriptor> descriptors = featureType.descriptors();
            Set<Entry<PropertyDescriptor, AttributeDiff>> attrDiffs = diff.getDiffs().entrySet();
            boolean ok = true;
            for (Iterator<Entry<PropertyDescriptor, AttributeDiff>> iterator = attrDiffs
                    .iterator(); iterator.hasNext();) {
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
                            Optional<Object> value = feature.get(i);
                            if (!attrDiff.canBeAppliedOn(value.orNull())) {
                                ok = false;
                            }
                            break;
                        }
                    }
                case NO_CHANGE:
                    break;// nothing to do
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
                        getType(feature.getFeatureTypeId(), typeCache));
            } else {
                toApply.addAddedFeature(feature.getPath(), feature.getFeature(),
                        getType(feature.getFeatureTypeId(), typeCache));
            }

        }
        List<FeatureInfo> removed = patch.getRemovedFeatures();
        for (FeatureInfo feature : removed) {
            String refSpec = Ref.WORK_HEAD + ":" + feature.getPath();
            obj = command(RevObjectParse.class).setRefSpec(refSpec).call();
            if (!obj.isPresent()) {
                toReject.addRemovedFeature(feature.getPath(), feature.getFeature(),
                        getType(feature.getFeatureTypeId(), typeCache));
            } else {
                RevFeature revFeature = (RevFeature) obj.get();
                DepthSearch depthSearch = new DepthSearch(objectDatabase());
                Optional<NodeRef> noderef = depthSearch.find(workingTree().getTree(),
                        feature.getPath());
                ObjectId revFeatureTypeId = noderef.get().getMetadataId();
                RevFeature patchRevFeature = feature.getFeature();
                if (revFeature.equals(patchRevFeature)
                        && revFeatureTypeId.equals(feature.getFeatureTypeId())) {
                    toApply.addRemovedFeature(feature.getPath(), feature.getFeature(),
                            getType(feature.getFeatureTypeId(), typeCache));
                } else {
                    toReject.addRemovedFeature(feature.getPath(), feature.getFeature(),
                            getType(feature.getFeatureTypeId(), typeCache));
                }
            }
        }
        ImmutableList<FeatureTypeDiff> alteredTrees = patch.getAlteredTrees();
        for (FeatureTypeDiff diff : alteredTrees) {
            DepthSearch depthSearch = new DepthSearch(objectDatabase());
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

    private RevFeatureType getType(ObjectId featureTypeId,
            Map<ObjectId, RevFeatureType> typeCache) {

        RevFeatureType type = typeCache.get(featureTypeId);
        if (null == type) {
            type = objectDatabase().getFeatureType(featureTypeId);
            typeCache.put(featureTypeId, type);
        }
        return type;
    }

}
