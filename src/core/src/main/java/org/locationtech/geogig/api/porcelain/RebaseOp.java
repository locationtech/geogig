/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.CommitBuilder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.hooks.Hookable;
import org.locationtech.geogig.api.plumbing.CatObject;
import org.locationtech.geogig.api.plumbing.DiffTree;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.plumbing.WriteTree2;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.plumbing.merge.ConflictsReadOp;
import org.locationtech.geogig.api.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.api.plumbing.merge.ReportCommitConflictsOp;
import org.locationtech.geogig.api.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ObjectReader;
import org.locationtech.geogig.storage.text.TextSerializationFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.Files;

/**
 * 
 * Rebase the current head to the included branch head.
 * 
 * Rebasing is done following these steps:
 * 
 * -Commits to apply are computed and for each one a file is created in the rebase-apply folder,
 * containing the ID of the commit. Files have correlative names starting on 1, indicating the order
 * in which they should be applied
 * 
 * -HEAD is rewinded to starting point
 * 
 * -Commits are applied. For each commit applied, the corresponding file is deleted
 * 
 * -A file named 'next' keeps track of the next commit to apply between executions of the rebase
 * command, in case of conflicts
 * 
 * - A file named 'branch' keeps track of the current branch name
 * 
 * 
 * 
 */
