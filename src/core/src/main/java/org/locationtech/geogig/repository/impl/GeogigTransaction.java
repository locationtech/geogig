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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.TransactionEnd;
import org.locationtech.geogig.porcelain.ConflictsException;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.PluginDefaults;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.impl.TransactionBlobStore;
import org.locationtech.geogig.storage.impl.TransactionBlobStoreImpl;
import org.locationtech.geogig.storage.impl.TransactionRefDatabase;
import org.locationtech.geogig.storage.impl.TransactionRefDatabase.ChangedRef;
import org.locationtech.geogig.storage.impl.TransactionStagingArea;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

/**
 * Provides a method of performing concurrent operations on a single Geogig repository.
 * 
 * @see org.locationtech.geogig.plumbing.TransactionBegin
 * @see org.locationtech.geogig.plumbing.TransactionEnd
 */
public class GeogigTransaction implements Context {

    private UUID transactionId;

    private Context context;

    private final StagingArea transactionIndex;

    private final WorkingTree transactionWorkTree;

    private final TransactionRefDatabase transactionRefDatabase;

    private TransactionBlobStore transactionBlobStore;

    private Optional<String> authorName = Optional.absent();

    private Optional<String> authorEmail = Optional.absent();

    /**
     * Constructs the transaction with the given ID and Injector.
     * 
     * @param context the non transactional command locator
     * @param transactionId the id of the transaction
     */
    public GeogigTransaction(Context context, UUID transactionId) {
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
        transactionRefDatabase.create();
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
        this.authorName = Optional.fromNullable(authorName);
        this.authorEmail = Optional.fromNullable(authorEmail);
        return this;
    }

    /**
     * @return the transaction id of the transaction
     */
    public UUID getTransactionId() {
        return transactionId;
    }

    @Override
    public WorkingTree workingTree() {
        return transactionWorkTree;
    }

    @Override
    @Deprecated
    public StagingArea index() {
        return stagingArea();
    }

    @Override
    public StagingArea stagingArea() {
        return transactionIndex;
    }

    @Override
    public RefDatabase refDatabase() {
        return transactionRefDatabase;
    }

    /**
     * Finds and returns an instance of a command of the specified class.
     * 
     * @param commandClass the kind of command to locate and instantiate
     * @return a new instance of the requested command class, with its dependencies resolved
     */
    @Override
    public <T extends AbstractGeoGigOp<?>> T command(Class<T> commandClass) {
        T instance = context.command(commandClass);
        instance.setContext(this);
        return instance;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append('[').append(transactionId)
                .append(']').toString();
    }

    public void commit() throws ConflictsException {
        commit(DefaultProgressListener.NULL);
    }

    public void commit(ProgressListener listener) throws ConflictsException {
        context.command(TransactionEnd.class).setAuthor(authorName.orNull(), authorEmail.orNull())
                .setTransaction(this).setCancel(false).setRebase(true).setProgressListener(listener)
                .call();
    }

    public void commitSyncTransaction() throws ConflictsException {
        commitSyncTransaction(DefaultProgressListener.NULL);
    }

    public void commitSyncTransaction(ProgressListener listener) throws ConflictsException {
        context.command(TransactionEnd.class).setAuthor(authorName.orNull(), authorEmail.orNull())
                .setTransaction(this).setCancel(false).setProgressListener(listener).call();
    }

    public void abort() {
        context.command(TransactionEnd.class).setTransaction(this).setCancel(true).call();
    }

    @Override
    public Platform platform() {
        return context.platform();
    }

    @Override
    public ObjectDatabase objectDatabase() {
        return context.objectDatabase();
    }

    @Override
    public IndexDatabase indexDatabase() {
        return context.indexDatabase();
    }

    @Override
    public ConflictsDatabase conflictsDatabase() {
        return transactionIndex != null ? transactionIndex.conflictsDatabase()
                : context.conflictsDatabase();
    }

    @Override
    public BlobStore blobStore() {
        return transactionBlobStore;
    }

    @Override
    public ConfigDatabase configDatabase() {
        return context.configDatabase();
    }

    @Override
    public GraphDatabase graphDatabase() {
        return context.graphDatabase();
    }

    @Override
    public Repository repository() {
        return context.repository();
    }

    @Override
    public PluginDefaults pluginDefaults() {
        return context.pluginDefaults();
    }

    public List<ChangedRef> changedRefs() {
        return transactionRefDatabase.changedRefs();
    }

    /**
     * The set of refs that have either changed since, or didn't exist at, the time the transaction
     * was created.
     */
    public @Deprecated ImmutableSet<Ref> getChangedRefs() {
        Set<String> changedRefNames = transactionRefDatabase.getChangedRefs();
        Set<Ref> changedRefs = new HashSet<Ref>();
        for (String name : changedRefNames) {
            Ref ref = this.command(RefParse.class).setName(name).call().get();
            changedRefs.add(ref);
        }
        return ImmutableSet.copyOf(changedRefs);
    }

    @Override
    public Context snapshot() {
        return this;
    }

}
