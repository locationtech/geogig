/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static org.locationtech.geogig.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.dsl.Blobs;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.plumbing.WriteTree2;
import org.locationtech.geogig.plumbing.merge.ConflictsUtils;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.ReportCommitConflictsOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.impl.PersistedIterable;

import com.google.common.collect.Iterators;

import lombok.Cleanup;

/**
 * 
 * Rebase the current head to the included branch head.
 * 
 * Rebasing is done following these steps:
 * 
 * -Commits to apply are computed and for each one a blob is created in the {@code rebase-apply/}
 * {@link BlobStore} path, containing the ID of the commit. Blobs have correlative names starting on
 * 1, indicating the order in which they should be applied
 * 
 * -HEAD is rewinded to starting point
 * 
 * -Commits are applied. For each commit applied, the corresponding blob is deleted
 * 
 * -A blob named 'next' keeps track of the next commit to apply between executions of the rebase
 * command, in case of conflicts
 * 
 * - A blob named 'branch' keeps track of the current branch name
 * 
 * 
 * 
 */
@CanRunDuringConflict
@Hookable(name = "rebase")
public class RebaseOp extends AbstractGeoGigOp<Boolean> {

    private static final String REBASE_BLOB_PREFIX = "rebase-apply/";

    public static final String REBASE_NEXT_BLOB = REBASE_BLOB_PREFIX + "next";

    public static final String REBASE_BRANCH_BLOB = REBASE_BLOB_PREFIX + "branch";

    public static final String REBASE_SQUASH_COMMIT_ID_BLOB = REBASE_BLOB_PREFIX + "squash";

    private Supplier<ObjectId> upstream;

    private Supplier<ObjectId> onto;

    private boolean skip;

    private boolean continueRebase;

    private boolean abort;

    private String squashMessage;

    /// non parameter state
    private String currentBranch;

    private ObjectId rebaseHead;

    /**
     * Sets the commit to replay commits onto.
     * 
     * @param onto a supplier for the commit id
     * @return {@code this}
     */
    public RebaseOp setOnto(final Supplier<ObjectId> onto) {
        this.onto = onto;
        return this;
    }

    /**
     * Sets whether to abort the current rebase operation
     * 
     * @param abort
     * @return
     */
    public RebaseOp setAbort(boolean abort) {
        this.abort = abort;
        return this;
    }

    /**
     * Sets the message to use to squash commits. If no message is provided, no squash is performed,
     * so this parameter acts also as a flag
     * 
     * @param squash the squash message
     * @return
     */
    public RebaseOp setSquashMessage(String squashMessage) {
        this.squashMessage = squashMessage;
        return this;
    }

    /**
     * Sets whether to continue a rebase operation aborted due to conflicts
     * 
     * @param continueRebase
     * @return {@code this}
     */
    public RebaseOp setContinue(boolean continueRebase) {
        this.continueRebase = continueRebase;
        return this;
    }

    /**
     * Sets whether to skip the current commit, which cause the rebase operation to be aborted
     * 
     * @param skip
     * @return {@code this}
     */
    public RebaseOp setSkip(boolean skip) {
        this.skip = skip;
        return this;
    }

    /**
     * Sets the upstream commit. This is used in finding the common ancestor.
     * 
     * @param upstream a supplier for the upstream commit
     * @return {@code this}
     */
    public RebaseOp setUpstream(final Supplier<ObjectId> upstream) {
        this.upstream = upstream;
        return this;
    }

