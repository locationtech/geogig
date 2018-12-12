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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.TransactionResolve;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CheckoutResult.Results;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeogigTransaction;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;

public @Builder @AllArgsConstructor @NoArgsConstructor class PRInitOp extends PRCommand<PR> {

    //@formatter:off
    private @NonNull Integer id;
    private URI remoteURI;
    private String remoteBranch;
    private String targetBranch;
    private String title;
    private String description;
    //@formatter:on

    public PRInitOp setId(int id) {
        this.id = id;
        return this;
    }

    protected @Override PR _call() {
        final Context liveContext = context();
        checkState(!(liveContext instanceof GeogigTransaction));

        final PR pr = getOrCreate(id);
        final GeogigTransaction txContext = pr.getTransaction(liveContext);
        try {
            final Ref liveTargetBranch = pr.resolveTargetBranch(liveContext);
            Ref prTargetBranch;
            try {
                prTargetBranch = pr.resolveTargetBranch(txContext);
            } catch (NoSuchElementException e) {
                prTargetBranch = setRef(txContext, pr.getTargetBranch(),
                        liveTargetBranch.getObjectId());
            }
            if (!liveTargetBranch.equals(prTargetBranch)) {
                setRef(txContext, prTargetBranch.getName(), liveTargetBranch.getObjectId());
            }
            final ObjectId commonAncestor = findCommonAncestor(pr, prTargetBranch);

            String originRef = pr.getOriginRef();
            String headRef = pr.getHeadRef();

            txContext.command(MergeOp.class).setAbort().call();
            txContext.command(UpdateRef.class).setName(pr.getMergeRef()).setDelete(true).call();

            // prepare originRef for PRPrepareOp to fetch onto it from the remote's issuer branch
            setRef(txContext, originRef, commonAncestor);
            // prepare refs/pull/<id>/head for PrPrepareOp to track where targetBranch were
            setRef(txContext, headRef, liveTargetBranch.getObjectId());

            // checkout the target branch inside the tx for the test merge to happen there.
            Results initCheckoutResult = txContext.command(CheckoutOp.class).setForce(true)
                    .setSource(prTargetBranch.getName()).call().getResult();
            Preconditions.checkState(Results.CHECKOUT_LOCAL_BRANCH.equals(initCheckoutResult));
            final Map<String, String> props = pr.toProperties();
            final String section = String.format("pr.%d", id);
            liveContext.configDatabase().putSection(section, props);

            return pr;
        } catch (Exception e) {
            txContext.abort();
            Throwables.throwIfUnchecked(e);
            throw new IllegalStateException(e);
        }
    }

    private ObjectId findCommonAncestor(PR pr, Ref targetBranch) {
        Ref issuerBranch;
        ObjectId commonAncestor;
        final Repository issuerRepo = pr.openRemote();
        try {
            issuerBranch = pr.resolveRemoteBranch(issuerRepo);

            commonAncestor = command(FindCommonAncestor.class)//
                    .setLeftId(targetBranch.getObjectId())//
                    .setLeftSource(graphDatabase())//
                    .setRightId(issuerBranch.getObjectId())//
                    .setRightSource(issuerRepo.graphDatabase())//
                    .call().orNull();
        } finally {
            issuerRepo.close();
        }
        checkArgument(commonAncestor != null,
                "Local branch %s and remote %s do not have a common ancestor", pr.getTargetBranch(),
                issuerBranch.getName());

        return commonAncestor;
    }

    private PR getOrCreate(@NonNull Integer id) {
        Optional<PR> existing = command(PRFindOp.class).setId(this.id).call();
        return prepare(existing.orElseGet(() -> build()));
    }

    private PR prepare(PR pr) {
        Preconditions.checkNotNull(pr.getTransactionId());
        Preconditions.checkNotNull(pr.getId());
        getProgressListener().setDescription("Initializing pull request " + pr);

        com.google.common.base.Optional<GeogigTransaction> tx = command(TransactionResolve.class)
                .setId(pr.getTransactionId()).call();
        if (!tx.isPresent()) {
            PRStatus status = command(PRHealthCheckOp.class).setRequest(pr).call();
            checkState(!status.isMerged(),
                    "Cannot re open pull request %s because it's already merged", pr.getId());
            // reinitialize a transaction, pr was closes
            final GeogigTransaction txContext = command(TransactionBegin.class).call();
            final UUID txId = txContext.getTransactionId();
            pr.setTransactionId(txId);
        }

        if (remoteURI != null)
            pr.setRemote(remoteURI);
        if (remoteBranch != null)
            pr.setRemoteBranch(remoteBranch);
        if (targetBranch != null)
            pr.setTargetBranch(targetBranch);
        if (title != null)
            pr.setTitle(title);
        if (description != null)
            pr.setDescription(description);
        return pr;
    }

    private PR build() {
        final GeogigTransaction txContext = command(TransactionBegin.class).call();
        final UUID txId = txContext.getTransactionId();
        final PR pr;
        pr = PR.builder()//
                .id(id)//
                .transactionId(txId).remote(remoteURI)//
                .remoteBranch(remoteBranch)//
                .targetBranch(targetBranch)//
                .title(title)//
                .description(description)//
                .build();
        getProgressListener().setDescription("Creating pull request " + pr);
        return pr;
    }

    private Ref setRef(final GeogigTransaction transaction, String refName, ObjectId value) {
        return transaction.command(UpdateRef.class).setName(refName).setNewValue(value).call()
                .get();
    }

}
