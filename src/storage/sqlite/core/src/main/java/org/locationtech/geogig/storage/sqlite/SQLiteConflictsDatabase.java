/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.sqlite;

import java.util.List;

import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Base class for SQLite based staging database.
 * 
 * @author Justin Deoliveira, Boundless
 * 
 * @param <T>
 */
public abstract class SQLiteConflictsDatabase<T> implements ConflictsDatabase {

    private SQLiteConflictsDatabase<T> repoDb;

    private final T cx;

    public SQLiteConflictsDatabase(T cx) {
        this.cx = cx;
    }

    public void open() {
        init(cx);
    }

    @Override
    public Optional<Conflict> getConflict(String namespace, String path) {
        List<Conflict> conflicts = getConflicts(namespace, path);
        if (conflicts.isEmpty()) {
            return Optional.absent();
        }
        return Optional.of(conflicts.get(0));
    }

    @Override
    public boolean hasConflicts(String namespace) {
        int count = count(namespace, cx);
        return count > 0;
    }

    @Override
    public List<Conflict> getConflicts(String namespace, String pathFilter) {
        return Lists.newArrayList(Iterables.transform(get(namespace, pathFilter, cx),
                StringToConflict.INSTANCE));
    }

    @Override
    public void addConflict(String namespace, Conflict conflict) {
        put(namespace, conflict.getPath(), conflict.toString(), cx);
    }

    @Override
    public void removeConflict(String namespace, String path) {
        remove(namespace, path, cx);
    }

    @Override
    public void removeConflicts(String namespace) {
        for (Conflict c : Iterables.transform(get(namespace, null, cx), StringToConflict.INSTANCE)) {
            removeConflict(namespace, c.getPath());
        }
    }

    /**
     * Creates the object table with the following schema:
     * 
     * <pre>
     * conflicts(namespace:varchar, path:varchar, conflict:varchar)
     * </pre>
     * 
     * Implementations of this method should be prepared to be called multiple times, so must check
     * if the table already exists.
     * 
     * @param cx The connection object.
     */
    protected abstract void init(T cx);

    /**
     * Returns the number of conflicts matching the specified namespace filter.
     * 
     * @param namespace Namespace value, may be <code>null</code>.
     * 
     */
    protected abstract int count(final String namespace, T cx);

    /**
     * Returns all conflicts matching the specified namespace and pathFilter.
     * 
     * @param namespace Namespace value, may be <code>null</code>.
     * @param pathFilter Path filter, may be <code>null</code>.
     * 
     */
    protected abstract Iterable<String> get(String namespace, String pathFilter, T cx);

    /**
     * Adds a conflict.
     * 
     * @param namespace The conflict namespace.
     * @param path The path of the conflict.
     * @param conflict The conflict value.
     */
    protected abstract void put(String namespace, String path, String conflict, T cx);

    /**
     * Removed a conflict.
     * 
     * @param namespace The conflict namespace.
     * @param path The path of the conflict.
     */
    protected abstract void remove(String namespace, String path, T cx);
}
