/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.TransactionEnd;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Optional;

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
public class TransactionStagingArea implements StagingArea {

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
    @Override
    public ConflictsDatabase conflictsDatabase() {
        return conflictsDb;
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    @Override
    public void updateStageHead(ObjectId newTree) {
        index.updateStageHead(newTree);
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    @Override
    public RevTree getTree() {
        return index.getTree();
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    @Override
    public Optional<Node> findStaged(String path) {
        return index.findStaged(path);
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    @Override
    public void stage(ProgressListener progress, Iterator<DiffEntry> unstaged, long numChanges) {
        index.stage(progress, unstaged, numChanges);
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    @Override
    public AutoCloseableIterator<DiffEntry> getStaged(@Nullable List<String> pathFilters) {
        return index.getStaged(pathFilters);
    }

    /**
     * Pass through to the original {@link StagingArea}.
     */
    @Override
    public DiffObjectCount countStaged(@Nullable List<String> pathFilters) {
        return index.countStaged(pathFilters);
    }

    /**
     * @param pathFilter the path filter for the conflicts
     * @return the number of conflicts that match the path filter, or the total number of conflicts
     *         if a path filter was not specified
     */
    @Override
    public long countConflicted(@Nullable String pathFilter) {
        return conflictsDb.getCountByPrefix(null, pathFilter);
    }

    /**
     * @param pathFilter the path filter for the conflicts
     * @return the conflicts that match the path filter, if no path filter is specified, all
     *         conflicts will be returned
     */
    @Override
    public Iterator<Conflict> getConflicted(@Nullable String pathFilter) {
        return conflictsDb.getByPrefix(null, pathFilter);
    }

    @Override
    public boolean isClean() {
        return index.isClean();
    }

}
