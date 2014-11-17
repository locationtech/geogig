/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import java.util.Iterator;
import java.util.Map;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.FeatureBuilder;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.DiffFeature;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.api.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.api.plumbing.diff.Patch;
import org.opengis.feature.Feature;

import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;

/**
 * Creates a patch that represents the differences between to version of the repository *
 * 
 */
public class CreatePatchOp extends AbstractGeoGigOp<Patch> {

    /**
     * The differences between the two version of the repository that are to be stored in a patch
     * object
     */
    private Iterator<DiffEntry> diffs;

    public CreatePatchOp setDiffs(Iterator<DiffEntry> diffs) {
        this.diffs = diffs;
        return this;
    }

    @Override
    protected  Patch _call() {
        Patch patch = new Patch();
        Map<ObjectId, RevFeatureType> featureTypes = Maps.newHashMap();
        while (diffs.hasNext()) {
            DiffEntry diffEntry = diffs.next();
            final NodeRef newObject = diffEntry.getNewObject();
            final NodeRef oldObject = diffEntry.getOldObject();
            if (diffEntry.changeType() == ChangeType.MODIFIED) {
                RevObject revObject = command(RevObjectParse.class)
                        .setObjectId(diffEntry.newObjectId()).call().get();
                if (revObject instanceof RevFeature) {
                    FeatureDiff diff = command(DiffFeature.class)
                            .setNewVersion(Suppliers.ofInstance(diffEntry.getNewObject()))
                            .setOldVersion(Suppliers.ofInstance(diffEntry.getOldObject())).call();
                    patch.addModifiedFeature(diff);
                } else if (revObject instanceof RevTree) {
                    RevFeatureType oldFeatureType = command(RevObjectParse.class)
                            .setObjectId(diffEntry.getOldObject().getMetadataId())
                            .call(RevFeatureType.class).get();
                    RevFeatureType newFeatureType = command(RevObjectParse.class)
                            .setObjectId(diffEntry.getNewObject().getMetadataId())
                            .call(RevFeatureType.class).get();
                    patch.addFeatureType(oldFeatureType);
                    patch.addFeatureType(newFeatureType);
                    patch.addAlteredTree(diffEntry);
                }

            } else if (diffEntry.changeType() == ChangeType.ADDED) {
                RevObject revObject = command(RevObjectParse.class)
                        .setObjectId(diffEntry.newObjectId()).call().get();
                if (revObject instanceof RevFeature) {
                    RevFeatureType featureType;
                    if (featureTypes.containsKey(newObject.getMetadataId())) {
                        featureType = featureTypes.get(newObject.getMetadataId());
                    } else {
                        featureType = command(RevObjectParse.class)
                                .setObjectId(newObject.getMetadataId()).call(RevFeatureType.class)
                                .get();
                        featureTypes.put(newObject.getMetadataId(), featureType);
                    }

                    FeatureBuilder featureBuilder = new FeatureBuilder(featureType);
                    Feature feature = featureBuilder.build(diffEntry.newObjectId().toString(),
                            (RevFeature) revObject);
                    String name = diffEntry.newPath();
                    patch.addAddedFeature(name, feature, featureType);
                } else if (revObject instanceof RevTree) {
                    ObjectId metadataId = diffEntry.getNewObject().getMetadataId();
                    if (!metadataId.isNull()) {
                        RevFeatureType featureType = command(RevObjectParse.class)
                                .setObjectId(metadataId).call(RevFeatureType.class).get();
                        patch.addAlteredTree(diffEntry);
                        patch.addFeatureType(featureType);
                    }
                }
            } else if (diffEntry.changeType() == ChangeType.REMOVED) {
                RevObject revObject = command(RevObjectParse.class)
                        .setObjectId(diffEntry.oldObjectId()).call().get();
                if (revObject instanceof RevFeature) {
                    RevFeatureType featureType;
                    if (featureTypes.containsKey(oldObject.getMetadataId())) {
                        featureType = featureTypes.get(oldObject.getMetadataId());
                    } else {
                        featureType = command(RevObjectParse.class)
                                .setObjectId(oldObject.getMetadataId()).call(RevFeatureType.class)
                                .get();
                        featureTypes.put(oldObject.getMetadataId(), featureType);
                    }

                    FeatureBuilder featureBuilder = new FeatureBuilder(featureType);
                    Feature feature = featureBuilder.build(diffEntry.oldObjectId().toString(),
                            (RevFeature) revObject);
                    String name = diffEntry.oldPath();
                    patch.addRemovedFeature(name, feature, featureType);
                } else if (revObject instanceof RevTree) {
                    ObjectId metadataId = diffEntry.getOldObject().getMetadataId();
                    if (!metadataId.isNull()) {
                        RevFeatureType featureType = command(RevObjectParse.class)
                                .setObjectId(metadataId).call(RevFeatureType.class).get();
                        patch.addAlteredTree(diffEntry);
                        patch.addFeatureType(featureType);
                    }
                }
            }
        }

        return patch;
    }
}
