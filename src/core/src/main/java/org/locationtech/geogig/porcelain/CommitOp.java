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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.CleanRefsOp;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.plumbing.WriteTree2;
import org.locationtech.geogig.plumbing.merge.ReadMergeCommitMessageOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;

/**
 * Commits the staged changed in the index to the repository, creating a new commit pointing to the
 * new root tree resulting from moving the staged changes to the repository, and updating the HEAD
 * ref to the new commit object.
 * <p>
 * Like {@code git commit -a}, If the {@link #setAll(boolean) all} flag is set, first stages all the
 * changed objects in the index, but does not state newly created (unstaged) objects that are not
 * already staged.
 * </p>
 * 
 */
@Hookable(name = "commit")
public class CommitOp extends AbstractGeoGigOp<RevCommit> {

    /**
     * {@code null} == unset, non null = explicitly set, even if resolves to {@code null}
     */
    @Nullable
    private Optional<String> authorName;

    /**
     * {@code null} == unset, non null = explicitly set, even if resolves to {@code null}
     */
    @Nullable
    private Optional<String> authorEmail;

    private String message;

    private Long authorTimeStamp;

    private Long committerTimeStamp;

    private Integer committerTimeZoneOffset;

    private Integer authorTimeZoneOffset;

    private List<ObjectId> parents = new ArrayList<>();

    // like the -a option in git commit
    private boolean all;

    private boolean allowEmpty;

    @Nullable
    private String committerName;

    /**
     * {@code null} == unset, non null = explicitly set, even if resolves to {@code null}
     */
    @Nullable
    private Optional<String> committerEmail;

    private RevCommit commit;

    private boolean amend;

    private final List<String> pathFilters = Lists.newLinkedList();

    /**
     * If set, ignores other information for creating a commit and uses the passed one.
     * 
     * @param commit the commit to use
     * 
     * @return {@code this}
     */
    public CommitOp setCommit(final RevCommit commit) {
        this.commit = commit;
        return this;
    }

    /**
     * If set, overrides the author's name from the configuration
     * 
     * @param authorName the author's name
     * @param authorEmail the author's email
     * @return {@code this}
     */
    public CommitOp setAuthor(final @Nullable String authorName,
            @Nullable final String authorEmail) {
        this.authorName = Optional.fromNullable(authorName);
        this.authorEmail = Optional.fromNullable(authorEmail);
        return this;
    }

    /**
     * If set, overrides the committer's name from the configuration
     * 
     * @param committerName the committer's name
     * @param committerEmail the committer's email
     */
    public CommitOp setCommitter(String committerName, @Nullable String committerEmail) {
        checkNotNull(committerName);
        this.committerName = committerName;
        this.committerEmail = Optional.fromNullable(committerEmail);
        return this;
    }

    /**
     * Sets the {@link RevCommit#getMessage() commit message}.
     * 
     * @param message description of the changes to record the commit with.
     * @return {@code this}, to ease command chaining
     */
    public CommitOp setMessage(@Nullable final String message) {
        this.message = message;
        return this;
    }

    /**
     * Sets the {@link RevPerson#getTimestamp() timestamp} for the author of this commit, or if not
     * set defaults to the current system time at the time {@link #call()} is called.
     * 
     * @param timestamp commit timestamp, in milliseconds, as in {@link Date#getTime()}
     * @return {@code this}, to ease command chaining
     */
    public CommitOp setAuthorTimestamp(@Nullable final Long timestamp) {
        this.authorTimeStamp = timestamp;
        return this;
    }

    /**
     * Sets the {@link RevPerson#getTimestamp() timestamp} for the committer of this commit, or if
     * not set defaults to the current system time at the time {@link #call()} is called.
     * 
     * @param timestamp commit timestamp, in milliseconds, as in {@link Date#getTime()}
     * @return {@code this}, to ease command chaining
     */
    public CommitOp setCommitterTimestamp(@Nullable final Long timestamp) {
        this.committerTimeStamp = timestamp;
        return this;
    }

    /**
     * Sets the time zone offset of the author.
     * 
     * @param timeZoneOffset time zone offset of the author
     * @return {@code this}, to ease command chaining
     */
    public CommitOp setAuthorTimeZoneOffset(@Nullable final Integer timeZoneOffset) {
        this.authorTimeZoneOffset = timeZoneOffset;
        return this;
    }

