/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.storage.impl.Blobs.putBlob;
import static org.locationtech.geogig.storage.impl.Blobs.readLines;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.model.impl.CommitBuilder;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.plumbing.WriteTree2;
import org.locationtech.geogig.plumbing.merge.ConflictsUtils;
import org.locationtech.geogig.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.impl.PersistedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

/**
 * Given one or more existing commits, revert the changes that the related patches introduce, and
 * record some new commits that record them. This requires your working tree to be clean (no
 * modifications from the HEAD commit).
 * 
 */
@CanRunDuringConflict
public class RevertOp extends AbstractGeoGigOp<Boolean> {

    private static final Logger log = LoggerFactory.getLogger(RevertOp.class);

    private static final String REVERT_PREFIX = "revert/";

    private static final String NEXT = REVERT_PREFIX + "next";

    private List<ObjectId> commits;

    private boolean createCommit = true;

    private String currentBranch;

    private ObjectId revertHead;

    private boolean abort;

    private boolean continueRevert;

    /**
     * Adds a commit to revert.
     * 
     * @param onto a supplier for the commit id
     * @return {@code this}
     */
    public RevertOp addCommit(final Supplier<ObjectId> commit) {
        return addCommit(commit.get());
    }

    public RevertOp addCommit(final ObjectId commit) {
        checkNotNull(commit);

        if (this.commits == null) {
            this.commits = new ArrayList<ObjectId>();
        }
        this.commits.add(commit);
        return this;
    }

    /**
     * Sets whether to abort the current revert operation
     * 
     * @param abort
     * @return
     */
    public RevertOp setAbort(boolean abort) {
        this.abort = abort;
        return this;
    }

    /**
     * Sets whether to continue a revert operation aborted due to conflicts
     * 
     * @param continueRevert
     * @return {@code this}
     */
    public RevertOp setContinue(boolean continueRevert) {
        this.continueRevert = continueRevert;
        return this;
    }

    /**
     * If true, creates a new commit with the changes from the reverted commit. Otherwise, it just
     * adds the corresponding changes from the reverted commit to the index and working tree, but
     * does not commit anything
     * 
     * @param createCommit whether to create a commit with reverted changes or not.
     * @return {@code this}
     */
    public RevertOp setCreateCommit(boolean createCommit) {
        this.createCommit = createCommit;
        return this;

    }

    /**
     * Executes the revert operation.
     * 
     * @return always {@code true}
     */
    protected @Override Boolean _call() {

        checkArgument(!(continueRevert && abort), "Cannot continue and abort at the same time");

        if (abort) {
            command(RevertAbort.class).setProgressListener(getProgressListener()).call();
            return true;
        }

        final Ref currHead;
        {
            final Optional<Ref> head = command(RefParse.class).setName(Ref.HEAD).call();
            checkState(head.isPresent(), "Repository has no HEAD, can't revert.");
            currHead = head.get();
            checkState(currHead instanceof SymRef, "Can't revert from detached HEAD");
            checkState(!currHead.getObjectId().isNull(), "HEAD has no history.");
            currentBranch = currHead.peel().getName();
            revertHead = currHead.getObjectId();
        }
        getProgressListener().started();

        // Revert can only be run in a conflicted situation if the abort option is used
        final boolean hasConflicts = conflictsDatabase().hasConflicts(null);
        checkState(!hasConflicts || abort, "Cannot run operation while merge conflicts exist.");

        Optional<Ref> ref = command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        if (continueRevert) {
            checkState(ref.isPresent(),
                    "Cannot continue. You are not in the middle of a revert process.");
            // Commit the manually-merged changes with the info of the commit that caused the
            // conflict
            applyNextCommit(false);
            // Commit files should already be prepared, so we do nothing else
        } else {
            // count staged and unstaged changes
            checkState(stagingArea().isClean() && workingTree().isClean(),
                    "You must have a clean working tree and index to perform a revert.");

            checkState(!ref.isPresent(),
                    "You are currently in the middle of a merge or rebase operation <ORIG_HEAD is present>.");

            getProgressListener().started();

            command(UpdateRef.class).setName(Ref.ORIG_HEAD).setNewValue(currHead.getObjectId())
                    .call();

            // Here we prepare the files with the info about the commits to apply in reverse
            Repository repository = repository();
            List<RevCommit> commitsToRevert = commits.stream().map(c -> repository.getCommit(c))
                    .collect(Collectors.toList());
            createRevertCommitsInfoFiles(commitsToRevert);
        }

        boolean ret;
        do {
            ret = applyNextCommit(true);
        } while (ret);

        command(UpdateRef.class).setDelete(true).setName(Ref.ORIG_HEAD).call();

        getProgressListener().complete();

        return true;

    }

