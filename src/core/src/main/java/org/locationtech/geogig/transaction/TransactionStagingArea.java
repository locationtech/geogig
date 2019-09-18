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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ConflictsDatabase;

/**
 * A {@link StagingArea} decorator for a specific {@link GeogigTransaction transaction}.
 * <p>
 * This decorator creates a transaction specific namespace under the
 * {@code transactions/<transaction id>} path, and maps all query and storage methods to that
 * namespace.
 * 
 * @see GeogigTransaction
 * @see TransactionBegin
 * @see TransactionEnd
 */
class TransactionStagingArea implements StagingArea {

    private StagingArea index;

    private ConflictsDatabase conflictsDb;

    /**
     * Constructs a new {@code TransactionStagingArea}.
     * 
     * @param index the repository index
     * @param transactionId the transaction id
     */
    public TransactionStagingArea(final StagingArea index, final UUID transactionId) {
        this.index = index;
        conflictsDb = new TransactionConflictsDatabase(index.conflictsDatabase(), transactionId);
    }

    /**
     * @return the transaction conflicts database
     */
    public @Override ConflictsDatabase conflictsDatabase() {
        return conflictsDb;
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    public @Override void updateStageHead(ObjectId newTree) {
        index.updateStageHead(newTree);
    }

    public @Override void updateStageHead(ObjectId newTree, String reason) {
        index.updateStageHead(newTree, reason);
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    public @Override RevTree getTree() {
        return index.getTree();
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    public @Override Optional<Node> findStaged(String path) {
        return index.findStaged(path);
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    public @Override void stage(ProgressListener progress, Iterator<DiffEntry> unstaged,
            long numChanges) {
        index.stage(progress, unstaged, numChanges);
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    public @Override AutoCloseableIterator<DiffEntry> getStaged(
            @Nullable List<String> pathFilters) {
        return index.getStaged(pathFilters);
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    public @Override DiffObjectCount countStaged(@Nullable List<String> pathFilters) {
        return index.countStaged(pathFilters);
    }

    /**
     * @param pathFilter the path filter for the conflicts
     * @return the number of conflicts that match the path filter, or the total number of conflicts
     *         if a path filter was not specified
     */
    public @Override long countConflicted(@Nullable String pathFilter) {
        return conflictsDb.getCountByPrefix(null, pathFilter);
    }

    /**
     * @param pathFilter the path filter for the conflicts
     * @return the conflicts that match the path filter, if no path filter is specified, all
     *         conflicts will be returned
     */
    public @Override Iterator<Conflict> getConflicted(@Nullable String pathFilter) {
        return conflictsDb.getByPrefix(null, pathFilter);
    }

    public @Override boolean isClean() {
        return index.isClean();
    }

}
