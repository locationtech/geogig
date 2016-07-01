/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.locationtech.geogig.api.Ref.TRANSACTIONS_PREFIX;
import static org.locationtech.geogig.api.Ref.append;

import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.api.plumbing.TransactionEnd;
import org.locationtech.geogig.api.plumbing.merge.Conflict;

import com.google.common.base.Optional;

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
public class TransactionConflictsDatabase implements ConflictsDatabase {

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
        this.txNamespace = append(append(TRANSACTIONS_PREFIX, transactionId.toString()),
                "conflicts");
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    @Override
    public boolean hasConflicts(String namespace) {
        return database.hasConflicts(txNamespace);
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    @Override
    public Optional<Conflict> getConflict(@Nullable String namespace, String path) {
        return database.getConflict(txNamespace, path);
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    @Override
    public List<Conflict> getConflicts(@Nullable String namespace, @Nullable String pathFilter) {
        return database.getConflicts(txNamespace, pathFilter);
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    @Override
    public void addConflict(@Nullable String namespace, Conflict conflict) {
        database.addConflict(txNamespace, conflict);
    }

    @Override
    public void addConflicts(@Nullable String namespace, Iterable<Conflict> conflicts) {
        database.addConflicts(txNamespace, conflicts);
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    @Override
    public void removeConflict(@Nullable String namespace, String path) {
        database.removeConflict(txNamespace, path);
    }

    /**
     * Pass through to {@link StagingDatabase}, replacing the namespace with the transaction
     * namespace.
     */
    @Override
    public void removeConflicts(@Nullable String namespace) {
        database.removeConflicts(txNamespace);
    }
}
