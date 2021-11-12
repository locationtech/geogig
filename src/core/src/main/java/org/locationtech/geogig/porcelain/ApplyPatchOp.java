/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureType.FeatureTypeBuilder;
import org.locationtech.geogig.feature.Name;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.plumbing.diff.AttributeDiff.TYPE;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.plumbing.diff.FeatureTypeDiff;
import org.locationtech.geogig.plumbing.diff.Patch;
import org.locationtech.geogig.plumbing.diff.VerifyPatchOp;
import org.locationtech.geogig.plumbing.diff.VerifyPatchResults;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.repository.impl.DepthSearch;
import org.locationtech.geogig.storage.ObjectStore;

/**
 * Applies a patch to the working tree. If partial application of the patch is allowed, it returns a
 * patch with the elements that could not be applied (might be an empty patch), or null otherwise
 * 
 * @see WorkingTree
 * @see Patch
 */
public class ApplyPatchOp extends AbstractGeoGigOp<Patch> {

    private Patch patch;

    private boolean applyPartial;

    private boolean reverse;

    /**
     * Sets the patch to apply
     * 
     * @param patch the patch to apply
     * @return {@code this}
     */
    public ApplyPatchOp setPatch(Patch patch) {
        this.patch = patch;
        return this;
    }

    /**
     * Sets whether to apply the original patch or its reversed version
     * 
     * @param reverse true if the patch should be applied in its reversed version
     * @return {@code this}
     */
    public ApplyPatchOp setReverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    /**
     * Sets whether the patch can be applied partially or not
     * 
     * @param applyPartial whether the patch can be applied partially or not
     * @return {@code this}
     */
    public ApplyPatchOp setApplyPartial(boolean applyPartial) {
        this.applyPartial = applyPartial;
        return this;
    }

    /**
     * Sets whether to use the index instead of the working tree.
     * 
     * TODO: This option is currently unused
     * 
     * @param cached whether to use the index instead of the working tree.
     * @return {@code this}
     */
    public ApplyPatchOp setCached(boolean cached) {
        // this.cached = cached;
        return this;
    }

    /**
     * Executes the apply command, applying the given patch If it cannot be applied and no partial
     * application is allowed, a {@link CannotApplyPatchException} exception is thrown. Returns a
     * patch with rejected entries, in case partial application is allowed
     * 
     * @return the modified {@link WorkingTree working tree}.
     */
    protected @Override Patch _call() throws RuntimeException {
        Preconditions.checkArgument(patch != null, "No patch file provided");

        VerifyPatchResults verify = command(VerifyPatchOp.class).setPatch(patch).setReverse(reverse)
                .call();
        Patch toReject = verify.getToReject();
        Patch toApply = verify.getToApply();

        if (!applyPartial) {
            if (!toReject.isEmpty()) {
                throw new CannotApplyPatchException(toReject);
            }
            applyPatch(toApply);
            return null;

        } else {
            applyPatch(toApply);
            return toReject;
        }

    }