    /**
     * Executes the rebase operation.
     * 
     * @return always {@code true}
     */
    protected @Override Boolean _call() {
        final Geogig geogig = geogig();

        final Ref currHead = geogig.refs().head().orElseThrow(
                () -> new IllegalStateException("Repository has no HEAD, can't rebase."));

        final boolean continueRebase = this.continueRebase;
        final boolean skip = this.skip;
        final boolean abort = this.abort;
        final Supplier<ObjectId> upstream = this.upstream;

        final ObjectId targetCommitId = upstream == null ? ObjectId.NULL : upstream.get();
        if (!(continueRebase || skip || abort)) {
            checkState(currHead instanceof SymRef, "Can't rebase from detached HEAD %s", currHead);
            checkState(upstream != null, "No upstream target has been specified.");
            checkState(!targetCommitId.isNull(), "Upstream did not resolve to a commit.");
        }

        if (abort) {
            return abortRebase();
        }
        if (continueRebase) {
            continueRebase(currHead);
        } else if (skip) {
            skip(currHead);
        } else {
            checkState(!geogig.conflicts().hasConflicts(),
                    "Cannot run operation while merge conflicts exist.");
            checkState(!geogig.refs().find(Ref.ORIG_HEAD).isPresent(),
                    "You are currently in the middle of a merge or rebase project <ORIG_HEAD is present>.");

            getProgressListener().started();
            final ObjectId headCommitId = currHead.getObjectId();
            currentBranch = currHead.peel().getName();
            geogig.refs().set(Ref.ORIG_HEAD, headCommitId, "rebase: savepoint");

            // prepare the files with the info about the commits to apply or, if that's not
            // needed, do a fast-forward
            final ObjectId ancestorCommitId = headCommitId.isNull() ? headCommitId : //
                    geogig.commands().commonAncestor(headCommitId, targetCommitId).orElseThrow(
                            () -> new IllegalStateException("No ancestor commit could be found."));

            if (ancestorCommitId.isNull() || ancestorCommitId.equals(headCommitId)) {
                // Fast-forward
                String reason = String.format("rebase: fast forward to %s", targetCommitId);
                Ref updatedBranch = new Ref(currentBranch, targetCommitId);
                command(UpdateRefs.class).setReason(reason)//
                        .add(updatedBranch)//
                        .add(new SymRef(Ref.HEAD, updatedBranch))//
                        .add(Ref.WORK_HEAD, targetCommitId)//
                        .add(Ref.STAGE_HEAD, targetCommitId)//
                        .call();
                getProgressListener().complete();
                return true;
            }

            // Get all commits between the head commit and the ancestor.
            Iterator<RevCommit> commitIterator = geogig.commands().logCall().iterator();

            List<RevCommit> commitsToRebase = new ArrayList<RevCommit>();
            RevCommit commit = commitIterator.next();
            while (!commit.getId().equals(ancestorCommitId)) {
                commitsToRebase.add(commit);
                commit = commitIterator.next();
            }

            // rewind the HEAD
            rebaseHead = onto == null ? targetCommitId : onto.get();
            geogig.commands().reset(rebaseHead, ResetMode.HARD, false);

            if (squashMessage == null) {
                createRebaseCommitsInfo(commitsToRebase);
            } else {
                RevCommitBuilder builder = RevCommit.builder().platform(this.platform())
                        .init(commitsToRebase.get(0));
                builder.parentIds(Arrays.asList(ancestorCommitId));
                builder.message(squashMessage);
                RevCommit squashCommit = builder.build();
                objectDatabase().put(squashCommit);
                // save the commit, since it does not exist in the database, and might be needed if
                // there is a conflict
                geogig.blobs().put(REBASE_SQUASH_COMMIT_ID_BLOB, squashCommit.getId().toString());
                applyCommit(squashCommit);
                return true;
            }
        }

        RevCommit squashCommit = readSquashCommit();
        if (squashCommit == null) {
            boolean ret;
            do {
                ret = applyNextCommit(true);
            } while (ret);
        }

        // clean up
        geogig.blobs().remove(REBASE_SQUASH_COMMIT_ID_BLOB);
        geogig.blobs().remove(REBASE_BRANCH_BLOB);
        geogig.refs().remove(Ref.ORIG_HEAD, "rebase: cleanup");

        getProgressListener().complete();

        return true;

    }

