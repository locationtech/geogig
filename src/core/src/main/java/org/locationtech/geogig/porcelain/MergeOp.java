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

import static org.locationtech.geogig.base.Preconditions.checkArgument;
import static org.locationtech.geogig.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.dsl.Objects;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.CleanRefsOp;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveBranchId;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.plumbing.merge.CheckMergeScenarioOp;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.MergeStatusBuilder;
import org.locationtech.geogig.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.plumbing.merge.SaveMergeCommitMessageOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import lombok.NonNull;

/**
 * 
 * Merge two or more histories together.
 * 
 */
public class MergeOp extends AbstractGeoGigOp<MergeOp.MergeReport> {

    public static final String MERGE_MSG = "MERGE_MSG";

    /**
     * The commits to merge on top of the current HEAD. If more than one then it's an octopus merge.
     */
    private List<ObjectId> commits = new ArrayList<ObjectId>();

    private String message = null;

    private boolean ours;

    private boolean theirs;

    private boolean continueMerge;

    private boolean noCommit;

    private boolean noFastForward;

    private boolean fastForwardOnly;

    private Optional<String> authorName = Optional.empty();

    private Optional<String> authorEmail = Optional.empty();

    /**
     * Original values
     */
    private Ref origHead = null;

    private Ref origCurrentBranch = null;

    private Ref origWorkHead = null;

    private Ref origStageHead = null;

    private boolean abort;

    /**
     * @param message the message for the merge commit
     * @return {@code this}
     */
    public MergeOp setMessage(final String message) {
        this.message = message;
        return this;
    }

    /**
     * Adds a commit whose history should be merged.
     * 
     * @param onto a supplier for the commit id
     * @return {@code this}
     */
    public MergeOp addCommit(final Supplier<ObjectId> commit) {
        return addCommit(commit.get());
    }

    public MergeOp addCommit(final @NonNull ObjectId commit) {
        this.commits.add(commit);
        return this;
    }

    public MergeOp setAbort() {
        this.abort = true;
        return this;
    }

    /**
     * 
     * @param ours true if the "ours" strategy should be used
     * @return {@code this}
     */
    public MergeOp setOurs(boolean ours) {
        this.ours = ours;
        return this;
    }

    /**
     * 
     * @param ours true if the "theirs" strategy should be used
     * @return {@code this}
     */
    public MergeOp setTheirs(boolean theirs) {
        this.theirs = theirs;
        return this;
    }

    /**
     * 
     * @param ours true if no commit should be made after the merge, leaving just the index with the
     *        merge result
     * @return {@code this}
     */
    public MergeOp setNoCommit(boolean noCommit) {
        this.noCommit = noCommit;
        return this;
    }

    /**
     * 
     * @param author the author of the commit
     * @param email email of author
     * @return {@code this}
     */
    public MergeOp setAuthor(@Nullable String authorName, @Nullable String authorEmail) {
        this.authorName = Optional.ofNullable(authorName);
        this.authorEmail = Optional.ofNullable(authorEmail);
        return this;
    }

    /**
     *
     * @param noFastForward true if the merge command should never fast forward merge.
     * @return {@code this}
     */
    public MergeOp setNoFastForward(boolean noFastForward) {
        this.noFastForward = noFastForward;
        return this;
    }

    /**
     *
     * @param fastForwardOnly true if the merge command should only fast forward merge. It will
     *        abort if it cant do so.
     * @return
     */
    public MergeOp setFastForwardOnly(boolean fastForwardOnly) {
        this.fastForwardOnly = fastForwardOnly;
        return this;
    }

