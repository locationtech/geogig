/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.mapdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;
import org.mapdb.Fun;
import org.mapdb.Serializer;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * A conflicts database that uses a Mapdb file for persistence.
 * 
 */
class MapdbConflictsDatabase implements ConflictsDatabase {

    private ConcurrentNavigableMap<Object[], String> conflicts = null;

    protected final DB db;

    public MapdbConflictsDatabase(org.mapdb.DB db) {
        this.db = db;
        open();
    }

    private void open() {
        if (conflicts != null) {
            return;
        }
        @SuppressWarnings("rawtypes")
        BTreeKeySerializer keySerializer = new BTreeKeySerializer.ArrayKeySerializer(
                new Comparator[] { Fun.COMPARATOR, Fun.COMPARATOR }, new Serializer[] {
                        Serializer.STRING, Serializer.STRING });
        conflicts = db.treeMapCreate("conflicts").keySerializer(keySerializer).makeOrGet();
    }

    // TODO when will close() be called? Only on shutdown of the JVM or at other times too (and
    // reopened later?)
    public synchronized void close() {
        if (conflicts != null) {
            if (!db.isClosed()) {
                db.commit();
                db.close();
            }
        }
        conflicts = null;
    }

    /**
     * Gets all conflicts that match the specified path filter.
     * 
     * @param namespace the namespace of the conflict
     * @param pathFilter the path filter, if this is not defined, all conflicts will be returned
     * @return the list of conflicts
     */
    @Override
    public List<Conflict> getConflicts(@Nullable String namespace, @Nullable final String pathFilter) {
        if (namespace == null) {
            namespace = "root";
        }
        Object[] fromKey = new Object[] { namespace };
        Object[] toKey = new Object[] { namespace, null };
        if (pathFilter != null) {
            char nextLetter = (char) (pathFilter.charAt(pathFilter.length() - 1) + 1);
            String end = pathFilter.substring(0, pathFilter.length() - 1) + nextLetter;
            fromKey = new Object[] { namespace, pathFilter };
            toKey = new Object[] { namespace, end };
        }

        List<Conflict> matchingConflicts = new ArrayList<Conflict>();
        Collection<String> stringObjects = conflicts.subMap(fromKey, toKey).values();
        for (String s : stringObjects) {
            matchingConflicts.add(Conflict.valueOf(s));
        }
        return ImmutableList.copyOf(matchingConflicts);
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
        conflicts.put(new Object[] { namespace, conflict.getPath() }, conflict.toString());
        db.commit();
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
        conflicts.remove(new Object[] { namespace, path });
        db.commit();
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
        String value = conflicts.get(new Object[] { namespace, path });
        Conflict conflict = null;
        if (value != null) {
            conflict = Conflict.valueOf(value);
        }
        return Optional.fromNullable(conflict);
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
        Object[] fromKey = new Object[] { namespace };
        Object[] toKey = new Object[] { namespace, null };
        conflicts.subMap(fromKey, toKey).clear();
        db.commit();
    }

    @Override
    public boolean hasConflicts(String namespace) {
        if (namespace == null) {
            namespace = "root";
        }

        Object[] fromKey = new Object[] { namespace };
        Object[] toKey = new Object[] { namespace, null };
        return conflicts.subMap(fromKey, toKey).size() > 0;
    }

}
