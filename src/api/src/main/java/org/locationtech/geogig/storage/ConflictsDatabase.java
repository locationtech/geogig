/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import org.locationtech.geogig.repository.Conflict;

import lombok.NonNull;

/**
 * Provides an interface for implementations of conflict databases, which manage GeoGig conflicts.
 * 
 * @since 1.0
 */
public interface ConflictsDatabase extends Store {
    /**
     * Checks whether there are conflicts for the given transaction namesapce.
     * 
     * @param namespace the namespace of the conflict
     * @return {@code true} if there are conflicts in the provided transaction namespace,
     *         {@code false} otherwise
     */
    public boolean hasConflicts(String namespace);

    /**
     * Gets the specified conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the conflict to retrieve
     * @return the conflict, or {@link Optional#empty()} if it was not found
     */
    public Optional<Conflict> getConflict(String namespace, String path);

    /**
     * Retrieves all conflicts that match the specified path filter.
     * 
     * @param namespace optional namespace (i.e. transaction id), to filter conflicts by.
     * @param treePath optional prefix path filter. If not given, all conflicts matching the given
     *        namespace are returned. If given, the {@code treePath} is the path of the parent tree
     *        for which to return conflicts.
     * @param batchSizre
     * @return an iterator of conflicts matching the prefix filter.
     * @apiNote {@code treePath} can only be the path of the parent tree for which to return
     *          conflict information, which in a SQL implementation over a table with a 'path'
     *          column would translate to
     *          {@code WHERE path = '<treePath>' OR path LIKE '<treePath>/%'} to account for
     *          conflicts to the tree itself as well as any of it's children.
     */
    public Iterator<Conflict> getByPrefix(String namespace, String prefixFilter);

    /**
     * Gets the number of conflicts in the given path.
     * 
     * @param namespace the namespace of the conflicts
     * @param treePath the path to count
     * @return the number of conflicts
     */
    public long getCountByPrefix(String namespace, String treePath);

    /**
     * Adds a conflict to the database.
     * 
     * @param namespace the namespace of the conflict
     * @param conflict the conflict to add
     */
    public void addConflict(String namespace, Conflict conflict);

    /**
     * Adds the provided conflicts to the database for the given namespace.
     */
    public void addConflicts(String namespace, Iterable<Conflict> conflicts);

    /**
     * Removes a conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the path of feature whose conflict should be removed
     */
    public void removeConflict(String namespace, @NonNull String path);

    /**
     * Removes all conflicts from the database.
     * 
     * @param namespace the namespace of the conflicts to remove
     */
    public void removeConflicts(String namespace);

    /**
     * Removes the conflicts matching the provided paths, if they exist.
     */
    public void removeConflicts(String namespace, @NonNull Iterable<String> paths);

    /**
     * Finds and returns the set of conflict paths that exist and match any of the provided paths.
     */
    public Set<String> findConflicts(String namespace, @NonNull Iterable<String> paths);

    /**
     * Removes all conflicts that match the specified namespace/prefix filter, if given, or all that
     * match the specified namespace if not.
     * 
     * @param namespace the transaction id, or {@code null} for no transaction namespace.
     * @param pathPrefix the path of the tree to filter by.
     */
    public void removeByPrefix(String namespace, String pathPrefix);
}