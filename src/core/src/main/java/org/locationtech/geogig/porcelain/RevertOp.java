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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.DiffTree;
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
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;

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

        final ProgressListener progress = getProgressListener();

        progress.setDescription("Reverting commit " + commit.getId());

        final Repository repository = repository();
        final ObjectId parentCommitId = commit.getParentIds().isEmpty() ? ObjectId.NULL
                : commit.getParentIds().get(0);

        final ObjectId parentTreeId = parentCommitId.isNull() ? RevTree.EMPTY_TREE_ID : //
                repository.commitExists(parentCommitId)
                        ? repository.getCommit(parentCommitId).getTreeId()
                        : RevTree.EMPTY_TREE_ID;

        // get changes (in reverse)
        final DiffTree reverseDiffCommand = command(DiffTree.class)//
                .setNewTree(parentTreeId)//
                .setOldTree(commit.getTreeId())//
                .setReportTrees(false)//
                .setPreserveIterationOrder(true);

        // get changes from current tip up to the commit being reversed
        final DiffTree branchDiffCommand = command(DiffTree.class)//
                .setNewTree(revertHead)//
                .setOldTree(commit.getId())//
                .setReportTrees(false)//
                .setPreserveIterationOrder(true);

        progress.setMaxProgress(-1);

        try (PersistedIterable<DiffEntry> diffs = ConflictsUtils.newTemporaryDiffEntryStream()) {
            final Function<ProgressListener, String> oldProgressIndicator = progress
                    .progressIndicator();
            int count = 0;
            progress.setProgressIndicator(
                    p -> String.format("Processed %,d features (%,d conflicts)", diffs.size(),
                            conflictsTarget.size()));

            Stopwatch sw = Stopwatch.createStarted();
            try (AutoCloseableIterator<DiffEntry> reverseDiff = reverseDiffCommand.call()) { //

                String msg = String.format("Creating revert state of commit %s (%s)",
                        commit.getId(), commit.getMessage());
                log.info(msg);

                final Iterator<List<DiffEntry>> partitions = Iterators.partition(reverseDiff,
                        10_000);
                while (partitions.hasNext()) {

                    List<DiffEntry> partition = partitions.next();

                    // e -> e.path()
                    com.google.common.base.Function<DiffEntry,String> fn = new com.google.common.base.Function<DiffEntry, String>() {
                        @Override
                        public String apply(DiffEntry e) {
                           return e.path();
                        }};

                    branchDiffCommand.setPathFilter(Lists.transform(partition, fn));

                    try (AutoCloseableIterator<DiffEntry> headToCommitDiff = branchDiffCommand
                            .call()) {

                        Iterator<DiffTuple> mergeSortDiff = mergeSortDiff(headToCommitDiff,
                                partition.iterator());

                        while (mergeSortDiff.hasNext()) {
                            final DiffTuple tuple = mergeSortDiff.next();
                            @Nullable
                            DiffEntry left = tuple.left;
                            final DiffEntry right = tuple.right;
                            progress.setProgress(++count);
                            if (null == left) {
                                diffs.add(right);
                                continue;
                            }

                            if (right.isAdd()) {
                                // Feature was deleted
                                ObjectId ancestor = right.oldObjectId();
                                ObjectId ours = left.newObjectId();
                                ObjectId theirs = right.newObjectId();
                                conflictsTarget
                                        .add(new Conflict(right.path(), ancestor, ours, theirs));
                            } else {
                                // Feature was added or modified
                                ObjectId ours = left.newObjectId();
                                // Make sure it wasn't changed
                                // do not mark as conflict if reverting to the same feature
                                // currently in
                                // HEAD
                                if (ours.equals(right.oldObjectId())) {
                                    diffs.add(right);
                                } else if (!ours.equals(right.newObjectId())) {
                                    ObjectId ancestor = right.oldObjectId();
                                    ObjectId theirs = right.newObjectId();
                                    conflictsTarget.add(
                                            new Conflict(right.oldPath(), ancestor, ours, theirs));
                                }
                            }
                        }
                    }
                }
                progress.setProgressIndicator(oldProgressIndicator);
            }

            String msg = String.format("Created revert state of %,d features in %s", count,
                    sw.stop());
            log.info(msg);
            StagingArea stagingArea = stagingArea();

            msg = String.format("Staging %,d changes...", diffs.size());
            log.info(msg);
            progress.setDescription(msg);
            sw = Stopwatch.createStarted();
            stagingArea.stage(progress, diffs.iterator(), diffs.size());
            msg = String.format("%,d changes staged in %s\n", diffs.size(), sw.stop());
            log.info(msg);
        }
    }

    private static class DiffTuple {
        //@formatter:off
        final @Nullable DiffEntry left, right;
        public DiffTuple(DiffEntry left, DiffEntry right) {this.left = left;this.right = right;}
        static DiffTuple of(DiffEntry left, DiffEntry right) { return new DiffTuple(left, right); }
        //@formatter:on
    }

    private static final Comparator<DiffEntry> comparator = (l, r) -> {
        checkNotNull(l);
        checkNotNull(r);
        return CanonicalNodeNameOrder.INSTANCE.compare(l.name(), r.name());
    };

    private Iterator<DiffTuple> mergeSortDiff(final Iterator<DiffEntry> leftMerge,
            final Iterator<DiffEntry> rightMerge) {

        return new AbstractIterator<RevertOp.DiffTuple>() {

            final PeekingIterator<DiffEntry> left = Iterators.peekingIterator(leftMerge);

            final PeekingIterator<DiffEntry> right = Iterators.peekingIterator(rightMerge);

            protected @Override DiffTuple computeNext() {
                DiffEntry peekLeft = left.hasNext() ? left.peek() : null;
                DiffEntry peekRight = right.hasNext() ? right.peek() : null;
                if (null == peekLeft && null == peekRight) {
                    return endOfData();
                }
                if (null == peekLeft) { // no more entries at left
                    return DiffTuple.of(null, right.next());
                }
                if (null == peekRight) {// no more entries at right, no need to continue
                    return endOfData();
                }
                // skip all left entries that are lower than right
                while (peekLeft != null && comparator.compare(peekLeft, peekRight) < 0) {
                    left.next();// consume peekLeft
                    peekLeft = left.hasNext() ? left.peek() : null;
                }
                if (null == peekLeft) { // no more entries at left
                    return DiffTuple.of(null, right.next());
                }

                DiffEntry lde, rde;
                int c = comparator.compare(peekLeft, peekRight);
                Preconditions.checkState(c >= 0);
                if (c == 0) {
                    lde = left.next();
                    rde = right.next();
                } else {
                    lde = null;
                    rde = right.next();
                }
                return DiffTuple.of(lde, rde);
            }
        };
    }

    private void createCommit(RevCommit commit) {

        // write new tree
        ObjectId newTreeId = command(WriteTree2.class).call();
        long timestamp = platform().currentTimeMillis();
        String committerName = resolveCommitter();
        String committerEmail = resolveCommitterEmail();
        // Create new commit
        RevCommitBuilder builder = RevCommit.builder();
        builder.parentIds(Arrays.asList(revertHead));
        builder.treeId(newTreeId);
        builder.committerTimestamp(timestamp);
        builder.message(
                "Revert '" + commit.getMessage() + "'\nThis reverts " + commit.getId().toString());
        builder.committer(committerName);
        builder.committerEmail(committerEmail);
        builder.author(committerName);
        builder.authorEmail(committerEmail);

        RevCommit newCommit = builder.build();
        objectDatabase().put(newCommit);

        revertHead = newCommit.getId();

        command(UpdateRef.class).setName(currentBranch).setNewValue(revertHead).call();
        command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

        workingTree().updateWorkHead(newTreeId);
        stagingArea().updateStageHead(newTreeId);

    }

    private String resolveCommitter() {
        final String namekey = "user.name";

        String name = getClientData(namekey, String.class)
                .or(command(ConfigGet.class).setName(namekey).call()).orNull();

        checkState(name != null,
                "%s not found in config. Use geogig config [--global] %s <your name> to configure it.",
                namekey, namekey);

        return name;
    }

    private String resolveCommitterEmail() {
        final String emailkey = "user.email";

        String email = getClientData(emailkey, String.class)
                .or(command(ConfigGet.class).setName(emailkey).call()).orNull();

        checkState(email != null,
                "%s not found in config. Use geogig config [--global] %s <your email> to configure it.",
                emailkey, emailkey);

        return email;
    }
}
