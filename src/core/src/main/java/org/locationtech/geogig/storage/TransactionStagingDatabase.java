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

import static org.locationtech.geogig.api.Ref.append;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.api.plumbing.TransactionEnd;
import org.locationtech.geogig.api.plumbing.merge.Conflict;

import com.google.common.base.Optional;

/**
 * A {@link StagingDatabase} decorator for a specific {@link GeogigTransaction transaction}.
 * <p>
 * This decorator creates a transaction specific namespace under the
 * {@code transactions/<transaction id>} path, and maps all query and storage methods to that
 * namespace.
 * 
 * @see GeogigTransaction
 * @see TransactionBegin
 * @see TransactionEnd
 */
public class TransactionStagingDatabase implements StagingDatabase {

    private final StagingDatabase database;

    private final String txNamespace;

    /**
     * Constructs a new {@code TransactionStagingDatabase}.
     * 
     * @param database the original staging database
     * @param transactionId the transaction id
     */
    public TransactionStagingDatabase(final StagingDatabase database, final UUID transactionId) {
        this.database = database;
        this.txNamespace = append(
                append(GeogigTransaction.TRANSACTIONS_NAMESPACE, transactionId.toString()),
                "conflicts");
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public void open() {
        database.open();
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public boolean isOpen() {
        return database.isOpen();
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public void close() {
        database.close();

    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public boolean exists(ObjectId id) {
        return database.exists(id);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public List<ObjectId> lookUp(String partialId) {
        return database.lookUp(partialId);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public RevObject get(ObjectId id) throws IllegalArgumentException {
        return database.get(id);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public <T extends RevObject> T get(ObjectId id, Class<T> type) throws IllegalArgumentException {
        return database.get(id, type);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public @Nullable
    RevObject getIfPresent(ObjectId id) {
        return database.getIfPresent(id);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public @Nullable
    <T extends RevObject> T getIfPresent(ObjectId id, Class<T> type)
            throws IllegalArgumentException {
        return database.getIfPresent(id, type);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public RevTree getTree(ObjectId id) {
        return database.getTree(id);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public RevFeature getFeature(ObjectId id) {
        return database.getFeature(id);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public RevFeatureType getFeatureType(ObjectId id) {
        return database.getFeatureType(id);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public RevCommit getCommit(ObjectId id) {
        return database.getCommit(id);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public RevTag getTag(ObjectId id) {
        return database.getTag(id);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public boolean put(RevObject object) {
        return database.put(object);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public ObjectInserter newObjectInserter() {
        return database.newObjectInserter();
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public boolean delete(ObjectId objectId) {
        return database.delete(objectId);
    }

    /**
     * Pass through to the original {@link StagingDatabase}.
     */
    @Override
    public void putAll(Iterator<? extends RevObject> objects, final BulkOpListener listener) {
        database.putAll(objects, listener);
    }

    @Override
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids, final BulkOpListener listener) {
        return database.getAll(ids, listener);
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

    @Override
    public long deleteAll(Iterator<ObjectId> ids, final BulkOpListener listener) {
        return database.deleteAll(ids, listener);
    }

    @Override
    public void configure() {
        // No-op
    }

    @Override
    public void checkConfig() {
        // No-op
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids) {
        return getAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids) {
        return deleteAll(ids, BulkOpListener.NOOP_LISTENER);
    }
}