    /**
     * Sets the time zone offset of the committer.
     * 
     * @param timeZoneOffset time zone offset of the committer
     * @return {@code this}, to ease command chaining
     */
    public CommitOp setCommitterTimeZoneOffset(@Nullable final Integer timeZoneOffset) {
        this.committerTimeZoneOffset = timeZoneOffset;
        return this;
    }

    /**
     * If {@code true}, tells {@link #call()} to stage all the unstaged changes that are not new
     * object before performing the commit.
     * 
     * @param all {@code true} to stage changes before commit, {@code false} to not do that.
     *        Defaults to {@code false}.
     * @return {@code this}, to ease command chaining
     */
    public CommitOp setAll(boolean all) {
        this.all = all;
        return this;
    }

    public CommitOp setPathFilters(@Nullable List<String> pathFilters) {
        this.pathFilters.clear();
        if (pathFilters != null) {
            this.pathFilters.addAll(pathFilters);
        }
        return this;
    }

    /**
     * @param parents parents to add
     * @return {@code this}
     */
    public CommitOp addParents(Collection<ObjectId> parents) {
        this.parents.addAll(parents);
        return this;
    }

    /**
     * Sets whether the operation should ammend the last commit instead of creating a new one
     * 
     * @param amend
     * @return
     */
    public CommitOp setAmend(boolean amend) {
        this.amend = amend;
        return this;
    }

    /**
     * Executes the commit operation.
     * 
     * @return the commit just applied, or {@code null} if
     *         {@code getProgressListener().isCanceled()}
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     * @throws NothingToCommitException if there are no staged changes by comparing the index
     *         staging tree and the repository HEAD tree.
     */
    @Override
    protected RevCommit _call() throws RuntimeException {
        String commitMessage = message;

        getProgressListener().started();
        float writeTreeProgress = 98f;
        if (all) {
            writeTreeProgress = 50f;
            AddOp op = command(AddOp.class);
            for (String st : pathFilters) {
                op.addPattern(st);
            }
            op.setUpdateOnly(true).setProgressListener(subProgress(48f)).call();
        }
        if (getProgressListener().isCanceled()) {
            return null;
        }

        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        checkState(currHead.isPresent(), "Repository has no HEAD, can't commit");
        final Ref headRef = currHead.get();
        checkState(headRef instanceof SymRef, //
                "HEAD is in a dettached state, cannot commit. Create a branch from it before committing");

        final String currentBranch = ((SymRef) headRef).getTarget();
        final ObjectId currHeadCommitId = headRef.getObjectId();

        Supplier<RevTree> oldRoot = resolveOldRoot();
        if (!currHeadCommitId.isNull()) {
            if (amend) {
                RevCommit headCommit = command(RevObjectParse.class).setObjectId(currHeadCommitId)
                        .call(RevCommit.class).get();
                parents.addAll(headCommit.getParentIds());
                if (commitMessage == null || commitMessage.isEmpty()) {
                    commitMessage = headCommit.getMessage();
                }
                RevTree commitTree = command(RevObjectParse.class)
                        .setObjectId(headCommit.getTreeId()).call(RevTree.class).get();
                oldRoot = Suppliers.ofInstance(commitTree);
            } else {
                parents.add(0, currHeadCommitId);
            }
        } else {
            Preconditions.checkArgument(!amend,
                    "Cannot amend. There is no previous commit to amend");
        }

        // additional operations in case we are committing after a conflicted merge
        final Optional<Ref> mergeHead = command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        if (mergeHead.isPresent()) {
            ObjectId mergeCommitId = mergeHead.get().getObjectId();
            if (!mergeCommitId.isNull()) {
                parents.add(mergeCommitId);
            }
            if (commitMessage == null) {
                commitMessage = command(ReadMergeCommitMessageOp.class).call();
            }
        }

        ObjectId newTreeId;
        {
            WriteTree2 writeTree = command(WriteTree2.class);
            writeTree.setOldRoot(oldRoot).setProgressListener(subProgress(writeTreeProgress));
            if (!pathFilters.isEmpty()) {
                writeTree.setPathFilter(pathFilters);
            }
            newTreeId = writeTree.call();
        }

        if (getProgressListener().isCanceled()) {
            return null;
        }

        final ObjectId currentRootTreeId = command(ResolveTreeish.class)
                .setTreeish(currHeadCommitId).call().or(RevTree.EMPTY_TREE_ID);
        if (currentRootTreeId.equals(newTreeId)) {
            if (!allowEmpty && !amend) {
                throw new NothingToCommitException("Nothing to commit after " + currHeadCommitId);
            }
            if (amend) {
                boolean hasMessage = message != null && !message.isEmpty();
                boolean hasTimestamp = committerTimeStamp != null;
                boolean hasCommitToReuse = commit != null;
                Preconditions.checkArgument(hasMessage || hasTimestamp || hasCommitToReuse,
                        "You must specify a new commit message, timestamp, or commit to reuse when amending a commit with no changes.");
            }
        }

        RevCommitBuilder builder = RevCommit.builder();
        if (this.commit == null) {
            builder.author(resolveAuthor())//
                    .authorEmail(resolveAuthorEmail())//
                    .committer(resolveCommitter())//
                    .committerEmail(resolveCommitterEmail())//
                    .message(commitMessage == null ? "" : commitMessage)//
                    .parentIds(parents)//
                    .treeId(newTreeId)//
                    .committerTimestamp(committerTimeStamp)//
                    .authorTimestamp(authorTimeStamp)//
                    .committerTimeZoneOffset(committerTimeZoneOffset)//
                    .authorTimeZoneOffset(authorTimeZoneOffset);
        } else {
            builder.init(commit);
            builder.parentIds(parents)//
                    .treeId(newTreeId)//
                    .committerTimestamp(committerTimeStamp)//
                    .committerTimeZoneOffset(committerTimeZoneOffset);
            if (commitMessage != null) {
                builder.message(commitMessage);
            }
        }

        final RevCommit commit = builder.build();
        if (getProgressListener().isCanceled()) {
            return null;
        }
        final ObjectStore objectDb = objectDatabase();
        objectDb.put(commit);
        final Optional<Ref> branchHead = command(UpdateRef.class).setName(currentBranch)
                .setNewValue(commit.getId()).setProgressListener(subProgress(1f)).call();

        checkState(commit.getId().equals(branchHead.get().getObjectId()));

        final Optional<Ref> newHead = command(UpdateSymRef.class).setName(Ref.HEAD)
                .setNewValue(currentBranch).call();

        checkState(currentBranch.equals(((SymRef) newHead.get()).getTarget()));

        Optional<ObjectId> treeId = command(ResolveTreeish.class)
                .setTreeish(branchHead.get().getObjectId()).call();
        checkState(treeId.isPresent());
        checkState(newTreeId.equals(treeId.get()));

        getProgressListener().setProgress(100f);
        getProgressListener().complete();

        command(CleanRefsOp.class).call();

        return commit;
    }

