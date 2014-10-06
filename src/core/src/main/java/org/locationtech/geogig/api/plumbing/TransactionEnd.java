/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.hooks.Hookable;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.api.porcelain.NothingToCommitException;
import org.locationtech.geogig.api.porcelain.RebaseConflictsException;
import org.locationtech.geogig.api.porcelain.RebaseOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

/**
 * Finishes a {@link GeogigTransaction} by merging all refs that have been changed.
 * <p>
 * If a given ref has not been changed on the repsoitory, it will simply update the repository's ref
 * to the value of the transaction ref.
 * <p>
 * If the repository ref was updated while the transaction occurred, the changes will be brought
 * together via a merge or rebase operation and the new ref will be updated to the result.
 * 
 * @see GeogigTransaction
 */
@Hookable(name = "transaction-end")
public class TransactionEnd extends AbstractGeoGigOp<Boolean> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionEnd.class);

    private boolean cancel = false;

    private GeogigTransaction transaction = null;

    private boolean rebase = false;

    private Optional<String> authorName = Optional.absent();

    private Optional<String> authorEmail = Optional.absent();

    /**
     * @param cancel if {@code true}, the transaction will be cancelled, otherwise it will be
     *        committed
     * @return {@code this}
     */
    public TransactionEnd setCancel(boolean cancel) {
        this.cancel = cancel;
        return this;
    }

    /**
     * @param transaction the transaction to end
     * @return {@code this}
     */
    public TransactionEnd setTransaction(GeogigTransaction transaction) {
        this.transaction = transaction;
        return this;
    }

    /**
     * @param rebase use rebase instead of merge when completing the transaction
     * @return {@code this}
     */
    public TransactionEnd setRebase(boolean rebase) {
        this.rebase = rebase;
        return this;
    }

    /**
     * @param authorName the author of the transaction to use for merge commits
     * @param authorEmail the email of the transaction author to use for merge commits
     * @return {@code this}
     */
    public TransactionEnd setAuthor(@Nullable String authorName, @Nullable String authorEmail) {
        this.authorName = Optional.fromNullable(authorName);
        this.authorEmail = Optional.fromNullable(authorEmail);
        return this;
    }

    /**
     * Ends the current transaction by either committing the changes or discarding them depending on
     * whether cancel is true or not.
     * 
     * @return Boolean - true if the transaction was successfully closed
     */
    @Override
    protected Boolean _call() {
        Preconditions.checkState(!(context instanceof GeogigTransaction),
                "Cannot end a transaction within a transaction!");
        Preconditions.checkArgument(transaction != null, "No transaction was specified!");

        try {
            if (!cancel) {
                updateRefs();
            }
        } finally {
            // Erase old refs
            transaction.close();
        }

        // Success
        return true;
    }

    private void updateRefs() {
        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        final String currentBranch;
        if (currHead.isPresent() && currHead.get() instanceof SymRef) {
            currentBranch = ((SymRef) currHead.get()).getTarget();
        } else {
            currentBranch = "";
        }

        ImmutableSet<Ref> changedRefs = getChangedRefs();
        // Lock the repository
        try {
            refDatabase().lock();
        } catch (TimeoutException e) {
            Throwables.propagate(e);
        }

        try {
            // Update refs
            for (Ref ref : changedRefs) {
                if (!ref.getName().startsWith(Ref.REFS_PREFIX)) {
                    continue;
                }
                Ref updatedRef = ref;

                Optional<Ref> repoRef = command(RefParse.class).setName(ref.getName()).call();
                if (repoRef.isPresent() && repositoryChanged(repoRef.get())) {
                    if (rebase) {
                        // Try to rebase
                        transaction.command(CheckoutOp.class).setSource(ref.getName())
                                .setForce(true).call();
                        try {
                            transaction.command(RebaseOp.class)
                                    .setUpstream(Suppliers.ofInstance(repoRef.get().getObjectId()))
                                    .call();
                        } catch (RebaseConflictsException e) {
                            Throwables.propagate(e);
                        }
                        updatedRef = transaction.command(RefParse.class).setName(ref.getName())
                                .call().get();
                    } else {
                        // sync transactions have to use merge to prevent divergent history
                        transaction.command(CheckoutOp.class).setSource(ref.getName())
                                .setForce(true).call();
                        try {
                            transaction.command(MergeOp.class)
                                    .setAuthor(authorName.orNull(), authorEmail.orNull())
                                    .addCommit(Suppliers.ofInstance(repoRef.get().getObjectId()))
                                    .call();
                        } catch (NothingToCommitException e) {
                            // The repo commit is already in our history, this is a fast
                            // forward.
                        }
                        updatedRef = transaction.command(RefParse.class).setName(ref.getName())
                                .call().get();
                    }
                }

                LOGGER.debug(String.format("commit %s %s -> %s", ref.getName(), ref.getObjectId(),
                        updatedRef.getObjectId()));

                command(UpdateRef.class).setName(ref.getName())
                        .setNewValue(updatedRef.getObjectId()).call();

                if (currentBranch.equals(ref.getName())) {
                    // Update HEAD, WORK_HEAD and STAGE_HEAD
                    command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(ref.getName()).call();
                    command(UpdateRef.class).setName(Ref.WORK_HEAD)
                            .setNewValue(updatedRef.getObjectId()).call();
                    command(UpdateRef.class).setName(Ref.STAGE_HEAD)
                            .setNewValue(updatedRef.getObjectId()).call();
                }
            }

            // TODO: What happens if there are unstaged or staged changes in the repository when
            // a transaction is committed?
        } finally {
            // Unlock the repository
            refDatabase().unlock();
        }
    }

    private ImmutableSet<Ref> getChangedRefs() {
        ImmutableSet<Ref> changedRefs = transaction.getChangedRefs();
        return changedRefs;
    }

    private boolean repositoryChanged(Ref ref) {
        Optional<Ref> transactionOriginal = transaction.command(RefParse.class)
                .setName(ref.getName().replace("refs/", "orig/refs/")).call();
        if (transactionOriginal.isPresent()) {
            return !ref.getObjectId().equals(transactionOriginal.get().getObjectId());
        }
        // Ref was created in transaction and on the repo
        return true;
    }
}
