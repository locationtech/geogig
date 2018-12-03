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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

/**
 * Volatile implementation of a GeoGig {@link ConflictsDatabase} that utilizes the heap for storage.
 */
public class HeapConflictsDatabase implements ConflictsDatabase {

    private ConcurrentHashMap<String, ConcurrentHashMap<String, Conflict>> conflicts;

    public HeapConflictsDatabase() {
        conflicts = new ConcurrentHashMap<>();
    }

    public @Override void open() {
        // no-op
    }

    public @Override synchronized void close() {
        if (conflicts != null) {
            conflicts.clear();
            conflicts = null;
        }
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
    @Override
    public void addConflict(@Nullable String namespace, Conflict conflict) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        map.put(conflict.getPath(), conflict);
    }

    @Override
    public void addConflicts(@Nullable String namespace, Iterable<Conflict> conflicts) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        map.putAll(Maps.uniqueIndex(conflicts, (c) -> c.getPath()));
    }

    /**
     * Removes a conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the path of feature whose conflict should be removed
     */
    @Override
    public void removeConflict(@Nullable String namespace, String path) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        map.remove(path);
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
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        return Optional.fromNullable(map.get(path));
    }

    /**
     * Removes all conflicts from the database.
     * 
     * @param namespace the namespace of the conflicts to remove
     */
    @Override
    public void removeConflicts(@Nullable String namespace) {
        conflicts.remove(namespace(namespace));
    }

    @Override
    public boolean hasConflicts(String namespace) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        return !map.isEmpty();
    }

    @Override
    public Iterator<Conflict> getByPrefix(@Nullable String namespace,
            @Nullable String prefixFilter) {

        ConcurrentHashMap<String, Conflict> map = get(namespace);
        Predicate<String> filter;
        filter = prefixFilter == null ? Predicates.alwaysTrue() : new PathFilter(prefixFilter);

        return Maps.filterKeys(map, filter).values().iterator();
    }

    @Override
    public long getCountByPrefix(@Nullable String namespace, @Nullable String treePath) {
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        Predicate<String> filter;
        filter = treePath == null ? Predicates.alwaysTrue() : new PathFilter(treePath);

        int count = Maps.filterKeys(map, filter).size();
        return count;
    }

    private static class PathFilter implements Predicate<String> {

        private String prefix;

        private String treePath;

        PathFilter(final String treePath) {
            checkNotNull(treePath);
            this.prefix = treePath + "/";
            this.treePath = treePath;
        }

        @Override
        public boolean apply(String path) {
            boolean matches = treePath.equals(path) || path.startsWith(prefix);
            return matches;
        }

    }

    @Override
    public Set<String> findConflicts(@Nullable String namespace, Set<String> paths) {
        checkNotNull(paths);

        Map<String, Conflict> nsmap = get(namespace);
        Set<String> matches = new HashSet<>();
        if (!nsmap.isEmpty()) {
            Set<String> keys = nsmap.keySet();
            for (String path : paths) {
                if (keys.contains(path)) {
                    matches.add(path);
                }
            }
        }
        return matches;
    }

    @Override
    public void removeByPrefix(@Nullable String namespace, @Nullable String pathPrefix) {
        Iterator<Conflict> matches = getByPrefix(namespace, pathPrefix);
        ConcurrentHashMap<String, Conflict> map = get(namespace);
        while (matches.hasNext()) {
            map.remove(matches.next().getPath());
        }
    }
}
