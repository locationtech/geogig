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

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import lombok.NonNull;

public class PRMergeOp extends PRCommand<PRStatus> {

    private @NonNull Integer prId;

    private @Nullable String message;

    public PRMergeOp setId(int prId) {
        this.prId = prId;
        return this;
    }

    public PRMergeOp setMessage(String message) {
        this.message = message;
        return this;
    }

    protected @Override PRStatus _call() throws NothingToCommitException, MergeConflictsException {
        final PRStatus preStatus = command(PRPrepareOp.class).setId(prId).setMessage(message)
                .setProgressListener(getProgressListener()).call();
        
        PR pr = preStatus.getRequest();

        final Optional<ObjectId> testMerge = preStatus.getMergeCommit();
        Ref liveTarget = pr.resolveTargetBranch(context());
        boolean nothingToCommit = !preStatus.isConflicted() && !preStatus.isRemoteBranchBehind()
                && !preStatus.isTargetBranchBehind() && testMerge.isPresent()
                && testMerge.get().equals(liveTarget.getObjectId());
        if (nothingToCommit) {
            throw new NothingToCommitException("Pull request does not represent any changes");
        }

        if (!preStatus.isConflicted() && preStatus.getMergeCommit().isPresent()) {
            GeogigTransaction transaction = pr.getTransaction(context());
            transaction.commitSyncTransaction(getProgressListener());// uses merge instead of rebase
        }
        PRStatus postStatus = command(PRHealthCheckOp.class).setRequest(pr).call();
        postStatus = postStatus.withReport(preStatus.getReport());
        if (postStatus.isConflicted()) {
            throw new MergeConflictsException(
                    String.format("Unable to merge pull request, %,d conflict(s) remaining",
                            postStatus.getNumConflicts()));
        }
        return postStatus;
    }
}