    private void createRevertCommitsInfoFiles(List<RevCommit> commitsToRebase) {

        for (int i = 0; i < commitsToRebase.size(); i++) {
            try {
                String blobName = REVERT_PREFIX + Integer.toString(i + 1);
                CharSequence contents = commitsToRebase.get(i).getId().toString();
                putBlob(context().blobStore(), blobName, contents);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create revert commits info", e);
            }
        }
        try {
            putBlob(context().blobStore(), NEXT, "1");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create next revert commit info", e);
        }

    }

    private boolean applyNextCommit(final boolean useCommitChanges) {
        Repository repository = repository();
        List<String> nextFile = readLines(context().blobStore(), NEXT);

        String idx = nextFile.get(0);
        String commitBlobName = REVERT_PREFIX + idx;
        List<String> commitFile = readLines(context().blobStore(), commitBlobName);
        if (commitFile.isEmpty()) {
            return false;
        }
        String commitId = commitFile.get(0);
        RevCommit commit = repository.getCommit(ObjectId.valueOf(commitId));
        try (PersistedIterable<Conflict> conflicts = ConflictsUtils.newTemporaryConflictStream()) {
            if (useCommitChanges) {
                applyRevertedChanges(commit, conflicts);
            }
            final long numConflicts = conflicts.size();
            if (createCommit && 0L == numConflicts) {
                createCommit(commit);
            } else {
                workingTree().updateWorkHead(repository.index().getTree().getId());
                if (numConflicts > 0L) {
                    // mark conflicted elements
                    command(ConflictsWriteOp.class).setConflicts(conflicts).call();

                    // created exception message
                    StringBuilder msg = new StringBuilder();
                    msg.append("error: could not apply ");
                    msg.append(commit.getId().toString().substring(0, 8));
                    msg.append(" " + commit.getMessage() + "\n");

                    Lists.newArrayList(Iterators.limit(conflicts.iterator(), 50)).forEach(c -> msg
                            .append("CONFLICT: conflict in ").append(c.getPath()).append("\n"));
                    if (numConflicts > 50) {
                        msg.append(String.format("And %,d more...", numConflicts - 50));
                    }
                    throw new RevertConflictsException(msg.toString());
                }
            }
        }
        context().blobStore().removeBlob(commitBlobName);
        int newIdx = Integer.parseInt(idx) + 1;
        putBlob(context().blobStore(), NEXT, Integer.toString(newIdx));
        return true;
    }

