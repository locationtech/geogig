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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.WriteTree2;
import org.locationtech.geogig.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.plumbing.merge.MergeScenarioConsumer;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.ReportCommitConflictsOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

/**
 * 
 * Apply the changes introduced by an existing commit.
 * <p>
 * 
 */
public class CherryPickOp extends AbstractGeoGigOp<RevCommit> {

    private final static int BUFFER_SIZE = 1000;

    private ObjectId commit;

    /**
     * Sets the commit to replay commits onto.
     * 
     * @param onto a supplier for the commit id
     * @return {@code this}
     */
    public CherryPickOp setCommit(final Supplier<ObjectId> commit) {
        Preconditions.checkNotNull(commit);

        this.commit = commit.get();
        return this;
    }

    /**
     * Executes the cherry pick operation.
     * 
     * @return RevCommit the new commit with the changes from the cherry-picked commit
     */
    @Override
    protected RevCommit _call() {
        final Repository repository = repository();
        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(currHead.isPresent(),
                "Repository has no HEAD, can't cherry pick.");
        Preconditions.checkState(currHead.get() instanceof SymRef,
                "Can't cherry pick from detached HEAD");
        final SymRef headRef = (SymRef) currHead.get();

        Preconditions.checkState(stagingArea().isClean() && workingTree().isClean(),
                "You must have a clean working tree and index to perform a cherry pick.");

        getProgressListener().started();

        Preconditions.checkArgument(repository.commitExists(commit),
                "Commit could not be resolved: %s.", commit);
        RevCommit commitToApply = repository.getCommit(commit);

        ObjectId headId = headRef.getObjectId();

        // In case there are conflicts
        StringBuilder conflictMsg = new StringBuilder();
        final int maxReportedConflicts = 25;
        final AtomicInteger reportedConflicts = new AtomicInteger(0);

        final List<Conflict> conflictsBuffer = Lists.newArrayListWithCapacity(BUFFER_SIZE);
        final List<DiffEntry> diffEntryBuffer = Lists.newArrayListWithCapacity(BUFFER_SIZE);

        // see if there are conflicts
        MergeScenarioReport report = command(ReportCommitConflictsOp.class).setCommit(commitToApply)
                .setConsumer(new MergeScenarioConsumer() {

                    @Override
                    public void conflicted(Conflict conflict) {
                        conflictsBuffer.add(conflict);
                        if (conflictsBuffer.size() == BUFFER_SIZE) {
                            // Write the conflicts
                            command(ConflictsWriteOp.class).setConflicts(conflictsBuffer).call();
                            conflictsBuffer.clear();
                        }
                        if (reportedConflicts.get() < maxReportedConflicts) {
                            conflictMsg
                                    .append("CONFLICT: conflict in " + conflict.getPath() + "\n");
                            reportedConflicts.incrementAndGet();
                        }
                    }

                    @Override
                    public void unconflicted(DiffEntry diff) {
                        diffEntryBuffer.add(diff);
                        if (diffEntryBuffer.size() == BUFFER_SIZE) {
                            // Stage it
                            stagingArea().stage(getProgressListener(), diffEntryBuffer.iterator(), 0);
                            diffEntryBuffer.clear();
                        }

                    }

                    @Override
                    public void merged(FeatureInfo featureInfo) {
                        // Stage it
                        workingTree().insert(featureInfo);
                        try (AutoCloseableIterator<DiffEntry> unstaged = workingTree()
                                .getUnstaged(null)) {
                            stagingArea().stage(getProgressListener(), unstaged, 0);
                        }
                    }

                    @Override
                    public void finished() {
                        if (conflictsBuffer.size() > 0) {
                            // Write the conflicts
                            command(ConflictsWriteOp.class).setConflicts(conflictsBuffer).call();
                            conflictsBuffer.clear();
                        }
                        if (diffEntryBuffer.size() > 0) {
                            // Stage it
                            stagingArea().stage(getProgressListener(), diffEntryBuffer.iterator(), 0);
                            diffEntryBuffer.clear();
                        }
                    }

                }).call();

        if (report.getConflicts() == 0) {
            // write new tree
            ObjectId newTreeId = command(WriteTree2.class).call();
            RevCommit newCommit = command(CommitOp.class).setCommit(commitToApply).call();

            repository.workingTree().updateWorkHead(newTreeId);
            repository.index().updateStageHead(newTreeId);

            getProgressListener().complete();

            return newCommit;
        }

        // stage changes
        workingTree().updateWorkHead(stagingArea().getTree().getId());

        command(UpdateRef.class).setName(Ref.CHERRY_PICK_HEAD).setNewValue(commit).call();
        command(UpdateRef.class).setName(Ref.ORIG_HEAD).setNewValue(headId).call();
        if (report.getConflicts() > reportedConflicts.get()) {
            conflictMsg
                    .append("And " + Long.toString(report.getConflicts() - reportedConflicts.get())
                            + " additional conflicts..\n");
        }
        conflictMsg.append("Fix conflicts and then commit the result using 'geogig commit -c "
                + commitToApply.getId().toString().substring(0, 8) + "\n");
        throw new ConflictsException(conflictMsg.toString());

    }
}
