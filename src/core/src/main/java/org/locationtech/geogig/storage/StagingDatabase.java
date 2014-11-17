/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage;

import java.util.List;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.di.Singleton;

import com.google.common.base.Optional;

/**
 * Provides an interface for GeoGig staging databases.
 * 
 */
@Singleton
public interface StagingDatabase extends ObjectDatabase {

    /**
     * Checks whether there are conflicts for the given transaction namesapce.
     * 
     * @param namespace the namespace of the conflict
     * @return {@code true} if there are conflicts in the provided transaction namespace,
     *         {@code false} otherwise
     */
    public boolean hasConflicts(@Nullable String namespace);

    /**
     * Gets the specified conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the conflict to retrieve
     * @return the conflict, or {@link Optional#absent()} if it was not found
     */
    public Optional<Conflict> getConflict(@Nullable String namespace, String path);

    /**
     * Gets all conflicts that match the specified path filter.
     * 
     * @param namespace the namespace of the conflict
     * @param pathFilter the path filter, if this is not defined, all conflicts will be returned
     * @return the list of conflicts
     */
    public List<Conflict> getConflicts(@Nullable String namespace, @Nullable String pathFilter);

    /**
     * Adds a conflict to the database.
     * 
     * @param namespace the namespace of the conflict
     * @param conflict the conflict to add
     */
    public void addConflict(@Nullable String namespace, Conflict conflict);

    /**
     * Removes a conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the path of feature whose conflict should be removed
     */
    public void removeConflict(@Nullable String namespace, String path);

    /**
     * Removes all conflicts from the database.
     * 
     * @param namespace the namespace of the conflicts to remove
     */
    public void removeConflicts(@Nullable String namespace);

}