    /**
     * Executes the merge operation.
     * 
     * @return always {@code true}
     */
    protected @Override MergeReport _call() throws RuntimeException {
        if (abort) {
            return abort();
        }
        final List<ObjectId> commits = this.commits;
        checkArgument(continueMerge || commits.size() > 0, "No commits specified for merge.");
        checkArgument(!(ours && theirs), "Cannot use both --ours and --theirs.");
        checkArgument(!(noFastForward && fastForwardOnly), "Cannot use both --no-ff and --ff-only");

        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        checkState(currHead.isPresent(), "Repository has no HEAD, can't merge.");

        checkState(workingTree().isClean(),
                "Merge cannot run if there are unstaged changes in the working tree");

        // capture original values in case the operation is cancelled
        origHead = currHead.get();
        getProgressListener()
                .setDescription(String.format("Merge: merging %s onto %s", commits, origHead));
        if (origHead instanceof SymRef) {
            final String currentBranch = ((SymRef) origHead).getTarget();
            origCurrentBranch = command(RefParse.class).setName(currentBranch).call().get();
        }
        origWorkHead = command(RefParse.class).setName(Ref.WORK_HEAD).call().get();
        origStageHead = command(RefParse.class).setName(Ref.STAGE_HEAD).call().get();

        Ref headRef = currHead.get();
        final ObjectId oursId = headRef.getObjectId();// on top of which commit to merge

        final ProgressListener progress = getProgressListener();
        progress.started();

        final Objects objects = geogig().objects();
        MergeStatusBuilder mergeStatusBuilder = new MergeStatusBuilder(context(), ours, commits,
                progress);
        MergeScenarioReport mergeScenario = null;

        List<CommitAncestorPair> pairs = new ArrayList<>();

        List<RevCommit> revCommits = new ArrayList<>();
        if (!headRef.getObjectId().isNull()) {
            revCommits.add(objects.getCommit(headRef.getObjectId()));
        }
        for (ObjectId commitId : commits) {
            revCommits.add(objects.getCommit(commitId));
        }
        progress.setDescription("Checking for possible conflicts...");
        final boolean mightHaveConflicts;// either there are conflicts or two features modified by
                                         // different branches might cause conflicts
        mightHaveConflicts = command(CheckMergeScenarioOp.class).setCommits(revCommits)
                .setProgressListener(progress).call();
        checkState(!(mightHaveConflicts && fastForwardOnly),
                "The flag --ff-only was specified but no fast forward merge could be executed");

        if (progress.isCanceled()) {
            cancel();
            return null;
        }

        if (mightHaveConflicts && !theirs) {
            checkState(commits.size() < 2,
                    "Conflicted merge.\nCannot merge more than two commits when conflicts exist"
                            + " or features have been modified in several histories");

            RevCommit headCommit = objects.getCommit(headRef.getObjectId());
            ObjectId commitId = commits.get(0);
            checkArgument(!commitId.isNull(), "Cannot merge a NULL commit.");
            checkArgument(objects.commitExists(commitId),
                    "Not a valid commit: " + commitId.toString());

            final RevCommit targetCommit = objects.getCommit(commitId);
            Optional<ObjectId> ancestorCommit = command(FindCommonAncestor.class)
                    .setLeft(headCommit).setRight(targetCommit).call();

            pairs.add(new CommitAncestorPair(commitId, ancestorCommit.get()));

            progress.setDescription("Possible conflicts. Creating intermediate merge status...");
            mergeScenario = command(ReportMergeScenarioOp.class).setMergeIntoCommit(headCommit)
                    .setToMergeCommit(targetCommit).setConsumer(mergeStatusBuilder).call();

            if (progress.isCanceled()) {
                cancel();
                return null;
            }

            workingTree().updateWorkHead(stagingArea().getTree().getId(),
                    "merge: reset staging area");

            if (!ours && mergeScenario.getConflicts() > 0) {
                // In case we use the "ours" strategy, we do nothing. We ignore conflicting
                // changes and leave the current elements
                command(UpdateRefs.class).setReason("merge: savepoint")//
                        .add(Ref.MERGE_HEAD, commitId)//
                        .add(Ref.ORIG_HEAD, headCommit.getId())//
                        .call();

                String mergeMsg = mergeStatusBuilder.getMergeMessage();
                String conflictMsg = mergeStatusBuilder.getConflictsMessage();
                command(SaveMergeCommitMessageOp.class).setMessage(mergeMsg).call();

                throw new MergeConflictsException(conflictMsg, headCommit.getId(), commitId,
                        mergeScenario);

            }
        } else {
            checkState(!mightHaveConflicts || commits.size() < 2,
                    "Conflicted merge.\nCannot merge more than two commits when conflicts exist"
                            + " or features have been modified in several histories");
            for (ObjectId commitId : commits) {
                if (headRef.getObjectId().isNull()) {
                    // Fast-forward
                    headRef = doFastForwardMerge(headRef, commitId, mergeStatusBuilder);
                    continue;
                }
                final RevCommit headCommit = objects.getCommit(headRef.getObjectId());
                final RevCommit targetCommit = objects.getCommit(commitId);

                progress.setDescription(String.format("Merging commit %s onto %s",
                        fmt(targetCommit), fmt(headCommit)));

                Optional<ObjectId> ancestorCommit = command(FindCommonAncestor.class)
                        .setLeft(headCommit).setRight(targetCommit).call();

                pairs.add(new CommitAncestorPair(commitId, ancestorCommit.get()));

                checkState(ancestorCommit.isPresent(), "No ancestor commit could be found.");

                if (commits.size() == 1) {
                    mergeScenario = command(ReportMergeScenarioOp.class)
                            .setMergeIntoCommit(headCommit).setToMergeCommit(targetCommit).call();
                    if (progress.isCanceled()) {
                        cancel();
                        return null;
                    }
                    progress.setDescription(mergeScenario.toString());

                    if (ancestorCommit.get().equals(headCommit.getId()) && !noFastForward) {
                        headRef = doFastForwardMerge(headRef, commitId, mergeStatusBuilder);
                        continue;
                    } else if (ancestorCommit.get().equals(commitId)) {
                        continue;
                    }
                }

                if (progress.isCanceled()) {
                    cancel();
                    return null;
                }

                // get changes
                progress.setDescription("Staging changes...");
                try (AutoCloseableIterator<DiffEntry> diff = command(DiffTree.class)
                        .setOldTree(ancestorCommit.get()).setNewTree(targetCommit.getId())
                        .setReportTrees(true).call()) {
                    // stage changes
                    progress.setProgress(0);
                    stagingArea().stage(progress, diff, -1);
                    mergeStatusBuilder.setChanged(true);
                    mergeStatusBuilder.setFastFoward(false);
                    workingTree().updateWorkHead(stagingArea().getTree().getId(),
                            "merge: reset to STAGE_HEAD");
                }
            }

            progress.complete();
        }
        if (!mergeStatusBuilder.isChanged()) {
            progress.setDescription("Merge: complete, nothing to merge.");
            throw new NothingToCommitException("The branch has already been merged.");
        }
        if (noFastForward) {
            mergeStatusBuilder.setFastFoward(false);
        }
        progress.setDescription("Creating merge commit");
        RevCommit mergeCommit = commit(mergeStatusBuilder.isFastForward());

        progress.setDescription("Merge: created merge commit " + mergeCommit);
        return new MergeReport(mergeCommit, Optional.ofNullable(mergeScenario), oursId, pairs);
    }

