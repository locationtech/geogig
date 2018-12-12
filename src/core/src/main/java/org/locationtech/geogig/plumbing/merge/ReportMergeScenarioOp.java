/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Iterator;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.merge.DiffMergeFeaturesOp.DiffMergeFeatureResult;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
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
        final Optional<ObjectId> ancestorOpt = command(FindCommonAncestor.class).setLeft(toMerge)
                .setRight(mergeInto).call();
        Preconditions.checkState(ancestorOpt.isPresent(), "No ancestor commit could be found.");

        final ObjectId ancestor = ancestorOpt.get();

        MergeScenarioReport report = null;

        try (AutoCloseableIterator<DiffEntry> mergeIntoDiffs = command(DiffTree.class)
                .setOldTree(ancestor).setReportTrees(true).setNewTree(mergeInto.getId())
                .setPreserveIterationOrder(true).call();
                AutoCloseableIterator<DiffEntry> toMergeDiffs = command(DiffTree.class)
                        .setOldTree(ancestor).setReportTrees(true).setNewTree(toMerge.getId())
                        .setPreserveIterationOrder(true).call();) {

            Iterator<MergeDiffRef> tupleIterator = new MergeDiffIterator(mergeIntoDiffs,
                    toMergeDiffs);

            final RevCommit ancestorCommit = objectDatabase().getCommit(ancestor);
            final RevTree ancestorTree = objectDatabase().getTree(ancestorCommit.getTreeId());

            report = process(tupleIterator, ancestorTree);
        }

        return report;

    }

    private static class MergeDiffIterator extends AbstractIterator<MergeDiffRef> {

        private PeekingIterator<DiffEntry> ours;

        private PeekingIterator<DiffEntry> theirs;

        MergeDiffIterator(AutoCloseableIterator<DiffEntry> ours,
                AutoCloseableIterator<DiffEntry> theirs) {
            this.ours = Iterators.peekingIterator(ours);
            this.theirs = Iterators.peekingIterator(theirs);
        }

        @Override
        protected MergeDiffRef computeNext() {
            DiffEntry left = ours.hasNext() ? ours.peek() : null;
            DiffEntry right = theirs.hasNext() ? theirs.peek() : null;

            if (left == null && right == null) {
                return endOfData();
            }
            if (right == null) {
                // no more their's diffs to process
                return endOfData();
            }

            if (left != null && right != null) {
                final int compare = DiffEntry.COMPARATOR.compare(left, right);
                if (compare < 0) {
                    // Only "our" branch modified the path, advance "our" iterator
                    ours.next();
                    right = null;
                } else if (compare > 0) {
                    // Only "their" branch modified the path, advance "their" iterator
                    theirs.next();
                    left = null;
                } else {
                    // Same path modified on both, advance both iterators
                    ours.next();
                    theirs.next();
                }
            } else if (left == null) {
                theirs.next();
            } else {
                ours.next();
            }

            return new MergeDiffRef(left, right);
        }

    }

    private static class MergeDiffRef {

        private final DiffEntry ours;

        private final DiffEntry theirs;

        public MergeDiffRef(@Nullable DiffEntry ours, @Nullable DiffEntry theirs) {
            checkArgument(!(ours == null && theirs == null));
            this.ours = ours;
            this.theirs = theirs;
        }

        public DiffEntry ours() {
            return ours;
        }

        public DiffEntry theirs() {
            return theirs;
        }
    }

    private MergeScenarioReport process(Iterator<MergeDiffRef> tupleIterator,
            RevTree ancestorTree) {

        MergeScenarioReport report = new MergeScenarioReport();
        while (tupleIterator.hasNext()) {
            MergeDiffRef mr = tupleIterator.next();

            DiffEntry ours = mr.ours();
            DiffEntry theirs = mr.theirs();
            if (ours == null) {
                // Only "their" branch modified the path
                consumer.unconflicted(theirs);
                report.addUnconflicted(theirs);
            } else if (theirs == null) {
                // Only "our" branch modified the path
                // nothing else to do
            } else {
                // both branches modifies the same path
                processPossibleConflict(ours, theirs, ancestorTree, report);
            }

            if (consumer.isCancelled()) {
                break;
            }
        }

        if (!consumer.isCancelled() || !tupleIterator.hasNext()) {
            consumer.finished();
        }

        return report;

    }

    private void processPossibleConflict(DiffEntry oursDiff, DiffEntry theirsDiff,
            RevTree ancestorTree, MergeScenarioReport report) {

        Preconditions.checkArgument(oursDiff.oldObject().equals(theirsDiff.oldObject()));

        final String path = oursDiff.path();

        final Optional<NodeRef> ancestorVersion = oursDiff.oldObject();

        final ObjectId ancestorVersionId = theirsDiff.oldObjectId();
        final ObjectId ours = oursDiff.newObjectId();
        final ObjectId theirs = theirsDiff.newObjectId();

        if (!oursDiff.changeType().equals(theirsDiff.changeType())) {
            consumer.conflicted(new Conflict(path, ancestorVersionId, ours, theirs));
            report.addConflict(path);
            return;
        }
        switch (theirsDiff.changeType()) {
        case ADDED:
            if (theirsDiff.getNewObject().equals(oursDiff.getNewObject())) {
                // already added in current branch, no need to do anything
            } else {
                if (TYPE.TREE == theirsDiff.newObjectType()) {
                    checkForFeatureTypeConflict(ancestorVersion, oursDiff, theirsDiff, report);
                } else {
                    consumer.conflicted(new Conflict(path, ancestorVersionId, ours, theirs));
                    report.addConflict(path);
                }
            }
            break;
        case REMOVED:
            // removed by both histories => no conflict and no need to do anything
            break;
        case MODIFIED:
            if (TYPE.TREE == theirsDiff.newObjectType()) {
                checkForFeatureTypeConflict(ancestorVersion, oursDiff, theirsDiff, report);
                break;
            }

            DiffMergeFeatureResult diffMergeFeatureResult = command(DiffMergeFeaturesOp.class)//
                    .setCommonAncestor(theirsDiff.getOldObject())//
                    .setMergeInto(oursDiff.getNewObject())//
                    .setToMerge(theirsDiff.getNewObject())//
                    .call();

            if (diffMergeFeatureResult.isConflict()) {
                consumer.conflicted(new Conflict(path, ancestorVersionId, ours, theirs));
                report.addConflict(path);
            } else if (diffMergeFeatureResult.isMerge()) {
                RevFeature mergedFeature = diffMergeFeatureResult.mergedFeature();
                if (mergedFeature.getId().equals(theirsDiff.newObjectId())) {
                    // the resulting merged feature equals the feature to merge from
                    // the branch, which means that it exists in the merge into tree and there
                    // is no need to report the to merge change as merged.
                    consumer.unconflicted(theirsDiff);
                    report.addUnconflicted(theirsDiff);
                } else {
                    ObjectId featureTypeId = oursDiff.getNewObject().getMetadataId();
                    FeatureInfo merged = FeatureInfo.insert(mergedFeature, featureTypeId, path);
                    consumer.merged(merged);
                    report.addMerged(path);
                }
            }
            // else do nothing, 'ours' has changed in the same way as 'theirs'
        }
    }

    private void checkForFeatureTypeConflict(Optional<NodeRef> ancestorVersion, DiffEntry oursDiff,
            DiffEntry theirsDiff, MergeScenarioReport report) {

        final String path = oursDiff.path();

        final boolean featureTypeConflict = !theirsDiff.getNewObject().getMetadataId()
                .equals(oursDiff.getNewObject().getMetadataId());
        if (featureTypeConflict) {
            // In this case, we store the metadata id, not the element id
            ObjectId ancestorVersionId = ancestorVersion.isPresent()
                    ? ancestorVersion.get().getMetadataId()
                    : ObjectId.NULL;
            ObjectId ours = oursDiff.getNewObject().getMetadataId();
            ObjectId theirs = theirsDiff.getNewObject().getMetadataId();
            consumer.conflicted(new Conflict(path, ancestorVersionId, ours, theirs));
            report.addConflict(path);
        }
    }
}
