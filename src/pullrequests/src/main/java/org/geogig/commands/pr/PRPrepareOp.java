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

import java.util.Optional;

import javax.annotation.Nullable;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.porcelain.BranchResolveOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.remotes.PullOp;
import org.locationtech.geogig.remotes.PullResult;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Pulls from the remote (issuer) repository branch onto the pull request's {@link PR#getOriginRef()
 * origin ref}, and them attempts the merge onto the PR's {@link PR#getHeadRef() head ref}.
 * <p>
 * 1. Reset target branch
 * <p>
 */
public @NoArgsConstructor @CanRunDuringConflict class PRPrepareOp extends PRCommand<PRStatus> {

    private @NonNull Integer requestId;

    private @Nullable String message;

    public PRPrepareOp setId(int prId) {
        this.requestId = prId;
        return this;
    }

    public PRPrepareOp setMessage(String message) {
        this.message = message;
        return this;
    }

    protected @Override PRStatus _call() {
        try {
            return prepareInternal();
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        }
    }

    private void ckeckAborted() throws InterruptedException {
        if (isCancelled()) {
            throw new InterruptedException("Pull request prepare aborted by user");
        }
    }

    /**
     * @return
     * @throws InterruptedException
     */
    private PRStatus prepareInternal() throws InterruptedException {
        final ProgressListener progress = getProgressListener();
        PRStatus preStatus = command(PRHealthCheckOp.class).setId(requestId)
                .setProgressListener(progress).call();

        final PR pr = preStatus.getRequest();

        progress.setDescription("Preparing pull request #%s '%s'", pr.getId(), pr.getTitle());

        final GeogigTransaction transaction = pr.getTransaction(context());
        final String testMergeRef = pr.getMergeRef();

        if (!preStatus.isRemoteBranchBehind() && !preStatus.isTargetBranchBehind()) {
            boolean conflictsAlreadyChecked = preStatus.isConflicted();
            boolean testMergeDone = preStatus.getMergeCommit().isPresent();
            if (conflictsAlreadyChecked || testMergeDone) {
                if (testMergeDone && this.message != null) {
                    RevCommit ammended = transaction.command(CommitOp.class).setAmend(true)
                            .setMessage(message).call();
                    setRef(transaction, testMergeRef, ammended.getId());
                    preStatus = preStatus.withMergeCommit(Optional.of(ammended.getId()));
                }
                progress.setDescription("Pull request up to date, returning current status");
                return preStatus;
            }
        }

        if (preStatus.isTargetBranchBehind()) {
            // reset pr target branch and pr head ref, and discard any previous conflict
            command(PRInitOp.class).setId(pr.getId()).call();
        } else {
            transaction.command(MergeOp.class).setAbort().call();
        }
        ckeckAborted();

        // pull onto target branch, fetching to refs/pull/<id>/origin
        final Remote remote = pr.buildRemote();

        MergeScenarioReport mergeScenarioReport = null;
        long conflictCount = 0;
        try {
            int commitsBehind = preStatus.getCommitsBehindRemoteBranch();
            if (commitsBehind > 0) {
                progress.setDescription("Pulling %,d commits onto %s", commitsBehind,
                        pr.getTargetBranch());
            } else {
                progress.setDescription("Merging remote branch %s onto %s", pr.getRemoteBranch(),
                        pr.getTargetBranch());
            }

            PullResult pullResult = pull(transaction, remote);
            ObjectId testMergeCommitID;
            if (isNothingToCommit(pullResult)) {
                if (message != null) {
                    transaction.command(CommitOp.class).setAmend(true).setMessage(message)
                            .setProgressListener(progress).call();
                }
                testMergeCommitID = transaction.command(RefParse.class).setName(Ref.HEAD).call()
                        .get().getObjectId();
            } else {
                progress.setDescription("Merge successful");
                MergeReport mergeReport = pullResult.getMergeReport().get();
                mergeScenarioReport = mergeReport.getReport().orNull();
                RevCommit commit = mergeReport.getMergeCommit();
                // update mergeRef
                testMergeCommitID = commit.getId();
            }
            setRef(transaction, testMergeRef, testMergeCommitID);
        } catch (MergeConflictsException mc) {
            progress.setDescription(
                    "Unable to create merge commit for pull request, there are merge conflicts");
            // delete mergeRef
            transaction.command(UpdateRef.class).setName(testMergeRef).setDelete(true).call();
            mergeScenarioReport = mc.getReport();
            conflictCount = mergeScenarioReport.getConflicts();
        }

        Optional<Ref> mergeCommitRef = pr.resolveMergeRef(transaction);

        progress.setProgressIndicator(null);
        ckeckAborted();
        PRStatus prPrepareResult = PRStatus.builder()//
                .request(pr)//
                .numConflicts(conflictCount)//
                .commitsBehindTargetBranch(0)//
                .commitsBehindRemoteBranch(0)//
                .mergeCommit(mergeCommitRef.map(r -> r.getObjectId()))
                .report(Optional.ofNullable(mergeScenarioReport))//
                .affectedLayers(preStatus.getAffectedLayers())//
                .build();

        return prPrepareResult;
    }

    private boolean isNothingToCommit(PullResult pullResult) {
        return pullResult.getNewRef().equals(pullResult.getOldRef());
    }

    private PullResult pull(final GeogigTransaction context, final Remote remote)
            throws MergeConflictsException {

        String fetchSpec = remote.getFetchSpec();
        PullResult pullResult = context.command(PullOp.class)//
                .setAll(false)//
                .setIncludeIndexes(true)//
                .setRemote(remote)//
                .addRefSpec(fetchSpec)//
                .setNoFastForward(true)//
                .setMessage(message)//
                .setProgressListener(getProgressListener())//
                .call();

        return pullResult;
    }

    private Ref resolveCurrentBranch(GeogigTransaction prtx) {
        return prtx.command(BranchResolveOp.class).call()
                .orElseThrow(() -> new IllegalStateException("Can't resolve current branch"));
    }

    private void setRef(final GeogigTransaction transaction, String refName, ObjectId value) {
        transaction.command(UpdateRef.class).setName(refName).setNewValue(value).call();
    }
}