    private String fmt(RevCommit c) {
        String msg = c.getMessage();
        if (msg.length() > 30) {
            msg = msg.substring(0, 30) + "...";
        }
        return String.format("%s (%s)", c.getId().toString().substring(0, 8), msg);
    }

    private MergeReport abort() {
        command(CleanRefsOp.class).reason("merge: abort").call();
        conflictsDatabase().removeConflicts(null);
        return null;
    }

    private Ref doFastForwardMerge(final @NonNull Ref headRef, final @NonNull ObjectId commitId,
            MergeStatusBuilder mergeStatusBuilder) {
        getProgressListener().setDescription(
                String.format("Fast forward merging %s onto %s", RevObjects.toShortString(commitId),
                        RevObjects.toShortString(headRef.getObjectId())));

        final String reason = String.format("merge: fast forward %s onto %s",
                RevObjects.toShortString(commitId),
                RevObjects.toShortString(headRef.getObjectId()));

        final Ref currentBranch = headRef.peel();
        final Ref newBranchRef = new Ref(currentBranch.getName(), commitId);
        final Ref updatedHead;

        UpdateRefs updateRefs = command(UpdateRefs.class)//
                .setReason(reason)//
                .add(newBranchRef)//
                .add(Ref.WORK_HEAD, commitId)//
                .add(Ref.STAGE_HEAD, commitId);
        if (headRef instanceof SymRef) {
            updatedHead = new SymRef(Ref.HEAD, newBranchRef);
            updateRefs.add(updatedHead);
        } else {
            updatedHead = currentBranch;
        }
        updateRefs.call();
        mergeStatusBuilder.setChanged(true);
        return updatedHead;
    }

