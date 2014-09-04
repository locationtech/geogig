/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.CommitBuilder;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.DiffTree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.plumbing.WriteTree2;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.plumbing.merge.ConflictsReadOp;
import org.locationtech.geogig.api.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.api.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

/**
 * Given one or more existing commits, revert the changes that the related patches introduce, and
 * record some new commits that record them. This requires your working tree to be clean (no
 * modifications from the HEAD commit).
 * 
 */
@CanRunDuringConflict
public class RevertOp extends AbstractGeoGigOp<Boolean> {

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
        Preconditions.checkNotNull(commit);

        if (this.commits == null) {
            this.commits = new ArrayList<ObjectId>();
        }
        this.commits.add(commit.get());
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
    @Override
    protected Boolean _call() {

        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(currHead.isPresent(), "Repository has no HEAD, can't revert.");
        Preconditions.checkState(currHead.get() instanceof SymRef,
                "Can't revert from detached HEAD");
        final SymRef headRef = (SymRef) currHead.get();
        Preconditions.checkState(!headRef.getObjectId().equals(ObjectId.NULL),
                "HEAD has no history.");
        currentBranch = headRef.getTarget();
        revertHead = currHead.get().getObjectId();

        Preconditions.checkArgument(!(continueRevert && abort),
                "Cannot continue and abort at the same time");

        // count staged and unstaged changes
        long staged = index().countStaged(null).count();
        long unstaged = workingTree().countUnstaged(null).count();
        Preconditions.checkState((staged == 0 && unstaged == 0) || abort || continueRevert,
                "You must have a clean working tree and index to perform a revert.");

        getProgressListener().started();

        // Revert can only be run in a conflicted situation if the abort option is used
        List<Conflict> conflicts = command(ConflictsReadOp.class).call();
        Preconditions.checkState(conflicts.isEmpty() || abort,
                "Cannot run operation while merge conflicts exist.");

        Optional<Ref> ref = command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        if (abort) {
            Preconditions.checkState(ref.isPresent(),
                    "Cannot abort. You are not in the middle of a revert process.");
            command(ResetOp.class).setMode(ResetMode.HARD)
                    .setCommit(Suppliers.ofInstance(ref.get().getObjectId())).call();
            command(UpdateRef.class).setDelete(true).setName(Ref.ORIG_HEAD).call();
            return true;
        } else if (continueRevert) {
            Preconditions.checkState(ref.isPresent(),
                    "Cannot continue. You are not in the middle of a revert process.");
            // Commit the manually-merged changes with the info of the commit that caused the
            // conflict
            applyNextCommit(false);
            // Commit files should already be prepared, so we do nothing else
        } else {
            Preconditions
                    .checkState(!ref.isPresent(),
                            "You are currently in the middle of a merge or rebase operation <ORIG_HEAD is present>.");

            getProgressListener().started();

            command(UpdateRef.class).setName(Ref.ORIG_HEAD)
                    .setNewValue(currHead.get().getObjectId()).call();

            // Here we prepare the files with the info about the commits to apply in reverse
            List<RevCommit> commitsToRevert = Lists.newArrayList();
            Repository repository = repository();
            for (ObjectId id : commits) {
                Preconditions.checkArgument(repository.commitExists(id),
                        "Commit was not found in the repository: " + id.toString());
                RevCommit commit = repository.getCommit(id);
                commitsToRevert.add(commit);
            }
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

    private File getRevertFolder() {
        URL dir = command(ResolveGeogigDir.class).call().get();
        File revertFolder = new File(dir.getFile(), "revert");
        if (!revertFolder.exists()) {
            Preconditions.checkState(revertFolder.mkdirs(), "Cannot create 'revert' folder");
        }
        return revertFolder;
    }

    private void createRevertCommitsInfoFiles(List<RevCommit> commitsToRebase) {
        File rebaseFolder = getRevertFolder();
        for (int i = 0; i < commitsToRebase.size(); i++) {

            File file = new File(rebaseFolder, Integer.toString(i + 1));
            try {
                Files.write(commitsToRebase.get(i).getId().toString(), file, Charsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create revert commits info files");
            }
        }
        File nextFile = new File(rebaseFolder, "next");
        try {
            Files.write("1", nextFile, Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create next revert commit info file");
        }

    }

    private boolean applyNextCommit(boolean useCommitChanges) {
        File rebaseFolder = getRevertFolder();
        File nextFile = new File(rebaseFolder, "next");
        Repository repository = repository();
        try {
            String idx = Files.readFirstLine(nextFile, Charsets.UTF_8);
            File commitFile = new File(rebaseFolder, idx);
            if (commitFile.exists()) {
                String commitId = Files.readFirstLine(commitFile, Charsets.UTF_8);
                RevCommit commit = repository.getCommit(ObjectId.valueOf(commitId));
                List<Conflict> conflicts = Lists.newArrayList();
                if (useCommitChanges) {
                    conflicts = applyRevertedChanges(commit);
                }
                if (createCommit && conflicts.isEmpty()) {
                    createCommit(commit);
                } else {
                    workingTree().updateWorkHead(repository.index().getTree().getId());
                    if (!conflicts.isEmpty()) {
                        // mark conflicted elements
                        command(ConflictsWriteOp.class).setConflicts(conflicts).call();

                        // created exception message
                        StringBuilder msg = new StringBuilder();
                        msg.append("error: could not apply ");
                        msg.append(commit.getId().toString().substring(0, 7));
                        msg.append(" " + commit.getMessage() + "\n");

                        for (Conflict conflict : conflicts) {
                            msg.append("CONFLICT: conflict in " + conflict.getPath() + "\n");
                        }

                        throw new RevertConflictsException(msg.toString());
                    }
                }
                commitFile.delete();
                int newIdx = Integer.parseInt(idx) + 1;
                Files.write(Integer.toString(newIdx), nextFile, Charsets.UTF_8);
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read/write revert commits index file");
        }

    }

    private List<Conflict> applyRevertedChanges(RevCommit commit) {

        ObjectId parentCommitId = ObjectId.NULL;
        if (commit.getParentIds().size() > 0) {
            parentCommitId = commit.getParentIds().get(0);
        }
        ObjectId parentTreeId = ObjectId.NULL;
        Repository repository = repository();
        if (repository.commitExists(parentCommitId)) {
            parentTreeId = repository.getCommit(parentCommitId).getTreeId();
        }

        // get changes (in reverse)
        Iterator<DiffEntry> reverseDiff = command(DiffTree.class).setNewTree(parentTreeId)
                .setOldTree(commit.getTreeId()).setReportTrees(false).call();

        ObjectId headTreeId = repository.getCommit(revertHead).getTreeId();
        final RevTree headTree = repository.getTree(headTreeId);

        ArrayList<Conflict> conflicts = new ArrayList<Conflict>();
        DiffEntry diff;
        while (reverseDiff.hasNext()) {
            diff = reverseDiff.next();
            if (diff.isAdd()) {
                // Feature was deleted
                Optional<NodeRef> node = command(FindTreeChild.class).setChildPath(diff.newPath())
                        .setIndex(true).setParent(headTree).call();
                // make sure it is still deleted
                if (node.isPresent()) {
                    conflicts.add(new Conflict(diff.newPath(), diff.oldObjectId(), node.get()
                            .objectId(), diff.newObjectId()));
                } else {
                    index().stage(getProgressListener(), Iterators.singletonIterator(diff), 1);
                }
            } else {
                // Feature was added or modified
                Optional<NodeRef> node = command(FindTreeChild.class).setChildPath(diff.oldPath())
                        .setIndex(true).setParent(headTree).call();
                ObjectId nodeId = node.get().getNode().getObjectId();
                // Make sure it wasn't changed
                if (node.isPresent() && nodeId.equals(diff.oldObjectId())) {
                    index().stage(getProgressListener(), Iterators.singletonIterator(diff), 1);
                } else {
                    // do not mark as conflict if reverting to the same feature currently in HEAD
                    if (!nodeId.equals(diff.newObjectId())) {
                        conflicts.add(new Conflict(diff.newPath(), diff.oldObjectId(), node.get()
                                .objectId(), diff.newObjectId()));
                    }
                }

            }

        }

        return conflicts;

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
        builder.setMessage("Revert '" + commit.getMessage() + "'\nThis reverts "
                + commit.getId().toString());
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
        index().updateStageHead(newTreeId);

    }

    private String resolveCommitter() {
        final String key = "user.name";
        Optional<String> name = command(ConfigGet.class).setName(key).call();

        checkState(
                name.isPresent(),
                "%s not found in config. Use geogig config [--global] %s <your name> to configure it.",
                key, key);

        return name.get();
    }

    private String resolveCommitterEmail() {
        final String key = "user.email";
        Optional<String> email = command(ConfigGet.class).setName(key).call();

        checkState(
                email.isPresent(),
                "%s not found in config. Use geogig config [--global] %s <your email> to configure it.",
                key, key);

        return email.get();
    }
}
