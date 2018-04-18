/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Optional;

/**
 * Serves as an interface for the staging area of the GeoGig repository.
 * <p>
 * The staging area keeps track of the changes that have been staged, but not yet committed to the
 * repository.
 * 
 * @since 1.0
 */
public interface StagingArea {

    /**
     * @return the conflicts database.
     */
    public ConflictsDatabase conflictsDatabase();

    /**
     * Updates the STAGE_HEAD ref to the specified tree.
     * 
     * @param newTree the tree to set as the new STAGE_HEAD
     */
    public void updateStageHead(ObjectId newTree);

    /**
     * @return the tree represented by STAGE_HEAD. If there is no tree set at STAGE_HEAD, it will
     *         return the HEAD tree (no staged changes).
     */
    public RevTree getTree();

    /**
     * @return {@code true} if there are no uncommitted features in the index, {@code false}
     *         otherwise
     */
    public boolean isClean();

    /**
     * @param path the path of the {@link Node}
     * @return the {@link Node} for the feature at the specified path if it exists in the index,
     *         otherwise {@link Optional#absent()}
     */
    public abstract Optional<Node> findStaged(final String path);

    /**
     * Stages the changes indicated by the {@link DiffEntry} iterator.
     * 
     * @param progress the listener to track progress with
     * @param unstaged the unstaged changes
     * @param numChanges the number of changes
     */
    public abstract void stage(final ProgressListener progress, final Iterator<DiffEntry> unstaged,
            final long numChanges);

    /**
     * @param pathFilter the path filter for the changes
     * @return an iterator for all of the differences between STAGE_HEAD and HEAD based on the path
     *         filter.
     */
    public abstract AutoCloseableIterator<DiffEntry> getStaged(
            final @Nullable List<String> pathFilters);

    /**
     * Returns the number of differences between STAGE_HEAD and HEAD based on the path filter.
     * 
     * @param pathFilters the path filters for the changes
     * @return the number differences
     */
    public abstract DiffObjectCount countStaged(final @Nullable List<String> pathFilters);

    /**
     * Returns the number of conflicted objects in the index, for the given path filter.
     * 
     * @param pathFilter the path filter for the conflicts
     * @return the number of conflicted objects
     */
    public long countConflicted(final @Nullable String pathFilter);

    /**
     * Returns the list of conflicted objects in the index for the given path filter.
     * 
     * @param pathFilter the path filter for the conflicts
     * @return the list of conflicts
     */
    public Iterator<Conflict> getConflicted(final @Nullable String pathFilter);

}