    private void cancel() {
        UpdateRefs updateRefs = command(UpdateRefs.class).setReason("merge: cancelled");
        // Restore original refs
        if (origHead != null) {
            if (origHead instanceof SymRef) {
                updateRefs.add(origCurrentBranch);
            } else {
                updateRefs.add(origHead);
            }
        }
        if (origWorkHead != null) {
            updateRefs.add(origWorkHead);
        }
        if (origStageHead != null) {
            updateRefs.add(origStageHead);
        }

        updateRefs.call();
        // Remove any conflicts that were generated.
        conflictsDatabase().removeConflicts(null);

    }

    private RevCommit commit(boolean fastForward) {

        final Geogig geogig = geogig();
        RevCommit mergeCommit;
        if (fastForward) {
            mergeCommit = geogig.objects().getCommit(commits.get(0));
        } else {
            String commitMessage = message;
            if (commitMessage == null) {
                commitMessage = "";
                for (ObjectId commit : commits) {
                    Optional<Ref> ref = command(ResolveBranchId.class).setObjectId(commit).call();
                    if (ref.isPresent()) {
                        commitMessage += "Merge branch " + ref.get().getName();
                    } else {
                        commitMessage += "Merge commit '" + commit.toString() + "'. ";
                    }
                }
            }
            if (noCommit) {
                final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
                SymRef headRef = (SymRef) currHead.get();
                RevCommit headCommit = geogig.objects().getCommit(headRef.getObjectId());
                command(UpdateRefs.class).setReason("merge: no commit")//
                        .add(Ref.MERGE_HEAD, commits.get(0))//
                        // TODO:how to store multiple ids when octopus merge
                        .add(Ref.ORIG_HEAD, headCommit.getId())//
                        .call();
                mergeCommit = headCommit;
                command(SaveMergeCommitMessageOp.class).setMessage(commitMessage).call();
            } else {
                CommitOp commit = command(CommitOp.class).setAllowEmpty(true)
                        .setMessage(commitMessage).addParents(commits);
                if (authorName.isPresent() || authorEmail.isPresent()) {
                    commit.setAuthor(authorName.orElse(null), authorEmail.orElse(null));
                }
                mergeCommit = commit.call();
            }
        }

        getProgressListener().complete();

        return mergeCommit;
    }

    public class CommitAncestorPair {
        private ObjectId theirs;

        private ObjectId ancestor;

        public ObjectId getTheirs() {
            return theirs;
        }

        public ObjectId getAncestor() {
            return ancestor;
        }

        public CommitAncestorPair(ObjectId theirs, ObjectId ancestor) {
            this.theirs = theirs;
            this.ancestor = ancestor;
        }
    }

    public class MergeReport {
        private RevCommit mergeCommit;

        private Optional<MergeScenarioReport> report;

        private ObjectId ours;

        private List<CommitAncestorPair> pairs;

        public RevCommit getMergeCommit() {
            return mergeCommit;
        }

        public ObjectId getOurs() {
            return ours;
        }

        public List<CommitAncestorPair> getPairs() {
            return pairs;
        }

        public Optional<MergeScenarioReport> getReport() {
            return report;
        }

        public MergeReport(RevCommit mergeCommit, Optional<MergeScenarioReport> report,
                ObjectId ours, List<CommitAncestorPair> pairs) {
            this.mergeCommit = mergeCommit;
            this.report = report;
            this.ours = ours;
            this.pairs = pairs;
        }
    }
}
