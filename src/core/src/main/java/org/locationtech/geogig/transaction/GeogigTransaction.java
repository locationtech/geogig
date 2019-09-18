/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.transaction;

import static org.locationtech.geogig.model.Ref.TRANSACTIONS_PREFIX;
import static org.locationtech.geogig.model.Ref.append;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.DelegatingContext;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.StagingAreaImpl;
import org.locationtech.geogig.repository.impl.WorkingTreeImpl;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.common.base.Preconditions;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

import lombok.NonNull;

/**
 * Provides a method of performing concurrent operations on a single Geogig repository.
 * 
 * @see org.locationtech.geogig.transaction.TransactionBegin
 * @see org.locationtech.geogig.transaction.TransactionEnd
 */
public class GeogigTransaction extends DelegatingContext implements Context {

    private UUID transactionId;

    private final NamespaceRefDatabase transactionRefDatabase;

    private final NamespaceRefDatabase initialStateRefDatabase;

    private final StagingArea transactionStagingArea;

    private final WorkingTree transactionWorkTree;

    private TransactionBlobStore transactionBlobStore;

    private Optional<String> authorName = Optional.empty();

    private Optional<String> authorEmail = Optional.empty();

    /**
     * Constructs the transaction with the given ID and Context.
     * 
     * @param context the non transactional command locator
     * @param transactionId the id of the transaction
     */
    GeogigTransaction(Context context, UUID transactionId) {
        super(context);
        Preconditions.checkArgument(!(context instanceof GeogigTransaction));
        this.context = context;
        this.transactionId = transactionId;

        transactionStagingArea = new TransactionStagingArea(new StagingAreaImpl(this),
                transactionId);
        transactionWorkTree = new WorkingTreeImpl(this);
        transactionBlobStore = new TransactionBlobStoreImpl(
                (TransactionBlobStore) context.blobStore(), transactionId);

        final String txNs = GeogigTransaction.buildTransactionNamespace(transactionId);
        final String origNs = Ref.append(txNs, "orig");
        final String changedNs = Ref.append(txNs, "changed");

        RefDatabase refDatabase = context.refDatabase();
        initialStateRefDatabase = new NamespaceRefDatabase(refDatabase, origNs);
        transactionRefDatabase = new NamespaceRefDatabase(refDatabase, changedNs);
    }

    public static String buildTransactionNamespace(final UUID transactionId) {
        return append(TRANSACTIONS_PREFIX, transactionId.toString());
    }

    public void create() {
        RefDatabase refDatabase = context.refDatabase();
        List<Ref> allRefs = refDatabase.getAll();
        List<@NonNull Ref> origRefs = allRefs.stream().map(initialStateRefDatabase::toInternal)
                .collect(Collectors.toList());
        List<@NonNull Ref> txRefs = allRefs.stream().map(transactionRefDatabase::toInternal)
                .collect(Collectors.toList());

        UpdateRefs createTxRefs = context.command(UpdateRefs.class).setReason("transaction begin");
        origRefs.forEach(createTxRefs::add);
        txRefs.forEach(createTxRefs::add);
        createTxRefs.call();
    }

    public void close() {
        transactionStagingArea.conflictsDatabase().removeConflicts(null);
        transactionBlobStore.removeBlobs(transactionId.toString());
        transactionRefDatabase.close();
        initialStateRefDatabase.close();
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
        return transactionStagingArea;
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
        return transactionStagingArea != null ? transactionStagingArea.conflictsDatabase()
                : context.conflictsDatabase();
    }

    public @Override BlobStore blobStore() {
        return transactionBlobStore;
    }

    public List<RefChange> changedRefs() {
        Map<String, Ref> originals = initialStateRefDatabase.getAll().stream()
                .collect(Collectors.toMap(Ref::getName, r -> r));
        Map<String, Ref> changed = transactionRefDatabase.getAll().stream()
                .collect(Collectors.toMap(Ref::getName, r -> r));

        MapDifference<String, Ref> difference = Maps.difference(originals, changed);

        List<RefChange> changes = new ArrayList<>();
        // include all new refs
        difference.entriesOnlyOnRight()
                .forEach((k, v) -> changes.add(RefChange.of(v.getName(), null, v)));

        // include all changed refs, with the new values
        difference.entriesDiffering().values().forEach(d -> changes
                .add(RefChange.of(d.leftValue().getName(), d.leftValue(), d.rightValue())));

        // deleted refs
        difference.entriesOnlyOnLeft()
                .forEach((k, v) -> changes.add(RefChange.of(v.getName(), v, null)));
        return changes;
    }

    public @Override Context snapshot() {
        return this;
    }

}
