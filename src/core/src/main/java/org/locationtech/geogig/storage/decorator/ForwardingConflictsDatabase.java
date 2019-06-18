/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.decorator;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class ForwardingConflictsDatabase implements ConflictsDatabase {

    protected final ConflictsDatabase actual;

    public @Override void open() {
        actual.open();
    }

    public @Override void close() {
        actual.close();
    }

    public @Override boolean hasConflicts(@Nullable String namespace) {
        return actual.hasConflicts(namespace);
    }

    public @Override boolean isOpen() {
        return actual.isOpen();
    }

    public @Override boolean isReadOnly() {
        return actual.isReadOnly();
    }

    public @Override void checkWritable() {
        actual.checkWritable();
    }

    public @Override Optional<Conflict> getConflict(@Nullable String namespace, String path) {
        return actual.getConflict(namespace, path);
    }

    public @Override void checkOpen() {
        actual.checkOpen();
    }

    public @Override Iterator<Conflict> getByPrefix(@Nullable String namespace,
            @Nullable String prefixFilter) {
        return actual.getByPrefix(namespace, prefixFilter);
    }

    public @Override long getCountByPrefix(@Nullable String namespace, @Nullable String treePath) {
        return actual.getCountByPrefix(namespace, treePath);
    }

    public @Override void addConflict(@Nullable String namespace, Conflict conflict) {
        actual.addConflict(namespace, conflict);
    }

    public @Override void addConflicts(@Nullable String namespace, Iterable<Conflict> conflicts) {
        actual.addConflicts(namespace, conflicts);
    }

    public @Override void removeConflict(@Nullable String namespace, String path) {
        actual.removeConflict(namespace, path);
    }

    public @Override void removeConflicts(@Nullable String namespace) {
        actual.removeConflicts(namespace);
    }

    public @Override void removeConflicts(@Nullable String namespace, Iterable<String> paths) {
        actual.removeConflicts(namespace, paths);
    }

    public @Override Set<String> findConflicts(@Nullable String namespace, Set<String> paths) {
        return actual.findConflicts(namespace, paths);
    }

    public @Override void removeByPrefix(@Nullable String namespace, @Nullable String pathPrefix) {
        actual.removeByPrefix(namespace, pathPrefix);
    }
}
