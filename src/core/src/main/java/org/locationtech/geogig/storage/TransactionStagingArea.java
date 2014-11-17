/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.api.plumbing.TransactionEnd;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffObjectCount;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.repository.StagingArea;

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

    private StagingDatabase database;

    /**
     * Constructs a new {@code TransactionStagingArea}.
     * 
     * @param index the repository index
     * @param transactionId the transaction id
     */
    public TransactionStagingArea(final StagingArea index, final UUID transactionId) {
        this.index = index;
        database = new TransactionStagingDatabase(index.getDatabase(), transactionId);
    }

    /**
     * @return the transaction staging database
     */
    @Override
    public StagingDatabase getDatabase() {
        return database;
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
    public Iterator<DiffEntry> getStaged(@Nullable List<String> pathFilters) {
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
    public int countConflicted(@Nullable String pathFilter) {
        return database.getConflicts(null, pathFilter).size();
    }

    /**
     * @param pathFilter the path filter for the conflicts
     * @return the conflicts that match the path filter, if no path filter is specified, all
     *         conflicts will be returned
     */
    @Override
    public List<Conflict> getConflicted(@Nullable String pathFilter) {
        return database.getConflicts(null, pathFilter);
    }

    @Override
    public boolean isClean() {
        return index.isClean();
    }

}
