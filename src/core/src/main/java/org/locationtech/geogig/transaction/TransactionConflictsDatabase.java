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

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

/**
 * A {@link ConflictsDatabase} decorator for a specific {@link GeogigTransaction transaction}.
 * <p>
 * This decorator creates a transaction specific namespace under the
 * {@code transactions/<transaction id>} path, and maps all query and storage methods to that
 * namespace.
 * 
 * @see GeogigTransaction
 * @see TransactionBegin
 * @see TransactionEnd
 */
class TransactionConflictsDatabase implements ConflictsDatabase {

    private final ConflictsDatabase database;

    private final String txNamespace;

    /**
     * Constructs a new {@code TransactionStagingDatabase}.
     * 
     * @param database the original conflicts database
     * @param transactionId the transaction id
     */
    public TransactionConflictsDatabase(final ConflictsDatabase database,
            final UUID transactionId) {
        this.database = database;
        this.txNamespace = transactionId.toString();
    }

    public @Override void open() {
        database.open();
    }

    public @Override void close() {
        database.close();
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    public @Override boolean hasConflicts(String namespace) {
        return database.hasConflicts(txNamespace);
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    public @Override Optional<Conflict> getConflict(@Nullable String namespace, String path) {
        return database.getConflict(txNamespace, path);
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    public @Override void addConflict(@Nullable String namespace, Conflict conflict) {
        database.addConflict(txNamespace, conflict);
    }

    public @Override void addConflicts(@Nullable String namespace, Iterable<Conflict> conflicts) {
        database.addConflicts(txNamespace, conflicts);
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    public @Override void removeConflict(@Nullable String namespace, String path) {
        database.removeConflict(txNamespace, path);
    }

    public @Override void removeConflicts(@Nullable String namespace, Iterable<String> paths) {
        database.removeConflicts(txNamespace, paths);
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    public @Override void removeConflicts(@Nullable String namespace) {
        database.removeConflicts(txNamespace);
    }

    public @Override Iterator<Conflict> getByPrefix(@Nullable String namespace,
            @Nullable String prefixFilter) {

        return database.getByPrefix(txNamespace, prefixFilter);
    }

    public @Override long getCountByPrefix(@Nullable String namespace, @Nullable String treePath) {

        return database.getCountByPrefix(txNamespace, treePath);
    }

    public @Override Set<String> findConflicts(@Nullable String namespace, Iterable<String> paths) {
        return database.findConflicts(txNamespace, paths);
    }

    public @Override void removeByPrefix(@Nullable String namespace, @Nullable String pathPrefix) {
        database.removeByPrefix(txNamespace, pathPrefix);
    }

    public @Override boolean isOpen() {
        return database.isOpen();
    }

    public @Override boolean isReadOnly() {
        return database.isReadOnly();
    }
}