    private void skip(final Ref currHead) {
        final Geogig geogig = geogig();
        geogig.refs().find(Ref.ORIG_HEAD).orElseThrow(() -> new IllegalStateException(
                "Cannot skip. You are not in the middle of a rebase process."));

        currentBranch = geogig.blobs().asString(REBASE_BRANCH_BLOB)
                .orElseThrow(() -> new IllegalStateException("Cannot read current branch info"));

        rebaseHead = currHead.getObjectId();
        geogig.commands().reset(rebaseHead, ResetMode.HARD, false);
        RevCommit squashCommit = readSquashCommit();
        if (squashCommit == null) {
            skipCurrentCommit();
        }
    }

    private void continueRebase(final Ref currHead) {
        final Geogig geogig = geogig();
        checkState(!geogig.conflicts().hasConflicts(),
                "Cannot run operation while merge conflicts exist.");

        final String branch = geogig.blobs().asString(REBASE_BRANCH_BLOB)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot continue. You are not in the middle of a rebase process (no rebase branch saved)."));

        geogig.refs().find(Ref.ORIG_HEAD).orElseThrow(() -> new IllegalStateException(
                "Cannot continue. You are not in the middle of a rebase process (ref ORIG_HEAD not found)."));

        currentBranch = branch;
        rebaseHead = currHead.getObjectId();
        RevCommit squashCommit = readSquashCommit();
        if (squashCommit == null) {
            // Commit the manually-merged changes with the info of the commit that caused the
            // conflict
            applyNextCommit(false);
            // Commit files should already be prepared, so we do nothing else
        } else {
            commitWithInfoFrom(squashCommit);
        }
    }

    private Boolean abortRebase() {
        final Geogig geogig = geogig();
        final String msg = "Cannot abort. You are not in the middle of a rebase process.";
        checkState(geogig.blobs().exists(REBASE_BRANCH_BLOB), msg);

        final Ref origHead = geogig.refs().find(Ref.ORIG_HEAD)
                .orElseThrow(() -> new IllegalStateException(msg));

        geogig.commands().reset(origHead.getObjectId(), ResetMode.HARD, true);
        geogig.refs().remove(Ref.ORIG_HEAD, "rebase: cleanup");
        return true;
    }

    private void skipCurrentCommit() {
        Blobs blobs = geogig().blobs();
        try {
            int idx = Integer.parseInt(blobs.asString(REBASE_NEXT_BLOB).get());
            String blobName = REBASE_BLOB_PREFIX + idx;
            blobs.remove(blobName);
            int newIdx = idx + 1;
            blobs.put(REBASE_NEXT_BLOB, String.valueOf(newIdx));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read/write rebase commits index", e);
        }

    }

    private void createRebaseCommitsInfo(List<RevCommit> commitsToRebase) {
        org.locationtech.geogig.dsl.Blobs blobs = geogig().blobs();
        try {
            for (int i = commitsToRebase.size() - 1, idx = 1; i >= 0; i--, idx++) {
                String blobName = REBASE_BLOB_PREFIX + idx;
                String contents = commitsToRebase.get(i).getId().toString();
                blobs.put(blobName, contents);
            }
            blobs.put(REBASE_NEXT_BLOB, "1");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create next rebase commit info");
        }

    }

    private boolean applyNextCommit(boolean useCommitChanges) {
        org.locationtech.geogig.dsl.Blobs blobs = geogig().blobs();
        Integer idx = blobs.asString(REBASE_NEXT_BLOB).map(Integer::parseInt).orElse(null);
        if (idx == null) {
            return false;
        }
        String blobName = REBASE_BLOB_PREFIX + idx;
        Optional<RevCommit> commit = blobs.asString(blobName).map(ObjectId::valueOf)
                .map(objectDatabase()::getCommit);
        if (!commit.isPresent()) {
            return false;
        }
        if (useCommitChanges) {
            applyCommit(commit.get());
        } else {
            commitWithInfoFrom(commit.get());
        }
        blobs.remove(blobName);
        try {
            blobs.put(REBASE_NEXT_BLOB, String.valueOf(idx + 1));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read/write rebase commits index", e);
        }
        return true;
    }