    private Supplier<RevTree> resolveOldRoot() {
        Supplier<RevTree> supplier = new Supplier<RevTree>() {
            @Override
            public RevTree get() {
                Optional<ObjectId> head = command(ResolveTreeish.class).setTreeish(Ref.HEAD).call();
                if (!head.isPresent() || head.get().isNull()) {
                    return RevTree.EMPTY;
                }
                return command(RevObjectParse.class).setObjectId(head.get()).call(RevTree.class)
                        .get();
            }
        };
        return Suppliers.memoize(supplier);
    }

    private String resolveCommitter() {
        if (committerName != null) {
            return committerName;
        }

        final String namekey = "user.name";

        String name = getClientData(namekey, String.class)
                .or(command(ConfigGet.class).setName(namekey).call()).orNull();

        checkState(name != null,
                "%s not found in config. Use geogig config [--global] %s <your name> to configure it.",
                namekey, namekey);

        return name;
    }

    @Nullable
    private String resolveCommitterEmail() {
        if (committerEmail != null) {
            return committerEmail.orNull();
        }

        final String emailkey = "user.email";

        String email = getClientData(emailkey, String.class)
                .or(command(ConfigGet.class).setName(emailkey).call()).orNull();

        checkState(email != null,
                "%s not found in config. Use geogig config [--global] %s <your email> to configure it.",
                emailkey, emailkey);

        return email;
    }

    private String resolveAuthor() {
        return authorName == null ? resolveCommitter() : authorName.orNull();
    }

    @Nullable
    private String resolveAuthorEmail() {
        // only use provided authorEmail if authorName was provided
        return authorName == null ? resolveCommitterEmail() : authorEmail.orNull();
    }

    /**
     * @param allowEmptyCommit whether to allow a commit that represents no changes over its parent
     * @return {@code this}
     */
    public CommitOp setAllowEmpty(boolean allowEmptyCommit) {
        this.allowEmpty = allowEmptyCommit;
        return this;
    }

}
