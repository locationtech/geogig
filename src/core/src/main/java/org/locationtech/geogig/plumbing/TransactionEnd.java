/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.porcelain.RebaseOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.storage.impl.TransactionRefDatabase.ChangedRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;

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

        boolean success = true;
        try {
            if (!cancel) {
                updateRefs();
            }
        } catch (ConflictsException e) {
            throw e;
        } catch (Exception e) {
            success = false;
        }

        // Erase old refs and remove transaction specific blobs
        transaction.close();

        // Success
        return success;
    }

    private void updateRefs() {
        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        final String currentBranch;
        if (currHead.isPresent() && currHead.get() instanceof SymRef) {
            currentBranch = ((SymRef) currHead.get()).getTarget();
        } else {
            currentBranch = "";
        }

        List<ChangedRef> changedRefs = getChangedRefs();
        // Lock the repository
        try {
            refDatabase().lock();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

        try {
            // Update refs
            for (ChangedRef change : changedRefs) {
                final String refName = change.name;
                if (!refName.startsWith(Ref.REFS_PREFIX)) {
                    continue;
                }
                final boolean isDelete = change.newValue == null;
                final boolean isNew = change.orignalValue == null;

                final ObjectId oldCommit = isNew ? null : ObjectId.valueOf(change.orignalValue);
                final ObjectId newCommit = isDelete ? null : ObjectId.valueOf(change.newValue);

                final Optional<Ref> currentRef = command(RefParse.class).setName(refName).call();
                final Ref updatedRef;

                if (currentRef.isPresent() && repositoryChanged(currentRef.get())) {
                    if (rebase) {
                        // Try to rebase
                        transaction.command(CheckoutOp.class).setSource(refName).setForce(true)
                                .setProgressListener(getProgressListener()).call();
                        transaction.command(RebaseOp.class)
                                .setUpstream(Suppliers.ofInstance(currentRef.get().getObjectId()))
                                .setProgressListener(getProgressListener()).call();

                        updatedRef = transaction.command(RefParse.class).setName(refName).call()
                                .get();
                    } else {
                        // sync transactions have to use merge to prevent divergent history
                        transaction.command(CheckoutOp.class).setSource(refName).setForce(true)
                                .setProgressListener(getProgressListener()).call();
                        try {
                            transaction.command(MergeOp.class)
                                    .setAuthor(authorName.orNull(), authorEmail.orNull())
                                    .addCommit(currentRef.get().getObjectId())
                                    .setProgressListener(getProgressListener()).call();
                        } catch (NothingToCommitException e) {
                            LOGGER.debug("Transaction merge for {} unnecessary. {}",
                                    currentRef.get().getName(), e.getMessage());
                            // The repo commit is already in our history, this is a fast
                            // forward.
                        }
                        updatedRef = transaction.command(RefParse.class).setName(refName).call()
                                .get();
                    }
                } else {
                    updatedRef = isDelete ? null : new Ref(refName, newCommit);
                }

                LOGGER.debug(String.format("commit %s %s -> %s", refName, oldCommit, newCommit));

                command(UpdateRef.class)//
                        .setName(refName)//
                        .setNewValue(updatedRef == null ? null : updatedRef.getObjectId())//
                        .setDelete(isDelete)//
                        .call();

                if (currentBranch.equals(refName)) {
                    // Update HEAD, WORK_HEAD and STAGE_HEAD
                    command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(refName).call();
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

    private List<ChangedRef> getChangedRefs() {
        return transaction.changedRefs();
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
