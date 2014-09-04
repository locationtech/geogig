/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.merge;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.FeatureInfo;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.DiffFeature;
import org.locationtech.geogig.api.plumbing.DiffTree;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.ResolveObjectType;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.api.plumbing.diff.FeatureDiff;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;

/**
 * Reports conflicts between changes introduced by two different histories. Given a commit and
 * another reference commit, it returns the set of changes from the common ancestor to the first
 * commit, classified according to whether they can or not be safely applied onto the reference
 * commit. Changes that will have no effect on the target commit are not included as unconflicted.
 */
public class ReportMergeScenarioOp extends AbstractGeoGigOp<MergeScenarioReport> {

    private RevCommit toMerge;

    private RevCommit mergeInto;

    /**
     * @param toMerge the commit with the changes to apply {@link RevCommit}
     */
    public ReportMergeScenarioOp setToMergeCommit(RevCommit toMerge) {
        this.toMerge = toMerge;
        return this;
    }

    /**
     * @param mergeInto the commit into which changes are to be merged {@link RevCommit}
     */
    public ReportMergeScenarioOp setMergeIntoCommit(RevCommit mergeInto) {
        this.mergeInto = mergeInto;
        return this;
    }

    @Override
    protected MergeScenarioReport _call() {

        Optional<ObjectId> ancestor = command(FindCommonAncestor.class).setLeft(toMerge)
                .setRight(mergeInto).call();
        Preconditions.checkState(ancestor.isPresent(), "No ancestor commit could be found.");

        Map<String, DiffEntry> mergeIntoDiffs = Maps.newHashMap();
        MergeScenarioReport report = new MergeScenarioReport();

        Iterator<DiffEntry> diffs = command(DiffTree.class).setOldTree(ancestor.get())
                .setReportTrees(true).setNewTree(mergeInto.getId()).call();
        while (diffs.hasNext()) {
            DiffEntry diff = diffs.next();
            String path = diff.oldPath() == null ? diff.newPath() : diff.oldPath();
            mergeIntoDiffs.put(path, diff);
        }

        Iterator<DiffEntry> toMergeDiffs = command(DiffTree.class).setOldTree(ancestor.get())
                .setReportTrees(true).setNewTree(toMerge.getId()).call();
        while (toMergeDiffs.hasNext()) {
            DiffEntry toMergeDiff = toMergeDiffs.next();
            String path = toMergeDiff.oldPath() == null ? toMergeDiff.newPath() : toMergeDiff
                    .oldPath();
            if (mergeIntoDiffs.containsKey(path)) {
                RevCommit ancestorCommit = command(RevObjectParse.class)
                        .setRefSpec(ancestor.get().toString()).call(RevCommit.class).get();
                RevTree ancestorTree = command(RevObjectParse.class)
                        .setObjectId(ancestorCommit.getTreeId()).call(RevTree.class).get();
                Optional<NodeRef> ancestorVersion = command(FindTreeChild.class).setChildPath(path)
                        .setParent(ancestorTree).call();
                ObjectId ancestorVersionId = ancestorVersion.isPresent() ? ancestorVersion.get()
                        .getNode().getObjectId() : ObjectId.NULL;
                ObjectId theirs = toMergeDiff.getNewObject() == null ? ObjectId.NULL : toMergeDiff
                        .getNewObject().objectId();
                DiffEntry mergeIntoDiff = mergeIntoDiffs.get(path);
                ObjectId ours = mergeIntoDiff.getNewObject() == null ? ObjectId.NULL
                        : mergeIntoDiff.getNewObject().objectId();
                if (!mergeIntoDiff.changeType().equals(toMergeDiff.changeType())) {
                    report.addConflict(new Conflict(path, ancestorVersionId, ours, theirs));
                    continue;
                }
                switch (toMergeDiff.changeType()) {
                case ADDED:
                    if (toMergeDiff.getNewObject().equals(mergeIntoDiff.getNewObject())) {
                        // already added in current branch, no need to do anything
                    } else {
                        TYPE type = command(ResolveObjectType.class).setObjectId(
                                toMergeDiff.getNewObject().objectId()).call();
                        if (TYPE.TREE.equals(type)) {
                            boolean conflict = !toMergeDiff.getNewObject().getMetadataId()
                                    .equals(mergeIntoDiff.getNewObject().getMetadataId());
                            if (conflict) {
                                // In this case, we store the metadata id, not the element id
                                ancestorVersionId = ancestorVersion.isPresent() ? ancestorVersion
                                        .get().getMetadataId() : ObjectId.NULL;
                                ours = mergeIntoDiff.getNewObject().getMetadataId();
                                theirs = toMergeDiff.getNewObject().getMetadataId();
                                report.addConflict(new Conflict(path, ancestorVersionId, ours,
                                        theirs));
                            }
                            // if the metadata ids match, it means both branches have added the same
                            // tree, maybe with different content, but there is no need to do
                            // anything. The correct tree is already there and the merge can be run
                            // safely, so we do not add it neither as a conflicted change nor as an
                            // unconflicted one
                        } else {
                            report.addConflict(new Conflict(path, ancestorVersionId, ours, theirs));
                        }
                    }
                    break;
                case REMOVED:
                    // removed by both histories => no conflict and no need to do anything
                    break;
                case MODIFIED:
                    TYPE type = command(ResolveObjectType.class).setObjectId(
                            toMergeDiff.getNewObject().objectId()).call();
                    if (TYPE.TREE.equals(type)) {
                        boolean conflict = !toMergeDiff.getNewObject().getMetadataId()
                                .equals(mergeIntoDiff.getNewObject().getMetadataId());
                        if (conflict) {
                            // In this case, we store the metadata id, not the element id
                            ancestorVersionId = ancestorVersion.isPresent() ? ancestorVersion.get()
                                    .getMetadataId() : ObjectId.NULL;
                            ours = mergeIntoDiff.getNewObject().getMetadataId();
                            theirs = toMergeDiff.getNewObject().getMetadataId();
                            report.addConflict(new Conflict(path, ancestorVersionId, ours, theirs));
                        }
                    } else {
                        FeatureDiff toMergeFeatureDiff = command(DiffFeature.class)
                                .setOldVersion(Suppliers.ofInstance(toMergeDiff.getOldObject()))
                                .setNewVersion(Suppliers.ofInstance(toMergeDiff.getNewObject()))
                                .call();
                        FeatureDiff mergeIntoFeatureDiff = command(DiffFeature.class)
                                .setOldVersion(Suppliers.ofInstance(mergeIntoDiff.getOldObject()))
                                .setNewVersion(Suppliers.ofInstance(mergeIntoDiff.getNewObject()))
                                .call();
                        if (toMergeFeatureDiff.conflicts(mergeIntoFeatureDiff)) {
                            report.addConflict(new Conflict(path, ancestorVersionId, ours, theirs));
                        } else {
                            // if the feature types are different we report a conflict and do not
                            // try to perform automerge
                            if (!toMergeDiff.getNewObject().getMetadataId()
                                    .equals(mergeIntoDiff.getNewObject().getMetadataId())) {
                                report.addConflict(new Conflict(path, ancestorVersionId, ours,
                                        theirs));
                            } else if (!toMergeFeatureDiff.equals(mergeIntoFeatureDiff)) {
                                Feature mergedFeature = command(MergeFeaturesOp.class)
                                        .setFirstFeature(mergeIntoDiff.getNewObject())
                                        .setSecondFeature(toMergeDiff.getNewObject())
                                        .setAncestorFeature(mergeIntoDiff.getOldObject()).call();
                                RevFeature revFeature = RevFeatureBuilder.build(mergedFeature);
                                if (revFeature.getId().equals(toMergeDiff.newObjectId())) {
                                    // the resulting merged feature equals the feature to merge from
                                    // the branch, which means that it exists in the repo and there
                                    // is no need to add it
                                    report.addUnconflicted(toMergeDiff);
                                } else {
                                    RevFeatureType featureType = command(RevObjectParse.class)
                                            .setObjectId(
                                                    mergeIntoDiff.getNewObject().getMetadataId())
                                            .call(RevFeatureType.class).get();
                                    FeatureInfo merged = new FeatureInfo(mergedFeature,
                                            featureType, path);
                                    report.addMerged(merged);
                                }
                            }
                        }
                    }
                    break;
                }
            } else {
                // If the element is a tree, not a feature, it might be a conflict even if the other
                // branch has not modified it.
                // If we are removing the tree, we have to make sure that there are no features
                // modified in the other branch under it.
                if (ChangeType.REMOVED.equals(toMergeDiff.changeType())) {
                    TYPE type = command(ResolveObjectType.class).setObjectId(
                            toMergeDiff.oldObjectId()).call();
                    if (TYPE.TREE.equals(type)) {
                        String parentPath = toMergeDiff.oldPath();
                        Set<Entry<String, DiffEntry>> entries = mergeIntoDiffs.entrySet();
                        boolean conflict = false;
                        for (Entry<String, DiffEntry> entry : entries) {
                            if (entry.getKey().startsWith(parentPath)) {
                                if (!ChangeType.REMOVED.equals(entry.getValue().changeType())) {
                                    RevCommit ancestorCommit = command(RevObjectParse.class)
                                            .setRefSpec(ancestor.get().toString())
                                            .call(RevCommit.class).get();
                                    RevTree ancestorTree = command(RevObjectParse.class)
                                            .setObjectId(ancestorCommit.getTreeId())
                                            .call(RevTree.class).get();
                                    Optional<NodeRef> ancestorVersion = command(FindTreeChild.class)
                                            .setChildPath(path).setParent(ancestorTree).call();
                                    ObjectId ancestorVersionId = ancestorVersion.isPresent() ? ancestorVersion
                                            .get().getNode().getObjectId()
                                            : ObjectId.NULL;
                                    ObjectId theirs = toMergeDiff.getNewObject() == null ? ObjectId.NULL
                                            : toMergeDiff.getNewObject().objectId();
                                    String oursRefSpec = mergeInto.getId().toString() + ":"
                                            + parentPath;
                                    Optional<ObjectId> ours = command(RevParse.class).setRefSpec(
                                            oursRefSpec).call();
                                    report.addConflict(new Conflict(path, ancestorVersionId, ours
                                            .get(), theirs));
                                    conflict = true;
                                    break;
                                }
                            }
                        }
                        if (!conflict) {
                            report.addUnconflicted(toMergeDiff);
                        }
                    } else {
                        report.addUnconflicted(toMergeDiff);
                    }
                } else {
                    report.addUnconflicted(toMergeDiff);
                }
            }

        }

        return report;

    }
}
