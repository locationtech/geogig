/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import java.util.Iterator;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.DiffTree;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.WriteTree2;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.api.plumbing.merge.ReportCommitConflictsOp;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

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
    protected  RevCommit _call() {
        final Repository repository = repository();
        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions
                .checkState(currHead.isPresent(), "Repository has no HEAD, can't cherry pick.");
        Preconditions.checkState(currHead.get() instanceof SymRef,
                "Can't cherry pick from detached HEAD");
        final SymRef headRef = (SymRef) currHead.get();

        Preconditions.checkState(index().isClean() && workingTree().isClean(),
                "You must have a clean working tree and index to perform a cherry pick.");

        getProgressListener().started();

        Preconditions.checkArgument(repository.commitExists(commit),
                "Commit could not be resolved: %s.", commit);
        RevCommit commitToApply = repository.getCommit(commit);

        ObjectId headId = headRef.getObjectId();

        ObjectId parentCommitId = ObjectId.NULL;
        if (commitToApply.getParentIds().size() > 0) {
            parentCommitId = commitToApply.getParentIds().get(0);
        }
        ObjectId parentTreeId = ObjectId.NULL;
        if (repository.commitExists(parentCommitId)) {
            parentTreeId = repository.getCommit(parentCommitId).getTreeId();
        }
        // get changes
        Iterator<DiffEntry> diff = command(DiffTree.class).setOldTree(parentTreeId)
                .setNewTree(commitToApply.getTreeId()).setReportTrees(true).call();

        // see if there are conflicts
        MergeScenarioReport report = command(ReportCommitConflictsOp.class)
                .setCommit(commitToApply).call();
        if (report.getConflicts().isEmpty()) {
            // stage changes
            index().stage(getProgressListener(), diff, 0);
            // write new tree
            ObjectId newTreeId = command(WriteTree2.class).call();
            RevCommit newCommit = command(CommitOp.class).setCommit(commitToApply).call();

            repository.workingTree().updateWorkHead(newTreeId);
            repository.index().updateStageHead(newTreeId);

            getProgressListener().complete();

            return newCommit;
        } else {
            Iterator<DiffEntry> unconflicted = report.getUnconflicted().iterator();
            // stage changes
            index().stage(getProgressListener(), unconflicted, 0);
            workingTree().updateWorkHead(index().getTree().getId());

            command(UpdateRef.class).setName(Ref.CHERRY_PICK_HEAD).setNewValue(commit).call();
            command(UpdateRef.class).setName(Ref.ORIG_HEAD).setNewValue(headId).call();
            command(ConflictsWriteOp.class).setConflicts(report.getConflicts()).call();

            StringBuilder msg = new StringBuilder();
            msg.append("error: could not apply ");
            msg.append(commitToApply.getId().toString().substring(0, 7));
            msg.append(" " + commitToApply.getMessage());
            for (Conflict conflict : report.getConflicts()) {
                msg.append("\t" + conflict.getPath() + "\n");
            }

            StringBuilder sb = new StringBuilder();
            for (Conflict conflict : report.getConflicts()) {
                sb.append("CONFLICT: conflict in " + conflict.getPath() + "\n");
            }
            sb.append("Fix conflicts and then commit the result using 'geogig commit -c "
                    + commitToApply.getId().toString().substring(0, 7) + "\n");
            throw new IllegalStateException(sb.toString());
        }
    }
}