    private void applyPatch(Patch patch) {
        final WorkingTree workTree = workingTree();
        final ObjectStore indexDb = objectDatabase();
        if (reverse) {
            patch = patch.reversed();
        }

        objectDatabase().putAll(patch.getFeatureTypes().iterator());

        List<FeatureInfo> removed = patch.getRemovedFeatures();
        for (FeatureInfo feature : removed) {
            workTree.delete(feature.getPath());
        }
        List<FeatureInfo> added = patch.getAddedFeatures();
        for (FeatureInfo feature : added) {
            workTree.insert(feature);
        }
        List<FeatureDiff> diffs = patch.getModifiedFeatures();
        for (FeatureDiff diff : diffs) {
            String path = diff.getPath();
            DepthSearch depthSearch = new DepthSearch(indexDb);
            Optional<NodeRef> noderef = depthSearch.find(workTree.getTree(), path);
            RevFeatureType oldRevFeatureType = command(RevObjectParse.class)
                    .setObjectId(noderef.get().metadataId()).call(RevFeatureType.class).get();
            String refSpec = Ref.WORK_HEAD + ":" + path;
            RevFeature feature = command(RevObjectParse.class).setRefSpec(refSpec)
                    .call(RevFeature.class).get();

            RevFeatureType newRevFeatureType = getFeatureType(diff, feature, oldRevFeatureType);
            List<PropertyDescriptor> oldDescriptors = oldRevFeatureType.descriptors();
            List<PropertyDescriptor> newDescriptors = newRevFeatureType.descriptors();
            Map<Name, Object> attrs = new HashMap<>();
            for (int i = 0; i < oldDescriptors.size(); i++) {
                PropertyDescriptor descriptor = oldDescriptors.get(i);
                if (newDescriptors.contains(descriptor)) {
                    Optional<Object> value = feature.get(i);
                    attrs.put(descriptor.getName(), value.orElse(null));
                }
            }
            Set<Entry<PropertyDescriptor, AttributeDiff>> featureDiffs = diff.getDiffs().entrySet();
            for (Iterator<Entry<PropertyDescriptor, AttributeDiff>> iterator = featureDiffs
                    .iterator(); iterator.hasNext();) {
                Entry<PropertyDescriptor, AttributeDiff> entry = iterator.next();
                if (!entry.getValue().getType().equals(TYPE.REMOVED)) {
                    Object oldValue = attrs.get(entry.getKey().getName());
                    attrs.put(entry.getKey().getName(), entry.getValue().applyOn(oldValue));
                }
            }

            Feature result = Feature.build(NodeRef.nodeFromPath(path), newRevFeatureType.type());
            Set<Entry<Name, Object>> entries = attrs.entrySet();
            for (Iterator<Entry<Name, Object>> iterator = entries.iterator(); iterator.hasNext();) {
                Entry<Name, Object> entry = iterator.next();
                result.setAttribute(entry.getKey(), entry.getValue());
            }

            RevFeature featureToInsert = RevFeature.builder().build(result);
            FeatureInfo featureInfo = FeatureInfo.insert(featureToInsert, newRevFeatureType.getId(),
                    path);
            workTree.insert(featureInfo);

        }
        List<FeatureTypeDiff> alteredTrees = patch.getAlteredTrees();
        for (FeatureTypeDiff diff : alteredTrees) {
            Optional<RevFeatureType> featureType;
            if (diff.getOldFeatureType().isNull()) {
                featureType = patch.getFeatureTypeFromId(diff.getNewFeatureType());
                workTree.createTypeTree(diff.getPath(), featureType.get().type());
            } else if (diff.getNewFeatureType().isNull()) {
                workTree.delete(diff.getPath());
            } else {
                featureType = patch.getFeatureTypeFromId(diff.getNewFeatureType());
                workTree.updateTypeTree(diff.getPath(), featureType.get().type());
            }
        }

    }

    private RevFeatureType getFeatureType(FeatureDiff diff, RevFeature oldFeature,
            RevFeatureType oldRevFeatureType) {
        List<String> removed = new ArrayList<>();
        List<PropertyDescriptor> added = new ArrayList<>();

        Set<Entry<PropertyDescriptor, AttributeDiff>> featureDiffs = diff.getDiffs().entrySet();
        for (Iterator<Entry<PropertyDescriptor, AttributeDiff>> iterator = featureDiffs
                .iterator(); iterator.hasNext();) {
            Entry<PropertyDescriptor, AttributeDiff> entry = iterator.next();
            if (entry.getValue().getType() == TYPE.REMOVED) {
                removed.add(entry.getKey().getName().getLocalPart());
            } else if (entry.getValue().getType() == TYPE.ADDED) {
                PropertyDescriptor pd = entry.getKey();
                added.add(pd);
            }
        }

        FeatureType sft = oldRevFeatureType.type();
        final List<PropertyDescriptor> descriptors = sft.getDescriptors();
        FeatureTypeBuilder featureTypeBuilder = FeatureType.builder().name(sft.getName());
        for (int i = 0; i < descriptors.size(); i++) {
            PropertyDescriptor descriptor = descriptors.get(i);
            if (!removed.contains(descriptor.getName().getLocalPart())) {
                featureTypeBuilder.add(descriptor);
            }
        }
        for (PropertyDescriptor descriptor : added) {
            featureTypeBuilder.add(descriptor);
        }
        FeatureType featureType = featureTypeBuilder.build();

        return RevFeatureType.builder().type(featureType).build();
    }

}
