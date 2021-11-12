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

import static org.locationtech.geogig.base.Preconditions.checkArgument;
import static org.locationtech.geogig.base.Preconditions.checkState;

import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.CheckRefFormat;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveCommit;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

import org.locationtech.geogig.base.Strings;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates a new head ref (branch) pointing to the specified tree-ish or the current HEAD if no
 * tree-ish was specified.
 * <p>
 */
@Slf4j
public class BranchCreateOp extends AbstractGeoGigOp<Ref> {

    private String branchName;

    private String commit_ish;

    private boolean checkout;

    private boolean orphan;

    private boolean force;

    private @Nullable String description;

    private @Nullable String remoteName, remoteBranch;

    /**
     * @param branchName the name of the branch to create, must not already exist
     */
    public BranchCreateOp setName(final String branchName) {
        this.branchName = branchName;
        return this;
    }

    /**
     * @param commit_ish either a branch ref or commit id where this branch starts at. If not set
     *        defaults to the current {@link Ref#HEAD HEAD}
     */
    public BranchCreateOp setSource(@Nullable String commit_ish) {
        this.commit_ish = commit_ish;
        return this;
    }

    /**
     * @param force true if the branch should overwrite an exisiting one with the same name, in case
     *        it exists
     */
    public BranchCreateOp setForce(boolean force) {
        this.force = force;
        return this;
    }

    /**
     * @param orphan {@code true} if the new branch shares no history with the current one, defaults
     *        to {@code false}
     */
    public BranchCreateOp setOrphan(boolean orphan) {
        this.orphan = orphan;
        return this;
    }

    public BranchCreateOp setRemoteName(String remote) {
        this.remoteName = remote;
        return this;
    }

    public BranchCreateOp setRemoteBranch(String branch) {
        this.remoteBranch = branch;
        return this;
    }

    public BranchCreateOp setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * @param checkout if {@code true}, in addition to creating the new branch, a {@link CheckoutOp
     *        checkout} operation will be performed against the newly created branch. If the check
     *        out failed for any reason the {@link CheckoutException} will be propagated back to the
     *        caller, although the branch is guaranteed to be created and could be retrieved through
     *        a {@link RefParse ref-parse} op.
     */
    public BranchCreateOp setAutoCheckout(boolean checkout) {
        this.checkout = checkout;
        return this;
    }

    protected Ref _call() {
        checkState(branchName != null, "branch name was not provided");
        final String branchRefPath;
        if (Ref.isChild(Ref.HEADS_PREFIX, branchName)) {
            branchRefPath = branchName;
        } else {
            branchRefPath = Ref.append(Ref.HEADS_PREFIX, branchName);
        }
        checkArgument(force || !command(RefParse.class).setName(branchRefPath).call().isPresent(),
                "A branch named '" + branchName + "' already exists.");

        command(CheckRefFormat.class).setThrowsException(true).setRef(branchRefPath).call();
        if (!Strings.isNullOrEmpty(remoteBranch)) {
            command(CheckRefFormat.class).setAllowOneLevel(true).setThrowsException(true)
                    .setRef(remoteBranch).call();
        }
        ObjectId branchOriginCommitId;
        String reason;
        if (orphan) {
            branchOriginCommitId = ObjectId.NULL;
            reason = "orphan branch";
        } else {
            final String branchOrigin = Optional.ofNullable(commit_ish).orElse(Ref.HEAD);
            StringBuilder reasonBuilder = new StringBuilder();
            branchOriginCommitId = resolveOriginCommitId(branchOrigin, reasonBuilder);
            reason = reasonBuilder.toString();
        }
        Optional<Ref> branchRef = command(UpdateRef.class).setName(branchRefPath)
                .setNewValue(branchOriginCommitId).setReason(reason).call();
        checkState(branchRef.isPresent());

        BranchConfig branchConfig = command(BranchConfigOp.class).setName(branchRefPath)
                .setRemoteName(remoteName).setRemoteBranch(remoteBranch).setDescription(description)
                .set();

        log.debug("Created branch {} {}", branchRef.get(), branchConfig);
        if (checkout) {
            command(CheckoutOp.class).setSource(branchRefPath).call();
        }
        return branchRef.get();
    }

    private ObjectId resolveOriginCommitId(String branchOrigin, StringBuilder reasonBuilder) {
        Optional<Ref> ref = command(RefParse.class).setName(branchOrigin).call();
        reasonBuilder.append("branch ");
        if (ref.isPresent()) {
            ObjectId commitId = ref.get().getObjectId();
            checkArgument(!commitId.isNull(),
                    branchOrigin + " has no commits yet, branch cannot be created.");
            reasonBuilder.append(ref.get().peel().getName());
            return commitId;
        }
        Optional<RevCommit> commit = command(ResolveCommit.class).setCommitIsh(branchOrigin).call();
        checkArgument(commit.isPresent(), branchOrigin + " does not resolve to a commit");
        reasonBuilder.append(branchOrigin);
        return commit.get().getId();
    }
}
