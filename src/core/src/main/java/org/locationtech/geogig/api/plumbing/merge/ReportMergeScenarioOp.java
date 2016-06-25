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
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.FeatureDiff;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

/**
 * Reports conflicts between changes introduced by two different histories. Given a commit and
 * another reference commit, it returns the set of changes from the common ancestor to the first
 * commit, classified according to whether they can or not be safely applied onto the reference
 * commit. Changes that will have no effect on the target commit are not included as unconflicted.
 */
public class ReportMergeScenarioOp extends AbstractGeoGigOp<MergeScenarioReport> {

    private RevCommit toMerge;

    private RevCommit mergeInto;

    private MergeScenarioConsumer consumer = null;

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

    public ReportMergeScenarioOp setConsumer(MergeScenarioConsumer consumer) {
        this.consumer = consumer;
        return this;
    }

    @Override
    protected MergeScenarioReport _call() {
        if (consumer == null) {
            consumer = new MergeScenarioConsumer();
        }
        final Optional<ObjectId> ancestor = command(FindCommonAncestor.class).setLeft(toMerge)
                .setRight(mergeInto).call();
        Preconditions.checkState(ancestor.isPresent(), "No ancestor commit could be found.");

        MergeScenarioReport report = new MergeScenarioReport();

        PeekingIterator<DiffEntry> mergeIntoDiffs = Iterators.peekingIterator(
                command(DiffTree.class).setOldTree(ancestor.get()).setReportTrees(true)
                        .setNewTree(mergeInto.getId()).setPreserveIterationOrder(true).call());

        final RevCommit ancestorCommit = objectDatabase().getCommit(ancestor.get());
        final RevTree ancestorTree = objectDatabase().getTree(ancestorCommit.getTreeId());

        PeekingIterator<DiffEntry> toMergeDiffs = Iterators.peekingIterator(
                command(DiffTree.class).setOldTree(ancestor.get()).setReportTrees(true)
                        .setNewTree(toMerge.getId()).setPreserveIterationOrder(true).call());

        while (toMergeDiffs.hasNext()) {
            processNext(mergeIntoDiffs, toMergeDiffs, ancestorTree, report);
            if (consumer.isCancelled()) {
                break;
            }
        }

        if (!consumer.isCancelled() || !toMergeDiffs.hasNext()) {
            consumer.finished();
        }

        return report;

    }

