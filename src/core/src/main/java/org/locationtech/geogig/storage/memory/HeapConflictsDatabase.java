/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.storage.AbstractStore;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;

import lombok.NonNull;

/**
 * Volatile implementation of a GeoGig {@link ConflictsDatabase} that utilizes the heap for storage.
 */
public class HeapConflictsDatabase extends AbstractStore implements ConflictsDatabase {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Conflict>> conflicts = new ConcurrentHashMap<>();

    public HeapConflictsDatabase() {
        super(false);
    }

    public HeapConflictsDatabase(boolean ro) {
        super(ro);
    }

    private String namespace(@Nullable String namespace) {
        return namespace == null ? "" : namespace;
    }

    private synchronized ConcurrentHashMap<String, Conflict> get(@Nullable String namespace) {
        namespace = namespace(namespace);
        ConcurrentHashMap<String, Conflict> nsmap = this.conflicts.get(namespace);
        if (nsmap == null) {
            nsmap = new ConcurrentHashMap<>();
            this.conflicts.put(namespace, nsmap);
        }
        return nsmap;
    }

    /**
     * Adds a conflict to the database.
     * 
     * @param namespace the namespace of the conflict
     * @param conflict the conflict to add
     */
    public @Override void addConflict(@Nullable String namespace, Conflict conflict) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        map.put(conflict.getPath(), conflict);
    }

    public @Override void addConflicts(@Nullable String namespace, Iterable<Conflict> conflicts) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        map.putAll(Maps.uniqueIndex(conflicts, (c) -> c.getPath()));
    }

    /**
     * Removes a conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the path of feature whose conflict should be removed
     */
    public @Override void removeConflict(@Nullable String namespace, String path) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        map.remove(path);
    }

    public @Override void removeConflicts(@Nullable String namespace, Iterable<String> paths) {
        for (String path : paths) {
            removeConflict(namespace, path);
        }
    }

    /**
     * Gets the specified conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the conflict to retrieve
     * @return the conflict, or {@link Optional#empty()} if it was not found
     */
    public @Override Optional<Conflict> getConflict(@Nullable String namespace, String path) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        return Optional.ofNullable(map.get(path));
    }

    /**
     * Removes all conflicts from the database.
     * 
     * @param namespace the namespace of the conflicts to remove
     */
    public @Override void removeConflicts(@Nullable String namespace) {
        conflicts.remove(namespace(namespace));
    }

    public @Override boolean hasConflicts(String namespace) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        return !map.isEmpty();
    }

    public @Override Iterator<Conflict> getByPrefix(@Nullable String namespace,
            @Nullable String prefixFilter) {

        ConcurrentHashMap<String, Conflict> map = get(namespace);
        Predicate<String> filter;
        filter = prefixFilter == null ? Predicates.alwaysTrue() : new PathFilter(prefixFilter);

        return Maps.filterKeys(map, filter).values().iterator();
    }

    public @Override long getCountByPrefix(@Nullable String namespace, @Nullable String treePath) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        Predicate<String> filter;
        filter = treePath == null ? Predicates.alwaysTrue() : new PathFilter(treePath);

        int count = Maps.filterKeys(map, filter).size();
        return count;
    }

    private static class PathFilter implements Predicate<String> {

        private String prefix;

        private String treePath;

        PathFilter(final @NonNull String treePath) {
            this.prefix = treePath + "/";
            this.treePath = treePath;
        }

        public @Override boolean apply(String path) {
            boolean matches = treePath.equals(path) || path.startsWith(prefix);
            return matches;
        }

    }

    public @Override Set<String> findConflicts(@Nullable String namespace,
            @NonNull Iterable<String> paths) {
        Map<String, Conflict> nsmap = get(namespace);
        return Streams.stream(paths).filter(nsmap::containsKey).collect(Collectors.toSet());
    }

    public @Override void removeByPrefix(@Nullable String namespace, @Nullable String pathPrefix) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        getByPrefix(namespace, pathPrefix).forEachRemaining(c -> map.remove(c.getPath()));
    }
}
