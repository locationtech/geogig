/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.FindChangedTrees;
import org.locationtech.geogig.plumbing.merge.ConflictsCountOp;
import org.locationtech.geogig.porcelain.BranchResolveOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;

import lombok.NonNull;

/**
 * Checks on the status of a pull request and returns whether there are conflicts and whether either
 * branch diverged from when it was created/last updated
 */
public @CanRunDuringConflict class PRHealthCheckOp extends PRCommand<PRStatus> {

    private @NonNull Integer id;

    private PR request;

    public PRHealthCheckOp setId(int id) {
        this.id = id;
        return this;
    }

    public PRHealthCheckOp setRequest(PR pr) {
        this.request = pr;
        return this;
    }

    protected @Override PRStatus _call() {
        final Context liveContext = context();
        checkState(!(liveContext instanceof GeogigTransaction));
        final ProgressListener progress = getProgressListener();
        progress.setProgressIndicator(p -> "Checking pull request status...");

        final PR pr = request != null ? request : command(PRFindOp.class).id(id).getOrFail();
        final Optional<GeogigTransaction> tx = pr.tryGetTransaction(liveContext);
        if (!tx.isPresent()) {
            Ref headRef = pr.resolveHeadRef(liveContext);
            Ref originRef = pr.resolveOriginRef(liveContext);
            List<String> affectedLayers = findAffectedLayers(headRef.getObjectId(),
                    originRef.getObjectId(), liveContext, liveContext);
            Optional<Ref> mergeRef = pr.resolveMergeRef(liveContext);
            boolean merged = mergeRef.isPresent();
            Optional<ObjectId> mergeCommit = mergeRef.map(r -> r.getObjectId());
            return PRStatus.builder()//
                    .request(pr)//
                    .closed(true)//
                    .merged(merged)//
                    .mergeCommit(mergeCommit)//
                    .report(Optional.empty())//
                    .affectedLayers(affectedLayers)//
                    .build();
        }

        final GeogigTransaction transaction = tx.get();
        final Ref prHeadRef = pr.resolveHeadRef(transaction);
        final Ref liveTargetRef = pr.resolveTargetBranch(liveContext);

        {
            final Ref txBranch = transaction.command(BranchResolveOp.class).call().get();
            Preconditions.checkState(liveTargetRef.getName().equals(txBranch.getName()),
                    "expected current branch %s, got %s", liveTargetRef.getName(),
                    txBranch.getName());
        }

        final CompletableFuture<Long> numConflicts;
        final CompletableFuture<Integer> commitsBehindRemote;
        final CompletableFuture<Integer> commitsBehindTarget;
        final CompletableFuture<List<String>> affectedLayers;

        numConflicts = supplyAsync(() -> transaction.command(ConflictsCountOp.class).call());

        final Repository remoteRepo = pr.openRemote();
        try {
            final Ref localOriginRef = pr.resolveOriginRef(transaction);
            final Ref liveOriginRef = pr.resolveRemoteBranch(remoteRepo);

            commitsBehindRemote = countMissingCommits(localOriginRef, liveOriginRef,
                    remoteRepo.context());
            commitsBehindTarget = countMissingCommits(prHeadRef, liveTargetRef, liveContext);
            affectedLayers = supplyAsync(() -> findAffectedLayers(liveOriginRef.getObjectId(),
                    liveTargetRef.getObjectId(), remoteRepo.context(), context()));

            Optional<Ref> mergedRef = pr.resolveMergeRef(transaction);
            Optional<ObjectId> mergeCommit = mergedRef.map(r -> r.getObjectId());

            CompletableFuture
                    .allOf(numConflicts, commitsBehindRemote, commitsBehindTarget, affectedLayers)
                    .join();

            progress.setProgressIndicator(null);
            return PRStatus.builder()//
                    .request(pr)//
                    .numConflicts(numConflicts.join())//
                    .mergeCommit(mergeCommit)//
                    .commitsBehindRemoteBranch(commitsBehindRemote.join())//
                    .commitsBehindTargetBranch(commitsBehindTarget.join())//
                    .report(Optional.empty())//
                    .affectedLayers(affectedLayers.join())//
                    .build();
        } finally {
            remoteRepo.close();
        }
    }

    private List<String> findAffectedLayers(ObjectId leftRef, ObjectId rightRef,
            Context leftContext, Context rightContext) {

        List<DiffEntry> entries = command(FindChangedTrees.class)//
                .setOldTreeIsh(leftRef)//
                .setLeftSource(leftContext.objectDatabase())//
                .setNewTreeIsh(rightRef)//
                .setRightSource(rightContext.objectDatabase())//
                .call();

        return entries.stream().map(e -> e.path()).collect(Collectors.toList());
    }

    static CompletableFuture<Integer> countMissingCommits(Ref oldTip, Ref newTip, Context context) {

        if (oldTip.getObjectId().equals(newTip.getObjectId())) {
            return CompletableFuture.completedFuture(Integer.valueOf(0));
        }

        return CompletableFuture.supplyAsync(() -> {
            Iterator<RevCommit> missingCommits = context.command(LogOp.class)
                    .setUntil(newTip.getObjectId()).setSince(oldTip.getObjectId()).call();
            int commitsBehind = Iterators.size(missingCommits);
            return commitsBehind;
        });
    }

}
