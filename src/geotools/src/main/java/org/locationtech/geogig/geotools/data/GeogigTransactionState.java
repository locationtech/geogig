/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.data;

import java.io.IOException;
import java.util.Iterator;

import javax.annotation.Nullable;

import org.geotools.data.Transaction;
import org.geotools.data.Transaction.State;
import org.geotools.data.store.ContentEntry;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.plumbing.DiffIndex;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.ConflictsException;
import org.locationtech.geogig.api.porcelain.NothingToCommitException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 *
 */
class GeogigTransactionState implements State {

    /** VERSIONING_COMMIT_AUTHOR */
    static final String VERSIONING_COMMIT_AUTHOR = "VersioningCommitAuthor";

    /** VERSIONING_COMMIT_MESSAGE */
    static final String VERSIONING_COMMIT_MESSAGE = "VersioningCommitMessage";

    private ContentEntry entry;

    private GeogigTransaction geogigTx;

    private Transaction tx;

    /**
     * @param entry
     */
    public GeogigTransactionState(ContentEntry entry) {
        this.entry = entry;
    }

    public Optional<GeogigTransaction> getGeogigTransaction() {
        return Optional.fromNullable(this.geogigTx);
    }

    @Override
    public void setTransaction(@Nullable final Transaction transaction) {
        Preconditions.checkArgument(!Transaction.AUTO_COMMIT.equals(transaction));

        if (transaction != null && this.tx != null) {
            throw new IllegalStateException(
                    "New transaction set without closing old transaction first.");
        }
        this.tx = transaction;

        if (transaction == null) {
            // Transaction.removeState has been called (during
            // transaction.close())
            if (this.geogigTx != null) {
                // throw new
                // IllegalStateException("Transaction is attempting to "
                // + "close a non committed or aborted geogig transaction");
                geogigTx.abort();
            }
            this.geogigTx = null;
        } else {
            if (this.geogigTx != null) {
                geogigTx.abort();
            }
            GeoGigDataStore dataStore = (GeoGigDataStore) entry.getDataStore();
            Context commandLocator = dataStore.getCommandLocator(this.tx);
            this.geogigTx = commandLocator.command(TransactionBegin.class).call();
            // checkout the working branch
            final String workingBranch = dataStore.getOrFigureOutBranch();
            this.geogigTx.command(CheckoutOp.class).setForce(true).setSource(workingBranch).call();
        }
    }

    @Override
    public void addAuthorization(String AuthID) throws IOException {
        // not required
    }

    @Override
    public void commit() throws IOException {
        Preconditions.checkState(this.geogigTx != null);
        /*
         * This follows suite with the hack set on GeoSever's
         * org.geoserver.wfs.Transaction.getDatastoreTransaction()
         */
        final Optional<String> txUserName = getTransactionProperty(VERSIONING_COMMIT_AUTHOR);
        final Optional<String> fullName = getTransactionProperty("fullname");
        final Optional<String> email = getTransactionProperty("email");

        final String author = fullName.isPresent() ? fullName.get() : txUserName.orNull();
        String commitMessage = getTransactionProperty(VERSIONING_COMMIT_MESSAGE).orNull();

        this.geogigTx.command(AddOp.class).call();
        try {
            CommitOp commitOp = this.geogigTx.command(CommitOp.class);
            if (txUserName != null) {
                commitOp.setAuthor(author, email.orNull());
            }
            if (commitMessage == null) {
                commitMessage = composeDefaultCommitMessage();
            }
            commitOp.setMessage(commitMessage);
            commitOp.call();
        } catch (NothingToCommitException nochanges) {
            // ok
        }

        try {
            this.geogigTx.setAuthor(author, email.orNull()).commit();
        } catch (ConflictsException e) {
            // TODO: how should this be handled?
            this.geogigTx.abort();
        }

        this.geogigTx = null;
    }

    private Optional<String> getTransactionProperty(final String propName) {
        Object property = this.tx.getProperty(propName);
        if (property instanceof String) {
            return Optional.of((String) property);
        }
        return Optional.absent();
    }

    private String composeDefaultCommitMessage() {
        Iterator<DiffEntry> indexDiffs = this.geogigTx.command(DiffIndex.class).call();
        int added = 0, removed = 0, modified = 0;
        StringBuilder msg = new StringBuilder();
        while (indexDiffs.hasNext()) {
            DiffEntry entry = indexDiffs.next();
            switch (entry.changeType()) {
            case ADDED:
                added++;
                break;
            case MODIFIED:
                modified++;
                break;
            case REMOVED:
                removed++;
                break;
            }
            if ((added + removed + modified) < 10) {
                msg.append("\n ").append(entry.changeType().toString().toLowerCase()).append(' ')
                        .append(entry.newPath() == null ? entry.oldName() : entry.newPath());
            }
        }
        int count = added + removed + modified;
        if (count > 10) {
            msg.append("\n And ").append(count - 10).append(" more changes.");
        }
        StringBuilder title = new StringBuilder();
        if (added > 0) {
            title.append("added ").append(added);
        }
        if (modified > 0) {
            if (title.length() > 0) {
                title.append(", ");
            }
            title.append("modified ").append(modified);
        }
        if (removed > 0) {
            if (title.length() > 0) {
                title.append(", ");
            }
            title.append("removed ").append(removed);
        }
        if (count > 0) {
            title.append(" features via unversioned legacy client.\n");
        }
        msg.insert(0, title);
        return msg.toString();
    }

    @Override
    public void rollback() throws IOException {
        Preconditions.checkState(this.geogigTx != null);
        this.geogigTx.abort();
        this.geogigTx = null;
    }

}