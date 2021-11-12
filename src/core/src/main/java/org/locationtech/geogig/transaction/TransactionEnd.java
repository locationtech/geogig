/* Copyright (c) 2013-2019 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.transaction;

import static java.lang.String.format;
import static org.locationtech.geogig.base.Preconditions.checkArgument;
import static org.locationtech.geogig.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.RefChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@Hookable(name = "transaction-end")
public class TransactionEnd extends AbstractGeoGigOp<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionEnd.class);

    private boolean cancel = false;

    private GeogigTransaction transaction = null;

    private boolean rebase = false;

    private Optional<String> authorName = Optional.empty();

    private Optional<String> authorEmail = Optional.empty();

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
        this.authorName = Optional.ofNullable(authorName);
        this.authorEmail = Optional.ofNullable(authorEmail);
        return this;
    }

    /**
     * Ends the current transaction by either committing the changes or discarding them depending on
     * whether cancel is true or not.
     * 
     * @return {@code null} - the operation either succeeds or fails
     * @throws ConflictsException
     */
    protected @Override Void _call() {
        checkState(!(context instanceof GeogigTransaction),
                "Cannot end a transaction within a transaction!");
        checkArgument(transaction != null, "No transaction was specified!");

        if (cancel) {
            log.debug("Cancelling transaction {}", transaction.getTransactionId());
            transaction.close();
            return null;
        }
        try {
            refDatabase().lock();// Lock the repository
            final Optional<Ref> currHead = geogig().refs().head();
            final Optional<Ref> currentBranch = currHead.filter(SymRef.class::isInstance)
                    .map(Ref::peel);

            final List<RefChange> changedRefs = transaction.changedRefs();

            UpdateRefs updateLiveRefs = command(UpdateRefs.class)
                    .setReason(format("Commit transaction %s", transaction.getTransactionId()));
            for (RefChange changeInTx : changedRefs) {
                if (!Ref.isChild(Ref.REFS_PREFIX, changeInTx.name())) {
                    continue;
                }
                log.debug("Handling {}", changeInTx);
                if (changeInTx.isDelete()) {
                    handleDeleted(currentBranch, changeInTx).ifPresent(updateLiveRefs::remove);
                } else if (changeInTx.isNew()) {
                    handleCreated(currentBranch, changeInTx).forEach(updateLiveRefs::add);
                } else {
                    handleUpdated(currentBranch, changeInTx).forEach(updateLiveRefs::add);
                }
            }

            updateLiveRefs.call();
            // TODO: What happens if there are unstaged or staged changes in the repository when
            // a transaction is committed?

            // Erase old refs and remove transaction specific blobs
            transaction.close();
        } catch (TimeoutException refDbLockTimeout) {
            throw new RuntimeException(refDbLockTimeout);
        } finally {
            // Unlock the repository
            refDatabase().unlock();
        }
        return null;
    }

    private Optional<Ref> handleDeleted(Optional<Ref> currentBranchOutsideTx, RefChange deleted) {
        // deleted refs, make sure not to delete the current branch
        if (currentBranchOutsideTx.isPresent()
                && currentBranchOutsideTx.get().getName().equals(deleted.name())) {
            log.warn("Not deleting current branch when committing transaction {}",
                    transaction.getTransactionId());
            return Optional.empty();
        }
        Optional<Ref> currValue = geogig().refs().find(deleted.name());
        if (currValue.isPresent() && !currValue.equals(deleted.oldValue())) {
            log.warn(
                    "Not deleting {} when committing transaction {}. Ref changed outside the transaction.",
                    deleted.name(), transaction.getTransactionId());
            return Optional.empty();

        }
        return currValue;
    }

    private List<Ref> handleCreated(Optional<Ref> currentBranchOutsideTx, RefChange newref) {
        if (currentBranchOutsideTx.isPresent()) {
            log.debug(
                    "Ref {} was created both inside and outside transaction {}, attempting a merge",
                    newref.name(), transaction.getTransactionId());
            return handleUpdated(currentBranchOutsideTx, newref);
        }

        return Collections.singletonList(newref.newValue().get());
    }

    private List<Ref> handleUpdated(Optional<Ref> currentBranchOutsideTx, RefChange change) {
        final String refName = change.name();
        checkArgument(Ref.isChild(Ref.REFS_PREFIX, refName));
        checkArgument(change.newValue().isPresent());

        Geogig tx = Geogig.of(transaction);

        final @Nullable Ref valueOutsideTx = geogig().refs().find(refName).orElse(null);
        final Ref valueInsideTx = change.newValue().get();
        final Ref finalValue;

        // force checkout target ref inside transaction
        tx.commands().checkout(refName).setForce(true).call();

        if (null == valueOutsideTx || valueOutsideTx.equals(change.oldValue().orElse(null))) {
            String logmsg = valueOutsideTx == null ? //
                    "Ref {} was deleted outside transaction {} and changed in transaction, restoring it"
                    : "Ref {} didn't change outside transaction {}";
            log.debug(logmsg, refName, transaction.getTransactionId());
            finalValue = valueInsideTx;
        } else if (rebase) {
            try {// Try to rebase
                tx.commands().rebase(valueOutsideTx.getObjectId())
                        .setProgressListener(getProgressListener()).call();
            } catch (ConflictsException ce) {
                log.warn(
                        "Conflicts rebasing {} {} on top of {}, commit aborted, resolve conflicts and retry.",
                        refName, valueInsideTx.getObjectId(), valueOutsideTx.getObjectId(), ce);
                throw ce;
            }
            finalValue = tx.refs().find(refName).get();
        } else {
            try { // sync transactions have to use merge to prevent divergent history
                tx.commands().merge(valueOutsideTx.getObjectId())
                        .setAuthor(authorName.orElse(null), authorEmail.orElse(null))
                        .setProgressListener(getProgressListener()).call();
            } catch (NothingToCommitException e) {
                LOGGER.debug("Transaction merge for {} unnecessary. {}", valueOutsideTx.getName(),
                        e.getMessage());
            }
            finalValue = tx.refs().find(refName).get();
        }
        List<Ref> updates;
        if (currentBranchOutsideTx.isPresent()
                && currentBranchOutsideTx.get().getName().equals(refName)) {
            updates = new ArrayList<>(4);
            updates.add(finalValue);
            // Update HEAD, WORK_HEAD and STAGE_HEAD
            updates.add(new SymRef(Ref.HEAD, finalValue));
            updates.add(new Ref(Ref.WORK_HEAD, finalValue.getObjectId()));
            updates.add(new Ref(Ref.STAGE_HEAD, finalValue.getObjectId()));
        } else {
            updates = Collections.singletonList(finalValue);
        }
        return updates;
    }
}
