/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.TransactionEnd;
import org.locationtech.geogig.api.porcelain.ConflictsException;
import org.locationtech.geogig.di.PluginDefaults;
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.DeduplicationService;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StagingDatabase;
import org.locationtech.geogig.storage.TransactionRefDatabase;
import org.locationtech.geogig.storage.TransactionStagingArea;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

/**
 * Provides a method of performing concurrent operations on a single Geogig repository.
 * 
 * @see org.locationtech.geogig.api.plumbing.TransactionBegin
 * @see org.locationtech.geogig.api.plumbing.TransactionEnd
 */
public class GeogigTransaction implements Context {

    private UUID transactionId;

    private Context injector;

    private final StagingArea transactionIndex;

    private final WorkingTree transactionWorkTree;

    private final TransactionRefDatabase transactionRefDatabase;

    private Optional<String> authorName = Optional.absent();

    private Optional<String> authorEmail = Optional.absent();

    /**
     * Constructs the transaction with the given ID and Injector.
     * 
     * @param locator the non transactional command locator
     * @param transactionId the id of the transaction
     */
    public GeogigTransaction(Context locator, UUID transactionId) {
        Preconditions.checkArgument(!(locator instanceof GeogigTransaction));
        this.injector = locator;
        this.transactionId = transactionId;

        transactionIndex = new TransactionStagingArea(new Index(this), transactionId);
        transactionWorkTree = new WorkingTree(this);
        transactionRefDatabase = new TransactionRefDatabase(locator.refDatabase(), transactionId);
    }

    public void create() {
        transactionRefDatabase.create();
    }

    public void close() {
        transactionRefDatabase.close();
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
    public StagingArea index() {
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
        T instance = injector.command(commandClass);
        instance.setContext(this);
        return instance;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append('[').append(transactionId)
                .append(']').toString();
    }

    public void commit() throws ConflictsException {
        injector.command(TransactionEnd.class).setAuthor(authorName.orNull(), authorEmail.orNull())
                .setTransaction(this).setCancel(false).setRebase(true).call();
    }

    public void commitSyncTransaction() throws ConflictsException {
        injector.command(TransactionEnd.class).setAuthor(authorName.orNull(), authorEmail.orNull())
                .setTransaction(this).setCancel(false).call();
    }

    public void abort() {
        injector.command(TransactionEnd.class).setTransaction(this).setCancel(true).call();
    }

    @Override
    public Platform platform() {
        return injector.platform();
    }

    @Override
    public ObjectDatabase objectDatabase() {
        return injector.objectDatabase();
    }

    @Override
    public StagingDatabase stagingDatabase() {
        return transactionIndex != null ? transactionIndex.getDatabase() : injector
                .stagingDatabase();
    }

    @Override
    public ConfigDatabase configDatabase() {
        return injector.configDatabase();
    }

    @Override
    public GraphDatabase graphDatabase() {
        return injector.graphDatabase();
    }

    @Override
    public Repository repository() {
        return injector.repository();
    }

    @Override
    public DeduplicationService deduplicationService() {
        return injector.deduplicationService();
    }

    @Override
    public PluginDefaults pluginDefaults() {
        return injector.pluginDefaults();
    }

    /**
     * The set of refs that have either changed since, or didn't exist at, the time the transaction
     * was created.
     */
    public ImmutableSet<Ref> getChangedRefs() {
        Set<String> changedRefNames = transactionRefDatabase.getChangedRefs();
        Set<Ref> changedRefs = new HashSet<Ref>();
        for (String name : changedRefNames) {
            Ref ref = this.command(RefParse.class).setName(name).call().get();
            changedRefs.add(ref);
        }
        return ImmutableSet.copyOf(changedRefs);
    }
}
