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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.FeatureInfo;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.SubProgressListener;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.DiffTree;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.ResolveBranchId;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.CheckMergeScenarioOp;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.api.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.api.plumbing.merge.SaveMergeCommitMessageOp;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * 
 * Merge two or more histories together.
 * 
 */
public class MergeOp extends AbstractGeoGigOp<MergeOp.MergeReport> {

    public static final String MERGE_MSG = "MERGE_MSG";

    private List<ObjectId> commits = new ArrayList<ObjectId>();;

    private String message = null;

    private boolean ours;

    private boolean theirs;

    private boolean noCommit;

    private boolean noFastForward;

    private boolean fastForwardOnly;

    private Optional<String> authorName = Optional.absent();

    private Optional<String> authorEmail = Optional.absent();

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
        checkNotNull(commit);

        this.commits.add(commit.get());
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
        this.authorName = Optional.fromNullable(authorName);
        this.authorEmail = Optional.fromNullable(authorEmail);
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
    @Override
    protected MergeReport _call() throws RuntimeException {

        checkArgument(commits.size() > 0, "No commits specified for merge.");
        checkArgument(!(ours && theirs), "Cannot use both --ours and --theirs.");
        checkArgument(!(noFastForward && fastForwardOnly), "Cannot use both --no-ff and --ff-only");

        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        checkState(currHead.isPresent(), "Repository has no HEAD, can't rebase.");
        Ref headRef = currHead.get();
        ObjectId oursId = headRef.getObjectId();
        // checkState(currHead.get() instanceof SymRef,
        // "Can't rebase from detached HEAD");
        // SymRef headRef = (SymRef) currHead.get();
        // final String currentBranch = headRef.getTarget();

        final ProgressListener progress = getProgressListener();
        progress.started();

        boolean fastForward = true;
        boolean changed = false;

        MergeScenarioReport mergeScenario = null;

        List<CommitAncestorPair> pairs = Lists.newArrayList();

        List<RevCommit> revCommits = Lists.newArrayList();
        if (!ObjectId.NULL.equals(headRef.getObjectId())) {
            revCommits.add(repository().getCommit(headRef.getObjectId()));
        }
        for (ObjectId commitId : commits) {
            revCommits.add(repository().getCommit(commitId));
        }
        final boolean hasConflictsOrAutomerge;
        hasConflictsOrAutomerge = command(CheckMergeScenarioOp.class).setCommits(revCommits).call()
                .booleanValue();
        checkState(!(hasConflictsOrAutomerge && fastForwardOnly),
                "The flag --ff-only was specified but no fast forward merge could be executed");
        if (hasConflictsOrAutomerge && !theirs) {
            checkState(commits.size() < 2,
                    "Conflicted merge.\nCannot merge more than two commits when conflicts exist"
                            + " or features have been modified in several histories");

            RevCommit headCommit = repository().getCommit(headRef.getObjectId());
            ObjectId commitId = commits.get(0);
            checkArgument(!ObjectId.NULL.equals(commitId), "Cannot merge a NULL commit.");
            checkArgument(repository().commitExists(commitId),
                    "Not a valid commit: " + commitId.toString());

            final RevCommit targetCommit = repository().getCommit(commitId);
            Optional<ObjectId> ancestorCommit = command(FindCommonAncestor.class)
                    .setLeft(headCommit).setRight(targetCommit).call();

            pairs.add(new CommitAncestorPair(commitId, ancestorCommit.get()));

            mergeScenario = command(ReportMergeScenarioOp.class).setMergeIntoCommit(headCommit)
                    .setToMergeCommit(targetCommit).call();

            List<FeatureInfo> merged = mergeScenario.getMerged();
            for (FeatureInfo feature : merged) {
                this.workingTree().insert(NodeRef.parentPath(feature.getPath()),
                        feature.getFeature());
                Iterator<DiffEntry> unstaged = workingTree().getUnstaged(null);
                index().stage(progress, unstaged, 0);
                changed = true;
                fastForward = false;
            }
            List<DiffEntry> unconflicting = mergeScenario.getUnconflicted();
            if (!unconflicting.isEmpty()) {
                index().stage(progress, unconflicting.iterator(), 0);
                changed = true;
                fastForward = false;
            }

            workingTree().updateWorkHead(index().getTree().getId());

            List<Conflict> conflicts = mergeScenario.getConflicts();
            if (!ours && !conflicts.isEmpty()) {
                // In case we use the "ours" strategy, we do nothing. We ignore conflicting
                // changes and leave the current elements
                command(UpdateRef.class).setName(Ref.MERGE_HEAD).setNewValue(commitId).call();
                command(UpdateRef.class).setName(Ref.ORIG_HEAD).setNewValue(headCommit.getId())
                        .call();
                command(ConflictsWriteOp.class).setConflicts(conflicts).call();

                StringBuilder msg = new StringBuilder();
                Optional<Ref> ref = command(ResolveBranchId.class).setObjectId(commitId).call();
                if (ref.isPresent()) {
                    msg.append("Merge branch " + ref.get().getName());
                } else {
                    msg.append("Merge commit '" + commitId.toString() + "'. ");
                }
                msg.append("\n\nConflicts:\n");
                for (Conflict conflict : mergeScenario.getConflicts()) {
                    msg.append("\t" + conflict.getPath() + "\n");
                }

                command(SaveMergeCommitMessageOp.class).setMessage(msg.toString()).call();

                StringBuilder sb = new StringBuilder();
                for (Conflict conflict : Iterables.limit(conflicts, 50) ) {
                    sb.append("CONFLICT: Merge conflict in " + conflict.getPath() + "\n");
                }
                if (conflicts.size() > 50) {
                    sb.append("and " + (conflicts.size() - 50) + " more.\n");
                }
                sb.append("Automatic merge failed. Fix conflicts and then commit the result.\n");
                throw new MergeConflictsException(sb.toString(), headCommit.getId(), commitId);

            }
        } else {
            checkState(!hasConflictsOrAutomerge || commits.size() < 2,
                    "Conflicted merge.\nCannot merge more than two commits when conflicts exist"
                            + " or features have been modified in several histories");
            for (ObjectId commitId : commits) {
                ProgressListener subProgress = subProgress(100.f / commits.size());

                checkArgument(!ObjectId.NULL.equals(commitId), "Cannot merge a NULL commit.");
                checkArgument(repository().commitExists(commitId),
                        "Not a valid commit: " + commitId.toString());

                subProgress.started();
                if (ObjectId.NULL.equals(headRef.getObjectId())) {
                    // Fast-forward
                    if (headRef instanceof SymRef) {
                        final String currentBranch = ((SymRef) headRef).getTarget();
                        command(UpdateRef.class).setName(currentBranch).setNewValue(commitId)
                                .call();
                        headRef = (SymRef) command(UpdateSymRef.class).setName(Ref.HEAD)
                                .setNewValue(currentBranch).call().get();
                    } else {
                        headRef = command(UpdateRef.class).setName(headRef.getName())
                                .setNewValue(commitId).call().get();
                    }

                    workingTree().updateWorkHead(commitId);
                    index().updateStageHead(commitId);
                    subProgress.complete();
                    changed = true;
                    continue;
                }

                RevCommit headCommit = repository().getCommit(headRef.getObjectId());
                final RevCommit targetCommit = repository().getCommit(commitId);

                Optional<ObjectId> ancestorCommit = command(FindCommonAncestor.class)
                        .setLeft(headCommit).setRight(targetCommit).call();

                pairs.add(new CommitAncestorPair(commitId, ancestorCommit.get()));

                subProgress.setProgress(10.f);

                checkState(ancestorCommit.isPresent(), "No ancestor commit could be found.");

                if (commits.size() == 1) {
                    mergeScenario = command(ReportMergeScenarioOp.class)
                            .setMergeIntoCommit(headCommit).setToMergeCommit(targetCommit).call();
                    if (ancestorCommit.get().equals(headCommit.getId()) && !noFastForward) {
                        // Fast-forward
                        if (headRef instanceof SymRef) {
                            final String currentBranch = ((SymRef) headRef).getTarget();
                            command(UpdateRef.class).setName(currentBranch).setNewValue(commitId)
                                    .call();
                            headRef = (SymRef) command(UpdateSymRef.class).setName(Ref.HEAD)
                                    .setNewValue(currentBranch).call().get();
                        } else {
                            headRef = command(UpdateRef.class).setName(headRef.getName())
                                    .setNewValue(commitId).call().get();
                        }

                        workingTree().updateWorkHead(commitId);
                        index().updateStageHead(commitId);
                        subProgress.complete();
                        changed = true;
                        continue;
                    } else if (ancestorCommit.get().equals(commitId)) {
                        continue;
                    }
                }

                // get changes
                Iterator<DiffEntry> diff = command(DiffTree.class).setOldTree(ancestorCommit.get())
                        .setNewTree(targetCommit.getId()).setReportTrees(true).call();
                // stage changes
                index().stage(new SubProgressListener(subProgress, 100.f), diff, 0);
                changed = true;
                fastForward = false;

                workingTree().updateWorkHead(index().getTree().getId());

                subProgress.complete();

            }

        }

        if (!changed) {
            throw new NothingToCommitException("The branch has already been merged.");
        }
        if (noFastForward) {
            fastForward = false;
        }
        RevCommit mergeCommit = commit(fastForward);

        MergeReport result = new MergeReport(mergeCommit, Optional.fromNullable(mergeScenario),
                oursId, pairs);

        return result;

    }

    private RevCommit commit(boolean fastForward) {

        RevCommit mergeCommit;
        if (fastForward) {
            mergeCommit = repository().getCommit(commits.get(0));
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
                RevCommit headCommit = repository().getCommit(headRef.getObjectId());
                command(UpdateRef.class).setName(Ref.MERGE_HEAD).setNewValue(commits.get(0)).call();
                // TODO:how to store multiple ids when octopus merge
                command(UpdateRef.class).setName(Ref.ORIG_HEAD).setNewValue(headCommit.getId())
                        .call();
                mergeCommit = headCommit;
                command(SaveMergeCommitMessageOp.class).setMessage(commitMessage).call();
            } else {
                CommitOp commit = command(CommitOp.class).setAllowEmpty(true)
                        .setMessage(commitMessage).addParents(commits);
                if (authorName.isPresent() || authorEmail.isPresent()) {
                    commit.setAuthor(authorName.orNull(), authorEmail.orNull());
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
