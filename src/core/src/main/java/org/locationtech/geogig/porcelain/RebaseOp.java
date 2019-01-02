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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.CatObject;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.plumbing.WriteTree2;
import org.locationtech.geogig.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.plumbing.merge.MergeScenarioConsumer;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.ReportCommitConflictsOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.impl.Blobs;
import org.locationtech.geogig.storage.text.TextRevObjectSerializer;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;

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

    public static final String REBASE_SQUASH_BLOB = REBASE_BLOB_PREFIX + "squash";

    private final static int BUFFER_SIZE = 1000;

    private Supplier<ObjectId> upstream;

    private Supplier<ObjectId> onto;

    private boolean skip;

    private boolean continueRebase;

    private String currentBranch;

    private ObjectId rebaseHead;

    private boolean abort;

    private String squashMessage;

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
    @Override
    protected Boolean _call() {

        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(currHead.isPresent(), "Repository has no HEAD, can't rebase.");

        if (!(continueRebase || skip || abort)) {
            Preconditions.checkState(currHead.get() instanceof SymRef,
                    "Can't rebase from detached HEAD %s", currHead.get());
            Preconditions.checkState(upstream != null, "No upstream target has been specified.");
            Preconditions.checkState(!ObjectId.NULL.equals(upstream.get()),
                    "Upstream did not resolve to a commit.");
        }

        // Rebase can only be run in a conflicted situation if the skip or abort option is used
        final boolean hasConflicts = conflictsDatabase().hasConflicts(null);
        Preconditions.checkState(!hasConflicts || skip || abort,
                "Cannot run operation while merge conflicts exist.");

        Optional<Ref> origHead = command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        final Optional<byte[]> branch = Blobs.getBlob(context().blobStore(), REBASE_BRANCH_BLOB);
        RevCommit squashCommit = readSquashCommit();
        if (abort) {
            Preconditions.checkState(origHead.isPresent() && branch.isPresent(),
                    "Cannot abort. You are not in the middle of a rebase process.");
            command(ResetOp.class).setMode(ResetMode.HARD)
                    .setCommit(Suppliers.ofInstance(origHead.get().getObjectId())).call();
            command(UpdateRef.class).setDelete(true).setName(Ref.ORIG_HEAD).call();
            return true;
        } else if (continueRebase) {
            Preconditions.checkState(origHead.isPresent() && branch.isPresent(),
                    "Cannot continue. You are not in the middle of a rebase process.");
            try {
                List<String> branchLines = Blobs.readLines(branch);
                currentBranch = branchLines.get(0);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read current branch info", e);
            }
            rebaseHead = currHead.get().getObjectId();
            if (squashCommit == null) {
                // Commit the manually-merged changes with the info of the commit that caused the
                // conflict
                applyNextCommit(false);
                // Commit files should already be prepared, so we do nothing else
            } else {
                applyCommit(squashCommit, false);
            }
        } else if (skip) {
            Preconditions.checkState(origHead.isPresent() && branch.isPresent(),
                    "Cannot skip. You are not in the middle of a rebase process.");
            try {
                List<String> branchLines = Blobs.readLines(branch);
                currentBranch = branchLines.get(0);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read current branch info");
            }
            rebaseHead = currHead.get().getObjectId();
            command(ResetOp.class).setCommit(Suppliers.ofInstance(rebaseHead))
                    .setMode(ResetMode.HARD).setClean(false).call();
            if (squashCommit == null) {
                skipCurrentCommit();
                applyNextCommit(true);
            } else {
                return true;
            }
        } else {
            Preconditions.checkState(!origHead.isPresent(),
                    "You are currently in the middle of a merge or rebase project <ORIG_HEAD is present>.");

            getProgressListener().started();

            command(UpdateRef.class).setName(Ref.ORIG_HEAD)
                    .setNewValue(currHead.get().getObjectId()).call();

            // Here we prepare the files with the info about the commits to apply or, if that's not
            // needed, do a fast-forward

            final SymRef headRef = (SymRef) currHead.get();
            currentBranch = headRef.getTarget();

            if (ObjectId.NULL.equals(headRef.getObjectId())) {
                // Fast-forward
                command(UpdateRef.class).setName(currentBranch).setNewValue(upstream.get()).call();
                command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();
                workingTree().updateWorkHead(upstream.get());
                stagingArea().updateStageHead(upstream.get());
                getProgressListener().complete();
                return true;
            }

            Repository repository = repository();
            final RevCommit headCommit = repository.getCommit(headRef.getObjectId());
            final RevCommit targetCommit = repository.getCommit(upstream.get());

            command(UpdateRef.class).setName(Ref.ORIG_HEAD).setNewValue(headCommit.getId());

            Optional<ObjectId> ancestorCommit = command(FindCommonAncestor.class)
                    .setLeft(headCommit).setRight(targetCommit)
                    .setProgressListener(subProgress(10.f)).call();

            Preconditions.checkState(ancestorCommit.isPresent(),
                    "No ancestor commit could be found.");

            if (ancestorCommit.get().equals(headCommit.getId())) {
                // Fast-forward
                command(UpdateRef.class).setName(currentBranch).setNewValue(upstream.get()).call();
                command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

                workingTree().updateWorkHead(upstream.get());
                stagingArea().updateStageHead(upstream.get());
                getProgressListener().complete();
                return true;
            }

            // Get all commits between the head commit and the ancestor.
            Iterator<RevCommit> commitIterator = command(LogOp.class).call();

            List<RevCommit> commitsToRebase = new ArrayList<RevCommit>();

            RevCommit commit = commitIterator.next();
            while (!commit.getId().equals(ancestorCommit.get())) {
                commitsToRebase.add(commit);
                commit = commitIterator.next();
            }

            // rewind the HEAD
            if (onto == null) {
                onto = Suppliers.ofInstance(upstream.get());
            }
            rebaseHead = onto.get();
            command(ResetOp.class).setCommit(Suppliers.ofInstance(rebaseHead))
                    .setMode(ResetMode.HARD).setClean(false).call();

            if (squashMessage != null) {
                RevCommitBuilder builder = RevCommit.builder().init(commitsToRebase.get(0));
                builder.parentIds(Arrays.asList(ancestorCommit.get()));
                builder.message(squashMessage);
                squashCommit = builder.build();
                // save the commit, since it does not exist in the database, and might be needed if
                // there is a conflict
                CharSequence commitString = command(CatObject.class)
                        .setObject(Suppliers.ofInstance(squashCommit)).call();
                try {
                    Blobs.putBlob(context().blobStore(), REBASE_SQUASH_BLOB, commitString);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot create squash commit info blob", e);
                }
                applyCommit(squashCommit, true);
                return true;
            } else {
                createRebaseCommitsInfo(commitsToRebase);
            }

            // ProgressListener subProgress = subProgress(90.f);
        }

        if (squashCommit == null) {
            boolean ret;
            do {
                ret = applyNextCommit(true);
            } while (ret);
        }

        // clean up
        context().blobStore().removeBlob(REBASE_SQUASH_BLOB);
        command(UpdateRef.class).setDelete(true).setName(Ref.ORIG_HEAD).call();
        context().blobStore().removeBlob(REBASE_BRANCH_BLOB);

        // subProgress.complete();

        getProgressListener().complete();

        return true;

    }

    private void skipCurrentCommit() {
        List<String> nextFile = Blobs.readLines(context().blobStore(), REBASE_NEXT_BLOB);
        try {
            String idx = nextFile.get(0);
            String blobName = REBASE_BLOB_PREFIX + idx;
            context().blobStore().removeBlob(blobName);
            int newIdx = Integer.parseInt(idx) + 1;
            Blobs.putBlob(context().blobStore(), REBASE_NEXT_BLOB, String.valueOf(newIdx));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read/write rebase commits index", e);
        }

    }

    private void createRebaseCommitsInfo(List<RevCommit> commitsToRebase) {

        for (int i = commitsToRebase.size() - 1, idx = 1; i >= 0; i--, idx++) {
            String blobName = REBASE_BLOB_PREFIX + Integer.toString(idx);
            try {
                String contents = commitsToRebase.get(i).getId().toString();
                Blobs.putBlob(context().blobStore(), blobName, contents);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create rebase commits info");
            }
        }
        try {
            Blobs.putBlob(context().blobStore(), REBASE_NEXT_BLOB, "1");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create next rebase commit info");
        }

    }

    private boolean applyNextCommit(boolean useCommitChanges) {
        List<String> nextFile = Blobs.readLines(context().blobStore(), REBASE_NEXT_BLOB);
        if (nextFile.isEmpty()) {
            return false;
        }
        String idx = nextFile.get(0);
        String blobName = REBASE_BLOB_PREFIX + idx;
        List<String> commitFile = Blobs.readLines(context().blobStore(), blobName);
        if (commitFile.isEmpty()) {
            return false;
        }
        String commitId = commitFile.get(0);
        RevCommit commit = objectDatabase().getCommit(ObjectId.valueOf(commitId));
        applyCommit(commit, useCommitChanges);
        context().blobStore().removeBlob(blobName);
        int newIdx = Integer.parseInt(idx) + 1;
        try {
            Blobs.putBlob(context().blobStore(), REBASE_NEXT_BLOB, String.valueOf(newIdx));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read/write rebase commits index", e);
        }
        return true;
    }

    /**
     * Applies the passed command.
     * 
     * @param commitToApply the commit to apply
     * @param useCommitChanges if true, applies the command completely, staging its changes before
     *        committing. If false, it commits the currently staged changes, ignoring the changes in
     *        the commit and using just its author and message
     */
    private void applyCommit(RevCommit commitToApply, boolean useCommitChanges) {

        Repository repository = repository();
        Platform platform = platform();
        if (useCommitChanges) {
            // In case there are conflicts
            StringBuilder conflictMsg = new StringBuilder();
            conflictMsg.append("error: could not apply ");
            conflictMsg.append(commitToApply.getId().toString().substring(0, 8));
            conflictMsg.append(" " + commitToApply.getMessage() + "\n");
            final int maxReportedConflicts = 25;
            final AtomicInteger reportedConflicts = new AtomicInteger(0);

            final List<Conflict> conflictsBuffer = Lists.newArrayListWithCapacity(BUFFER_SIZE);
            final List<DiffEntry> diffEntryBuffer = Lists.newArrayListWithCapacity(BUFFER_SIZE);

            // see if there are conflicts
            MergeScenarioReport report = command(ReportCommitConflictsOp.class)
                    .setCommit(commitToApply).setConsumer(new MergeScenarioConsumer() {

                        @Override
                        public void conflicted(Conflict conflict) {
                            conflictsBuffer.add(conflict);
                            if (conflictsBuffer.size() == BUFFER_SIZE) {
                                // Write the conflicts
                                command(ConflictsWriteOp.class).setConflicts(conflictsBuffer)
                                        .call();
                                conflictsBuffer.clear();
                            }
                            if (reportedConflicts.get() < maxReportedConflicts) {
                                conflictMsg.append(
                                        "CONFLICT: conflict in " + conflict.getPath() + "\n");
                                reportedConflicts.incrementAndGet();
                            }
                        }

                        @Override
                        public void unconflicted(DiffEntry diff) {
                            diffEntryBuffer.add(diff);
                            if (diffEntryBuffer.size() == BUFFER_SIZE) {
                                // Stage it
                                stagingArea().stage(getProgressListener(),
                                        diffEntryBuffer.iterator(), 0);
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
                                command(ConflictsWriteOp.class).setConflicts(conflictsBuffer)
                                        .call();
                                conflictsBuffer.clear();
                            }
                            if (diffEntryBuffer.size() > 0) {
                                // Stage it
                                stagingArea().stage(getProgressListener(),
                                        diffEntryBuffer.iterator(), 0);
                                diffEntryBuffer.clear();
                            }
                        }

                    }).call();
            if (report.getConflicts() == 0) {
                // write new tree
                ObjectId newTreeId = command(WriteTree2.class).call();

                long timestamp = platform.currentTimeMillis();
                // Create new commit
                RevCommitBuilder builder = RevCommit.builder().init(commitToApply);
                builder.parentIds(Arrays.asList(rebaseHead));
                builder.treeId(newTreeId);
                builder.committerTimestamp(timestamp);
                builder.committerTimeZoneOffset(platform.timeZoneOffset(timestamp));

                RevCommit newCommit = builder.build();
                repository.objectDatabase().put(newCommit);

                rebaseHead = newCommit.getId();

                command(UpdateRef.class).setName(currentBranch).setNewValue(rebaseHead).call();
                command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

                workingTree().updateWorkHead(newTreeId);
                stagingArea().updateStageHead(newTreeId);

            } else {

                workingTree().updateWorkHead(stagingArea().getTree().getId());

                try {
                    Blobs.putBlob(context().blobStore(), REBASE_BRANCH_BLOB, currentBranch);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot create current branch info", e);
                }

                throw new RebaseConflictsException(conflictMsg.toString());

            }
        } else {
            // write new tree
            ObjectId newTreeId = command(WriteTree2.class).call();

            long timestamp = platform.currentTimeMillis();
            // Create new commit
            RevCommitBuilder builder = RevCommit.builder().init(commitToApply);
            builder.parentIds(Arrays.asList(rebaseHead));
            builder.treeId(newTreeId);
            builder.committerTimestamp(timestamp);
            builder.committerTimeZoneOffset(platform.timeZoneOffset(timestamp));

            RevCommit newCommit = builder.build();
            repository.objectDatabase().put(newCommit);

            rebaseHead = newCommit.getId();

            command(UpdateRef.class).setName(currentBranch).setNewValue(rebaseHead).call();
            command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

            workingTree().updateWorkHead(newTreeId);
            stagingArea().updateStageHead(newTreeId);
        }

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
        List<String> lines = Blobs.readLines(context().blobStore(), REBASE_SQUASH_BLOB);
        if (lines.isEmpty()) {
            return null;
        }
        ObjectId id = ObjectId.valueOf(lines.get(0).split("\t")[1].trim());
        String commitString = Joiner.on("\n").join(lines.subList(1, lines.size()));
        ByteArrayInputStream stream = new ByteArrayInputStream(
                commitString.getBytes(Charsets.UTF_8));
        RevCommit revCommit;
        try {
            revCommit = (RevCommit) TextRevObjectSerializer.INSTANCE.read(id, stream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to parse commit " + commitString, e);
        }
        return revCommit;
    }
}
