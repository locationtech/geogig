/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.dsl;

import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.ResolveCommit;
import org.locationtech.geogig.plumbing.ResolveTree;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.RebaseOp;
import org.locationtech.geogig.porcelain.ResetOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.Command;
import org.locationtech.geogig.repository.Context;

import com.google.common.base.Suppliers;

import lombok.NonNull;

/**
 * Commonly used commands
 */
public class Commands extends ObjectStores {

    public Commands(Context context) {
        super(context, context.objectDatabase());
    }

    public <T extends Command<?>> T command(@NonNull Class<T> commandClass) {
        return context.command(commandClass);
    }

    public @NonNull LogOp log() {
        return command(LogOp.class);
    }

    public @NonNull Stream<RevCommit> logCall() {
        Iterator<RevCommit> iterator = command(LogOp.class).call();
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL
                        | Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.DISTINCT),
                false);
    }

    public Optional<ObjectId> commonAncestor(@NonNull RevCommit leftCommit,
            @NonNull RevCommit rightCommit) {
        return command(FindCommonAncestor.class).setLeft(leftCommit).setRight(rightCommit).call();
    }

    public Optional<ObjectId> commonAncestor(@NonNull ObjectId leftCommit,
            @NonNull ObjectId rightCommit) {
        return command(FindCommonAncestor.class).setLeftId(leftCommit).setRightId(rightCommit)
                .call();
    }

    public ResetOp reset(@NonNull ObjectId commitId) {
        return command(ResetOp.class).setCommit(commitId);
    }

    public boolean reset(@NonNull ObjectId commitId, ResetMode mode, boolean clean) {
        return command(ResetOp.class).setCommit(commitId).setMode(mode).setClean(clean).call();
    }

    public Optional<RevCommit> resolveCommit(@NonNull ObjectId commitIsh) {
        return command(ResolveCommit.class).setCommitIsh(commitIsh).call();
    }

    public Optional<RevCommit> resolveCommit(@NonNull String commitIsh) {
        return command(ResolveCommit.class).setCommitIsh(commitIsh).call();
    }

    public Optional<RevTree> resolveTree(@NonNull ObjectId treeIsh) {
        return context.command(ResolveTree.class).setTreeIsh(treeIsh).call();
    }

    public Optional<RevTree> resolveTree(@NonNull String treeIsh) {
        return context.command(ResolveTree.class).setTreeIsh(treeIsh).call();
    }

    public void add() {
        command(AddOp.class).call();
    }

    public void add(@NonNull String path) {
        command(AddOp.class).addPattern(path).call();
    }

    public CheckoutOp checkout(@NonNull String refName) {
        return command(CheckoutOp.class).setSource(refName);
    }

    public RebaseOp rebase(@NonNull ObjectId upstreamCommitId) {
        return command(RebaseOp.class).setUpstream(Suppliers.ofInstance(upstreamCommitId));
    }

    public MergeOp merge(@NonNull ObjectId commitToMerge) {
        return command(MergeOp.class).addCommit(commitToMerge);
    }

    public MergeOp merge(@NonNull String commitIsh) {
        RevCommit commitToMerge = resolveCommit(commitIsh).orElseThrow(
                () -> new IllegalArgumentException("commit-ish not found: " + commitIsh));
        return command(MergeOp.class).addCommit(commitToMerge.getId());
    }

    public CommitOp commit(@NonNull String messageFormaat, Object... messageArgs) {
        String message = String.format(messageFormaat, messageArgs);
        return command(CommitOp.class).setMessage(message);
    }

    public BranchCreateOp branch(@NonNull String branchName) {
        return command(BranchCreateOp.class).setName(branchName);
    }
}
