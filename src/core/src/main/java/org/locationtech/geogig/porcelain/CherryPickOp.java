/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static org.locationtech.geogig.base.Preconditions.checkState;

import java.util.Optional;
import java.util.function.Supplier;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveCommit;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.plumbing.WriteTree2;
import org.locationtech.geogig.plumbing.merge.ConflictsUtils;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.ReportCommitConflictsOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.impl.PersistedIterable;

import com.google.common.collect.Iterators;

import lombok.Cleanup;
import lombok.NonNull;

/**
 * 
 * Apply the changes introduced by an existing commit.
 * <p>
 * 
 */
public class CherryPickOp extends AbstractGeoGigOp<RevCommit> {

    private ObjectId commit;

    /**
     * Sets the commit to replay commits onto.
     * 
     * @param onto a supplier for the commit id
     * @return {@code this}
     */
    public CherryPickOp setCommit(final @NonNull Supplier<ObjectId> commit) {
        this.commit = commit.get();
        return this;
    }

    /**
     * Executes the cherry pick operation.
     * 
     * @return RevCommit the new commit with the changes from the cherry-picked commit
     */
    protected @Override RevCommit _call() {
        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        checkState(currHead.isPresent(), "Repository has no HEAD, can't cherry pick.");
        checkState(currHead.get() instanceof SymRef, "Can't cherry pick from detached HEAD");
        final SymRef headRef = (SymRef) currHead.get();

        checkState(stagingArea().isClean() && workingTree().isClean(),
                "You must have a clean working tree and index to perform a cherry pick.");

        getProgressListener().started();

        final RevCommit commitToApply = command(ResolveCommit.class).setCommitIsh(commit).call()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Commit could not be resolved: " + commit));

        ObjectId headId = headRef.getObjectId();

        final @Cleanup PersistedIterable<Conflict> conflicts = ConflictsUtils
                .newTemporaryConflictStream();
        final @Cleanup PersistedIterable<DiffEntry> unconflicted = ConflictsUtils
                .newTemporaryDiffEntryStream();
        final @Cleanup PersistedIterable<FeatureInfo> merged = ConflictsUtils
                .newTemporaryFeatureInfoStream();

        // see if there are conflicts
        MergeScenarioReport report = command(ReportCommitConflictsOp.class)//
                .setCommit(commitToApply)//
                .setOnConflict(conflicts::add)//
                .setOnFeatureMerged(merged::add)//
                .setOnUnconflictedChange(unconflicted::add)//
                .call();
        if (conflicts.size() > 0) {
            geogig().conflicts().save(conflicts);
        }
        if (unconflicted.size() > 0) {
            stagingArea().stage(getProgressListener(), unconflicted.iterator(),
                    unconflicted.size());
        }
        if (merged.size() > 0) {
            workingTree().insert(merged.iterator(), getProgressListener());
            try (AutoCloseableIterator<DiffEntry> unstaged = workingTree().getUnstaged(null)) {
                stagingArea().stage(getProgressListener(), unstaged, 0);
            }
        }
        if (report.getConflicts() == 0) {
            // write new tree
            ObjectId newTreeId = command(WriteTree2.class).call();
            RevCommit newCommit = command(CommitOp.class).setCommit(commitToApply).call();
            UpdateRefs cleanup = command(UpdateRefs.class).setReason("cherry-pick: no conflicts");
            if (!newTreeId.equals(workingTree().getTree().getId())) {
                cleanup.add(Ref.WORK_HEAD, newTreeId);
            }
            if (!newTreeId.equals(stagingArea().getTree().getId())) {
                cleanup.add(Ref.STAGE_HEAD, newTreeId);
            }
            cleanup.call();

            getProgressListener().complete();

            return newCommit;
        }

        // stage changes
        UpdateRefs updateRefs = command(UpdateRefs.class)
                .setReason("cherry-pick: set up conflicts");
        updateRefs.add(Ref.WORK_HEAD, stagingArea().getTree().getId());
        updateRefs.add(Ref.CHERRY_PICK_HEAD, commit);
        updateRefs.add(Ref.ORIG_HEAD, headId);
        updateRefs.call();

        final int maxReportedConflicts = 25;
        StringBuilder conflictMsg = new StringBuilder();
        Iterators.limit(conflicts.iterator(), maxReportedConflicts).forEachRemaining(c -> {
            conflictMsg.append("CONFLICT: conflict in ").append(c.getPath()).append('\n');
        });
        if (report.getConflicts() > maxReportedConflicts) {
            conflictMsg.append("And " + Long.toString(report.getConflicts() - maxReportedConflicts)
                    + " additional conflicts..\n");
        }
        conflictMsg.append("Fix conflicts and then commit the result using 'commit -c "
                + commitToApply.getId().toString().substring(0, 8) + "'\n");
        throw new ConflictsException(conflictMsg.toString());

    }
}
