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

import java.util.Map;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.DiffFeature;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.plumbing.diff.Patch;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;

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
    private AutoCloseableIterator<DiffEntry> diffs;

    public CreatePatchOp setDiffs(AutoCloseableIterator<DiffEntry> diffs) {
        this.diffs = diffs;
        return this;
    }

    @Override
    protected Patch _call() {
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

                    String name = diffEntry.newPath();
                    patch.addAddedFeature(name, (RevFeature) revObject, featureType);
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
                    String name = diffEntry.oldPath();
                    patch.addRemovedFeature(name, (RevFeature) revObject, featureType);
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