@CanRunDuringConflict
@Hookable(name = "rebase")
public class RebaseOp extends AbstractGeoGigOp<Boolean> {

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
                    "Can't rebase from detached HEAD");
            Preconditions.checkState(upstream != null, "No upstream target has been specified.");
            Preconditions.checkState(!ObjectId.NULL.equals(upstream.get()),
                    "Upstream did not resolve to a commit.");
        }

        // Rebase can only be run in a conflicted situation if the skip or abort option is used
        List<Conflict> conflicts = command(ConflictsReadOp.class).call();
        Preconditions.checkState(conflicts.isEmpty() || skip || abort,
                "Cannot run operation while merge conflicts exist.");

        Optional<Ref> ref = command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        File branchFile = new File(getRebaseFolder(), "branch");
        RevCommit squashCommit = readSquashCommit();
        if (abort) {
            Preconditions.checkState(ref.isPresent() && branchFile.exists(),
                    "Cannot abort. You are not in the middle of a rebase process.");
            command(ResetOp.class).setMode(ResetMode.HARD)
                    .setCommit(Suppliers.ofInstance(ref.get().getObjectId())).call();
            command(UpdateRef.class).setDelete(true).setName(Ref.ORIG_HEAD).call();
            branchFile.delete();
            return true;
        } else if (continueRebase) {
            Preconditions.checkState(ref.isPresent() && branchFile.exists(),
                    "Cannot continue. You are not in the middle of a rebase process.");
            try {
                currentBranch = Files.readFirstLine(branchFile, Charsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read current branch info file");
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
            Preconditions.checkState(ref.isPresent() && branchFile.exists(),
                    "Cannot skip. You are not in the middle of a rebase process.");
            try {
                currentBranch = Files.readFirstLine(branchFile, Charsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read current branch info file");
            }
            rebaseHead = currHead.get().getObjectId();
            command(ResetOp.class).setCommit(Suppliers.ofInstance(rebaseHead))
                    .setMode(ResetMode.HARD).call();
            if (squashCommit == null) {
                skipCurrentCommit();
                applyNextCommit(true);
            } else {
                return true;
            }
        } else {
            Preconditions
                    .checkState(!ref.isPresent(),
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
                index().updateStageHead(upstream.get());
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
                index().updateStageHead(upstream.get());
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
                    .setMode(ResetMode.HARD).call();

            if (squashMessage != null) {
                CommitBuilder builder = new CommitBuilder(commitsToRebase.get(0));
                builder.setParentIds(Arrays.asList(ancestorCommit.get()));
                builder.setMessage(squashMessage);
                squashCommit = builder.build();
                // save the commit, since it does not exist in the database, and might be needed if
                // there is a conflict
                CharSequence commitString = command(CatObject.class).setObject(
                        Suppliers.ofInstance(squashCommit)).call();
                File squashFile = new File(getRebaseFolder(), "squash");
                try {
                    Files.write(commitString, squashFile, Charsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot create squash commit info file");
                }
                applyCommit(squashCommit, true);
                return true;
            } else {
                createRebaseCommitsInfoFiles(commitsToRebase);
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
        File squashFile = new File(getRebaseFolder(), "squash");
        if (squashFile.exists()) {
            squashFile.delete();
        }
        command(UpdateRef.class).setDelete(true).setName(Ref.ORIG_HEAD).call();
        branchFile.delete();

        // subProgress.complete();

        getProgressListener().complete();

        return true;

    }

    private File getRebaseFolder() {
        URL dir = command(ResolveGeogigDir.class).call().get();
        File rebaseFolder = new File(dir.getFile(), "rebase-apply");
        if (!rebaseFolder.exists()) {
            Preconditions.checkState(rebaseFolder.mkdirs(), "Cannot create 'rebase-apply' folder");
        }
        return rebaseFolder;
    }

    private void skipCurrentCommit() {
        File rebaseFolder = getRebaseFolder();
        File nextFile = new File(rebaseFolder, "next");
        try {
            String idx = Files.readFirstLine(nextFile, Charsets.UTF_8);
            File commitFile = new File(rebaseFolder, idx);
            commitFile.delete();
            int newIdx = Integer.parseInt(idx) + 1;
            Files.write(Integer.toString(newIdx), nextFile, Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read/write rebase commits index file");
        }

    }

    private void createRebaseCommitsInfoFiles(List<RevCommit> commitsToRebase) {
        File rebaseFolder = getRebaseFolder();
        for (int i = commitsToRebase.size() - 1, idx = 1; i >= 0; i--, idx++) {

            File file = new File(rebaseFolder, Integer.toString(idx));
            try {
                Files.write(commitsToRebase.get(i).getId().toString(), file, Charsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create rebase commits info files");
            }
        }
        File nextFile = new File(rebaseFolder, "next");
        try {
            Files.write("1", nextFile, Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create next rebase commit info file");
        }

    }

    private boolean applyNextCommit(boolean useCommitChanges) {
        File rebaseFolder = getRebaseFolder();
        File nextFile = new File(rebaseFolder, "next");
        try {
            String idx = Files.readFirstLine(nextFile, Charsets.UTF_8);
            File commitFile = new File(rebaseFolder, idx);
            if (commitFile.exists()) {
                String commitId = Files.readFirstLine(commitFile, Charsets.UTF_8);
                RevCommit commit = repository().getCommit(ObjectId.valueOf(commitId));
                applyCommit(commit, useCommitChanges);
                commitFile.delete();
                int newIdx = Integer.parseInt(idx) + 1;
                Files.write(Integer.toString(newIdx), nextFile, Charsets.UTF_8);
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read/write rebase commits index file");
        }

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
            ObjectId parentTreeId;
            ObjectId parentCommitId = ObjectId.NULL;
            if (commitToApply.getParentIds().size() > 0) {
                parentCommitId = commitToApply.getParentIds().get(0);
            }
            parentTreeId = ObjectId.NULL;
            if (repository.commitExists(parentCommitId)) {
                parentTreeId = repository.getCommit(parentCommitId).getTreeId();
            }
            // get changes
            Iterator<DiffEntry> diff = command(DiffTree.class).setOldTree(parentTreeId)
                    .setNewTree(commitToApply.getTreeId()).setReportTrees(true).call();

            // see if there are conflicts
            MergeScenarioReport report = command(ReportCommitConflictsOp.class).setCommit(
                    commitToApply).call();
            if (report.getConflicts().isEmpty()) {
                // stage changes
                index().stage(getProgressListener(), diff, 0);

                // write new tree
                ObjectId newTreeId = command(WriteTree2.class).call();

                long timestamp = platform.currentTimeMillis();
                // Create new commit
                CommitBuilder builder = new CommitBuilder(commitToApply);
                builder.setParentIds(Arrays.asList(rebaseHead));
                builder.setTreeId(newTreeId);
                builder.setCommitterTimestamp(timestamp);
                builder.setCommitterTimeZoneOffset(platform.timeZoneOffset(timestamp));

                RevCommit newCommit = builder.build();
                repository.objectDatabase().put(newCommit);

                rebaseHead = newCommit.getId();

                command(UpdateRef.class).setName(currentBranch).setNewValue(rebaseHead).call();
                command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

                workingTree().updateWorkHead(newTreeId);
                index().updateStageHead(newTreeId);

            } else {
                Iterator<DiffEntry> unconflicted = report.getUnconflicted().iterator();
                // stage unconflicted changes
                index().stage(getProgressListener(), unconflicted, 0);
                workingTree().updateWorkHead(index().getTree().getId());

                // mark conflicted elements
                command(ConflictsWriteOp.class).setConflicts(report.getConflicts()).call();

                // created exception message
                StringBuilder msg = new StringBuilder();
                msg.append("error: could not apply ");
                msg.append(commitToApply.getId().toString().substring(0, 7));
                msg.append(" " + commitToApply.getMessage() + "\n");

                for (Conflict conflict : report.getConflicts()) {
                    msg.append("CONFLICT: conflict in " + conflict.getPath() + "\n");
                }

                File branchFile = new File(getRebaseFolder(), "branch");
                try {
                    Files.write(currentBranch, branchFile, Charsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot create current branch info file");
                }

                throw new RebaseConflictsException(msg.toString());

            }
        } else {
            // write new tree
            ObjectId newTreeId = command(WriteTree2.class).call();

            long timestamp = platform.currentTimeMillis();
            // Create new commit
            CommitBuilder builder = new CommitBuilder(commitToApply);
            builder.setParentIds(Arrays.asList(rebaseHead));
            builder.setTreeId(newTreeId);
            builder.setCommitterTimestamp(timestamp);
            builder.setCommitterTimeZoneOffset(platform.timeZoneOffset(timestamp));

            RevCommit newCommit = builder.build();
            repository.objectDatabase().put(newCommit);

            rebaseHead = newCommit.getId();

            command(UpdateRef.class).setName(currentBranch).setNewValue(rebaseHead).call();
            command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

            workingTree().updateWorkHead(newTreeId);
            index().updateStageHead(newTreeId);
        }

    }

    /**
     * Return the commit that is the squashed version of all the commits to apply, reading it from
     * the 'squash' file. If the file does not exist (that is, we are not in the middle of a rebase
     * with squash operation), returns null
     * 
     * @return
     */
    private RevCommit readSquashCommit() {
        File file = new File(getRebaseFolder(), "squash");
        if (!file.exists()) {
            return null;
        }
        List<String> lines;
        try {
            lines = Files.readLines(file, Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create squash commit info file");
        }
        ObjectId id = ObjectId.valueOf(lines.get(0).split("\t")[1].trim());
        String commitString = Joiner.on("\n").join(lines.subList(1, lines.size()));
        ByteArrayInputStream stream = new ByteArrayInputStream(
                commitString.getBytes(Charsets.UTF_8));
        ObjectReader<RevCommit> reader = new TextSerializationFactory().createCommitReader();
        RevCommit revCommit = reader.read(id, stream);
        return revCommit;

    }
}