    /**
     * Process the next modified path in the diff.
     * 
     * @param mergeIntoDiffs diffs from "our" branch
     * @param toMergeDiffs diffs from "their" branch
     * @param ancestorTree root tree for the ancestor commit of both branches
     * @param report the merge scenario report.
     * @return
     */
    private void processNext(PeekingIterator<DiffEntry> mergeIntoDiffs,
            PeekingIterator<DiffEntry> toMergeDiffs, RevTree ancestorTree,
            MergeScenarioReport report) {
        DiffEntry mergeIntoDiff = mergeIntoDiffs.hasNext() ? mergeIntoDiffs.peek() : null;
        DiffEntry toMergeDiff = toMergeDiffs.peek();
        // If merge into diff is null, that means the path will be unconflicted, no need to compare.
        int compare = mergeIntoDiff != null
                ? DiffEntry.COMPARATOR.compare(mergeIntoDiff, toMergeDiff) : 1;
        if (compare < 0) {
            // Only "our" branch modified the path, advance "our" iterator
            mergeIntoDiffs.next();
        } else if (compare > 0) {
            // Only "their" branch modified the path, advance "their" iterator
            toMergeDiffs.next();

            consumer.unconflicted(toMergeDiff);
            report.addUnconflicted();
        } else {
            // Same path modified on both, advance both iterators
            mergeIntoDiffs.next();
            toMergeDiffs.next();

            String path = mergeIntoDiff.oldPath() != null ? mergeIntoDiff.oldPath()
                    : mergeIntoDiff.newPath();

            Optional<NodeRef> ancestorVersion = command(FindTreeChild.class).setChildPath(path)
                    .setParent(ancestorTree).call();
            ObjectId ancestorVersionId = ancestorVersion.isPresent()
                    ? ancestorVersion.get().getNode().getObjectId() : ObjectId.NULL;
            ObjectId theirs = toMergeDiff.getNewObject() == null ? ObjectId.NULL
                    : toMergeDiff.getNewObject().getObjectId();
            ObjectId ours = mergeIntoDiff.getNewObject() == null ? ObjectId.NULL
                    : mergeIntoDiff.getNewObject().getObjectId();
            if (!mergeIntoDiff.changeType().equals(toMergeDiff.changeType())) {
                consumer.conflicted(new Conflict(path, ancestorVersionId, ours, theirs));
                report.addConflict();
                return;
            }
            switch (toMergeDiff.changeType()) {
            case ADDED:
                if (toMergeDiff.getNewObject().equals(mergeIntoDiff.getNewObject())) {
                    // already added in current branch, no need to do anything
                } else {
                    final TYPE type = toMergeDiff.getNewObject().getType();
                    if (TYPE.TREE.equals(type)) {
                        boolean conflict = !toMergeDiff.getNewObject().getMetadataId()
                                .equals(mergeIntoDiff.getNewObject().getMetadataId());
                        if (conflict) {
                            // In this case, we store the metadata id, not the element id
                            ancestorVersionId = ancestorVersion.isPresent()
                                    ? ancestorVersion.get().getMetadataId() : ObjectId.NULL;
                            ours = mergeIntoDiff.getNewObject().getMetadataId();
                            theirs = toMergeDiff.getNewObject().getMetadataId();
                            consumer.conflicted(
                                    new Conflict(path, ancestorVersionId, ours, theirs));
                            report.addConflict();
                        }
                        // if the metadata ids match, it means both branches have added the same
                        // tree, maybe with different content, but there is no need to do
                        // anything. The correct tree is already there and the merge can be run
                        // safely, so we do not add it neither as a conflicted change nor as an
                        // unconflicted one
                    } else {
                        consumer.conflicted(new Conflict(path, ancestorVersionId, ours, theirs));
                        report.addConflict();
                    }
                }
                break;
            case REMOVED:
                // removed by both histories => no conflict and no need to do anything
                break;
            case MODIFIED:
                final TYPE type = toMergeDiff.getNewObject().getType();
                if (TYPE.TREE.equals(type)) {
                    boolean conflict = !toMergeDiff.getNewObject().getMetadataId()
                            .equals(mergeIntoDiff.getNewObject().getMetadataId());
                    if (conflict) {
                        // In this case, we store the metadata id, not the element id
                        ancestorVersionId = ancestorVersion.isPresent()
                                ? ancestorVersion.get().getMetadataId() : ObjectId.NULL;
                        ours = mergeIntoDiff.getNewObject().getMetadataId();
                        theirs = toMergeDiff.getNewObject().getMetadataId();
                        consumer.conflicted(new Conflict(path, ancestorVersionId, ours, theirs));
                        report.addConflict();
                    }
                } else {
                    FeatureDiff toMergeFeatureDiff = command(DiffFeature.class)
                            .setOldVersion(Suppliers.ofInstance(toMergeDiff.getOldObject()))
                            .setNewVersion(Suppliers.ofInstance(toMergeDiff.getNewObject())).call();
                    FeatureDiff mergeIntoFeatureDiff = command(DiffFeature.class)
                            .setOldVersion(Suppliers.ofInstance(mergeIntoDiff.getOldObject()))
                            .setNewVersion(Suppliers.ofInstance(mergeIntoDiff.getNewObject()))
                            .call();
                    if (toMergeFeatureDiff.conflicts(mergeIntoFeatureDiff)) {
                        consumer.conflicted(new Conflict(path, ancestorVersionId, ours, theirs));
                        report.addConflict();
                    } else {
                        // if the feature types are different we report a conflict and do not
                        // try to perform automerge
                        if (!toMergeDiff.getNewObject().getMetadataId()
                                .equals(mergeIntoDiff.getNewObject().getMetadataId())) {
                            consumer.conflicted(
                                    new Conflict(path, ancestorVersionId, ours, theirs));
                            report.addConflict();
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
                                consumer.unconflicted(toMergeDiff);
                                report.addUnconflicted();
                            } else {
                                RevFeatureType featureType = command(RevObjectParse.class)
                                        .setObjectId(mergeIntoDiff.getNewObject().getMetadataId())
                                        .call(RevFeatureType.class).get();
                                FeatureInfo merged = new FeatureInfo(mergedFeature, featureType,
                                        path);
                                consumer.merged(merged);
                                report.addMerged();
                            }
                        }
                    }
                }
                break;
            }

        }
    }
}
