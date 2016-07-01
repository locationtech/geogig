/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;

/**
 * Volatile implementation of a GeoGig {@link ConflictsDatabase} that utilizes the heap for storage.
 */
public class HeapConflictsDatabase implements ConflictsDatabase {

    private Map<String, Map<String, Conflict>> conflicts;

    public HeapConflictsDatabase() {
        conflicts = new ConcurrentHashMap<>();
    }

    public synchronized void close() {
        if (conflicts != null) {
            conflicts.clear();
            conflicts = null;
        }
    }

    /**
     * Gets all conflicts that match the specified path filter.
     * 
     * @param namespace the namespace of the conflict
     * @param pathFilter the path filter, if this is not defined, all conflicts will be returned
     * @return the list of conflicts
     */
    @Override
    public List<Conflict> getConflicts(@Nullable String namespace,
            @Nullable final String pathFilter) {
        if (namespace == null) {
            namespace = "root";
        }

        if (conflicts.get(namespace) == null) {
            return ImmutableList.of();
        }
        if (pathFilter == null) {
            return ImmutableList.copyOf(conflicts.get(namespace).values());
        }
        UnmodifiableIterator<Conflict> filtered = Iterators
                .filter(conflicts.get(namespace).values().iterator(), new Predicate<Conflict>() {
                    @Override
                    public boolean apply(@Nullable Conflict c) {
                        return (c.getPath().startsWith(pathFilter));
                    }

                });
        return ImmutableList.copyOf(filtered);
    }

    /**
     * Adds a conflict to the database.
     * 
     * @param namespace the namespace of the conflict
     * @param conflict the conflict to add
     */
    @Override
    public void addConflict(@Nullable String namespace, Conflict conflict) {
        if (namespace == null) {
            namespace = "root";
        }
        Map<String, Conflict> conflictMap = conflicts.get(namespace);
        if (conflictMap == null) {
            conflictMap = Maps.newHashMap();
            conflicts.put(namespace, conflictMap);
        }
        conflictMap.put(conflict.getPath(), conflict);

    }

    @Override
    public void addConflicts(@Nullable String namespace, Iterable<Conflict> conflicts) {
        if (namespace == null) {
            namespace = "root";
        }
        Map<String, Conflict> conflictMap = this.conflicts.get(namespace);
        if (conflictMap == null) {
            conflictMap = Maps.newHashMap();
            this.conflicts.put(namespace, conflictMap);
        }
        conflictMap.putAll(Maps.uniqueIndex(conflicts, (c) -> c.getPath()));
    }

    /**
     * Removes a conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the path of feature whose conflict should be removed
     */
    @Override
    public void removeConflict(@Nullable String namespace, String path) {
        if (namespace == null) {
            namespace = "root";
        }
        Map<String, Conflict> conflictMap = conflicts.get(namespace);
        if (conflictMap != null) {
            conflictMap.remove(path);
        }
    }

    @Override
    public void removeConflicts(@Nullable String namespace, Iterable<String> paths) {
        for (String path : paths) {
            removeConflict(namespace, path);
        }
    }

    /**
     * Gets the specified conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the conflict to retrieve
     * @return the conflict, or {@link Optional#absent()} if it was not found
     */
    @Override
    public Optional<Conflict> getConflict(@Nullable String namespace, String path) {
        if (namespace == null) {
            namespace = "root";
        }
        Map<String, Conflict> conflictMap = conflicts.get(namespace);
        if (conflictMap != null) {
            return Optional.fromNullable(conflictMap.get(path));
        }
        return Optional.absent();
    }

    /**
     * Removes all conflicts from the database.
     * 
     * @param namespace the namespace of the conflicts to remove
     */
    @Override
    public void removeConflicts(@Nullable String namespace) {
        if (namespace == null) {
            namespace = "root";
        }
        conflicts.remove(namespace);
    }

    @Override
    public boolean hasConflicts(String namespace) {
        if (namespace == null) {
            namespace = "root";
        }
        Map<String, Conflict> conflicts = this.conflicts.get(namespace);
        return conflicts != null && !conflicts.isEmpty();
    }

}