    /**
     * Applies the changed of the passed commit.
     * 
     * @param commitToApply the commit to apply
     */
    private void applyCommit(RevCommit commitToApply) {
        Geogig geogig = geogig();

        final @Cleanup PersistedIterable<Conflict> conflicts = ConflictsUtils
                .newTemporaryConflictStream();
        final @Cleanup PersistedIterable<DiffEntry> unconflicted = ConflictsUtils
                .newTemporaryDiffEntryStream();
        final @Cleanup PersistedIterable<FeatureInfo> merged = ConflictsUtils
                .newTemporaryFeatureInfoStream();

        // see if there are conflicts
        final MergeScenarioReport report = command(ReportCommitConflictsOp.class)
                .setCommit(commitToApply)//
                .setOnConflict(conflicts::add)//
                .setOnFeatureMerged(merged::add)//
                .setOnUnconflictedChange(unconflicted::add)//
                .call();

        if (conflicts.size() > 0) {
            geogig.conflicts().save(conflicts);
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
            commitWithInfoFrom(commitToApply);
        } else {
            workingTree().updateWorkHead(stagingArea().getTree().getId(),
                    "rebase: match STAGE_HEAD");
            geogig.blobs().put(REBASE_BRANCH_BLOB, currentBranch);

            StringBuilder conflictMsg = new StringBuilder();
            conflictMsg.append("error: could not apply ")
                    .append(RevObjects.toShortString(commitToApply.getId())).append(" ")
                    .append(commitToApply.getMessage()).append('\n');
            final int maxReportedConflicts = 25;
            Iterators.limit(conflicts.iterator(), maxReportedConflicts).forEachRemaining(c -> {
                conflictMsg.append("CONFLICT: conflict in ").append(c.getPath()).append('\n');
            });

            throw new RebaseConflictsException(conflictMsg.toString());
        }
    }

    private void commitWithInfoFrom(RevCommit commitToApply) {
        // write new tree
        ObjectId newTreeId = command(WriteTree2.class).call();

        long timestamp = platform().currentTimeMillis();
        // Create new commit
        RevCommitBuilder builder = RevCommit.builder().platform(this.platform())
                .init(commitToApply);
        builder.parentIds(Arrays.asList(rebaseHead));
        builder.treeId(newTreeId);
        builder.committerTimestamp(timestamp);
        builder.committerTimeZoneOffset(platform().timeZoneOffset(timestamp));

        RevCommit newCommit = builder.build();
        objectDatabase().put(newCommit);

        rebaseHead = newCommit.getId();
        final Ref updatedBranch = new Ref(currentBranch, rebaseHead);
        final String reason = String.format("rebase: commit reusing %s '%s'",
                RevObjects.toShortString(commitToApply.getId()),
                RevObjects.messageTitle(commitToApply.getMessage()));
        command(UpdateRefs.class).setReason(reason)//
                .add(updatedBranch)//
                .add(new SymRef(Ref.HEAD, updatedBranch))//
                .add(Ref.WORK_HEAD, newTreeId)//
                .add(Ref.STAGE_HEAD, newTreeId)//
                .call();

    }

    /**
     * Return the commit that is the squashed version of all the commits to apply, reading it from
     * the 'squash' file. If the file does not exist (that is, we are not in the middle of a rebase
     * with squash operation), returns null
     * 
     * @return
     */
    @Nullable
    private RevCommit readSquashCommit() {
        Optional<ObjectId> commitId = geogig().blobs().asString(REBASE_SQUASH_COMMIT_ID_BLOB)
                .map(ObjectId::valueOf);
        if (!commitId.isPresent()) {
            return null;
        }
        RevCommit revCommit = objectDatabase().getCommit(commitId.get());
        return revCommit;
    }
}