    private void applyRevertedChanges(final RevCommit commit,
            PersistedIterable<Conflict> conflictsTarget) {

        final ProgressListener progressListener = getProgressListener();

        progressListener.setDescription("Reverting commit " + commit.getId());

        final boolean revertingBranchTip = revertHead.equals(commit.getId());

        final Repository repository = repository();
        final ObjectId parentCommitId = commit.getParentIds().isEmpty() ? ObjectId.NULL
                : commit.getParentIds().get(0);

        final ObjectId parentTreeId = parentCommitId.isNull() ? RevTree.EMPTY_TREE_ID : //
                repository.commitExists(parentCommitId)
                        ? repository.getCommit(parentCommitId).getTreeId()
                        : RevTree.EMPTY_TREE_ID;

        final ObjectId headTreeId = repository.getCommit(revertHead).getTreeId();
        final RevTree headTree = repository.getTree(headTreeId);

        // get changes (in reverse)
        final DiffTree reverseDiffCommand = command(DiffTree.class).setNewTree(parentTreeId)
                .setOldTree(commit.getTreeId()).setReportTrees(false);

        try (PersistedIterable<DiffEntry> diffs = ConflictsUtils.newTemporaryDiffEntryStream()) {
            DiffEntry diff;
            final FindTreeChild findTreeChild = command(FindTreeChild.class).setParent(headTree);

            try (AutoCloseableIterator<DiffEntry> reverseDiff = reverseDiffCommand.call()) {
                log.info("Creating revert state of commit {}", commit.getId());
                Stopwatch sw = Stopwatch.createStarted();
                int count = 0;
                while (reverseDiff.hasNext()) {
                    diff = reverseDiff.next();
                    count++;

                    if (revertingBranchTip) {
                        diffs.add(diff);
                        continue;
                    }

                    if (diff.isAdd()) {
                        // Feature was deleted
                        Optional<NodeRef> node = findTreeChild.setChildPath(diff.newPath()).call();
                        // make sure it is still deleted
                        if (node.isPresent()) {
                            conflictsTarget.add(new Conflict(diff.newPath(), diff.oldObjectId(),
                                    node.get().getObjectId(), diff.newObjectId()));
                        } else {
                            diffs.add(diff);
                        }
                    } else {
                        // Feature was added or modified
                        Optional<NodeRef> node = findTreeChild.setChildPath(diff.oldPath()).call();
                        ObjectId nodeId = node.get().getNode().getObjectId();
                        // Make sure it wasn't changed
                        if (node.isPresent() && nodeId.equals(diff.oldObjectId())) {
                            diffs.add(diff);
                        } else {
                            // do not mark as conflict if reverting to the same feature currently in
                            // HEAD
                            if (!nodeId.equals(diff.newObjectId())) {
                                conflictsTarget.add(new Conflict(diff.oldPath(), diff.oldObjectId(),
                                        node.get().getObjectId(), diff.newObjectId()));
                            }
                        }
                    }
                }
                log.info(String.format("Created revert state of %,d features in %s", count,
                        sw.stop()));
            }

            StagingArea stagingArea = stagingArea();
            String msg = String.format("Staging %,d changes...", diffs.size());
            log.info(msg);
            progressListener.setDescription(msg);
            Stopwatch sw = Stopwatch.createStarted();
            stagingArea.stage(progressListener, diffs.iterator(), diffs.size());
            log.info(String.format("%,d changes staged in %s\n", diffs.size(), sw.stop()));
        }
    }

    private void createCommit(RevCommit commit) {

        // write new tree
        ObjectId newTreeId = command(WriteTree2.class).call();
        long timestamp = platform().currentTimeMillis();
        String committerName = resolveCommitter();
        String committerEmail = resolveCommitterEmail();
        // Create new commit
        CommitBuilder builder = new CommitBuilder();
        builder.setParentIds(Arrays.asList(revertHead));
        builder.setTreeId(newTreeId);
        builder.setCommitterTimestamp(timestamp);
        builder.setMessage(
                "Revert '" + commit.getMessage() + "'\nThis reverts " + commit.getId().toString());
        builder.setCommitter(committerName);
        builder.setCommitterEmail(committerEmail);
        builder.setAuthor(committerName);
        builder.setAuthorEmail(committerEmail);

        RevCommit newCommit = builder.build();
        objectDatabase().put(newCommit);

        revertHead = newCommit.getId();

        command(UpdateRef.class).setName(currentBranch).setNewValue(revertHead).call();
        command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

        workingTree().updateWorkHead(newTreeId);
        stagingArea().updateStageHead(newTreeId);

    }

    private String resolveCommitter() {
        final String key = "user.name";
        Optional<String> name = command(ConfigGet.class).setName(key).call();

        checkState(name.isPresent(),
                "%s not found in config. Use geogig config [--global] %s <your name> to configure it.",
                key, key);

        return name.get();
    }

    private String resolveCommitterEmail() {
        final String key = "user.email";
        Optional<String> email = command(ConfigGet.class).setName(key).call();

        checkState(email.isPresent(),
                "%s not found in config. Use geogig config [--global] %s <your email> to configure it.",
                key, key);

        return email.get();
    }
}
