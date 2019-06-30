/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.DelegatingContext;
import org.locationtech.geogig.plumbing.TransactionEnd;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.impl.TransactionBlobStore;
import org.locationtech.geogig.storage.impl.TransactionBlobStoreImpl;
import org.locationtech.geogig.storage.impl.TransactionRefDatabase;
import org.locationtech.geogig.storage.impl.TransactionRefDatabase.ChangedRef;
import org.locationtech.geogig.storage.impl.TransactionStagingArea;

import com.google.common.base.Preconditions;

/**
 * Provides a method of performing concurrent operations on a single Geogig repository.
 * 
 * @see org.locationtech.geogig.plumbing.TransactionBegin
 * @see org.locationtech.geogig.plumbing.TransactionEnd
 */
public class GeogigTransaction extends DelegatingContext implements Context {

    private UUID transactionId;

    private final StagingArea transactionIndex;

    private final WorkingTree transactionWorkTree;

    private final TransactionRefDatabase transactionRefDatabase;

    private TransactionBlobStore transactionBlobStore;

    private Optional<String> authorName = Optional.empty();

    private Optional<String> authorEmail = Optional.empty();

    /**
     * Constructs the transaction with the given ID and Injector.
     * 
     * @param context the non transactional command locator
     * @param transactionId the id of the transaction
     */
    public GeogigTransaction(Context context, UUID transactionId) {
        super(context);
        Preconditions.checkArgument(!(context instanceof GeogigTransaction));
        this.context = context;
        this.transactionId = transactionId;

        transactionIndex = new TransactionStagingArea(new StagingAreaImpl(this), transactionId);
        transactionWorkTree = new WorkingTreeImpl(this);
        transactionRefDatabase = new TransactionRefDatabase(context.refDatabase(), transactionId);
        transactionBlobStore = new TransactionBlobStoreImpl(
                (TransactionBlobStore) context.blobStore(), transactionId);
    }

    public void create() {
        transactionRefDatabase.open();
    }

    public void close() {
        transactionBlobStore.removeBlobs(transactionId.toString());
        transactionRefDatabase.close();
        transactionIndex.conflictsDatabase().removeConflicts(null);
    }

    /**
     * 
     * @param authorName name of the author of this transaction
     * @param authorEmail email of the author of this transaction
     * @return {@code this}
     */
    public GeogigTransaction setAuthor(@Nullable String authorName, @Nullable String authorEmail) {
        this.authorName = Optional.ofNullable(authorName);
        this.authorEmail = Optional.ofNullable(authorEmail);
        return this;
    }

    /**
     * @return the transaction id of the transaction
     */
    public UUID getTransactionId() {
        return transactionId;
    }

    public @Override WorkingTree workingTree() {
        return transactionWorkTree;
    }

    public @Override StagingArea stagingArea() {
        return transactionIndex;
    }

    public @Override RefDatabase refDatabase() {
        return transactionRefDatabase;
    }

    public @Override String toString() {
        return new StringBuilder(getClass().getSimpleName()).append('[').append(transactionId)
                .append(']').toString();
    }

    public void commit() throws ConflictsException {
        commit(DefaultProgressListener.NULL);
    }

    public void commit(ProgressListener listener) throws ConflictsException {
        context.command(TransactionEnd.class)
                .setAuthor(authorName.orElse(null), authorEmail.orElse(null)).setTransaction(this)
                .setCancel(false).setRebase(true).setProgressListener(listener).call();
    }

    public void commitSyncTransaction() throws ConflictsException {
        commitSyncTransaction(DefaultProgressListener.NULL);
    }

    public void commitSyncTransaction(ProgressListener listener) throws ConflictsException {
        context.command(TransactionEnd.class)
                .setAuthor(authorName.orElse(null), authorEmail.orElse(null)).setTransaction(this)
                .setCancel(false).setProgressListener(listener).call();
    }

    public void abort() {
        context.command(TransactionEnd.class).setTransaction(this).setCancel(true).call();
    }

    public @Override ConflictsDatabase conflictsDatabase() {
        return transactionIndex != null ? transactionIndex.conflictsDatabase()
                : context.conflictsDatabase();
    }

    public @Override BlobStore blobStore() {
        return transactionBlobStore;
    }

    public List<ChangedRef> changedRefs() {
        return transactionRefDatabase.changedRefs();
    }

    public @Override Context snapshot() {
        return this;
    }

}
