/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

/**
 * 
 */
public class FileConflictsDatabase implements ConflictsDatabase {

    private File repositoryDirectory;

    public FileConflictsDatabase(final File repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    public synchronized void open() {
        if (isOpen()) {
            return;
        }
    }

    public void close() {
        repositoryDirectory = null;
    }

    public boolean isOpen() {
        return repositoryDirectory != null;
    }

    // TODO:
    // *****************************************************************************************
    // The following methods are a temporary implementation of conflict storage that relies on a
    // conflict file in the index folder
    // *****************************************************************************************

    @Override
    public boolean hasConflicts(String namespace) {
        final Object monitor = resolveConflictsMonitor(namespace);
        if (monitor == null) {
            return false;
        }
        synchronized (monitor) {
            final File file = resolveConflictsFile(namespace);
            return file.exists() && file.length() > 0;
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
        final Object monitor = resolveConflictsMonitor(namespace);
        if (null == monitor) {
            return ImmutableList.of();
        }
        synchronized (monitor) {
            final File file = resolveConflictsFile(namespace);
            if (null == file || !file.exists() || file.length() == 0) {
                return ImmutableList.of();
            }
            List<Conflict> conflicts;
            try {
                conflicts = Files.readLines(file, Charsets.UTF_8,
                        new LineProcessor<List<Conflict>>() {
                            List<Conflict> conflicts = Lists.newArrayList();

                            @Override
                            public List<Conflict> getResult() {
                                return conflicts;
                            }

                            @Override
                            public boolean processLine(String s) throws IOException {
                                Conflict c = Conflict.valueOf(s);
                                if (pathFilter == null) {
                                    conflicts.add(c);
                                } else if (c.getPath().startsWith(pathFilter)) {
                                    conflicts.add(c);
                                }
                                return true;
                            }
                        });
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            return conflicts;
        }
    }

    /**
     * Adds a conflict to the database.
     * 
     * @param namespace the namespace of the conflict
     * @param conflict the conflict to add
     */
    @Override
    public void addConflict(@Nullable String namespace, Conflict conflict) {
        final Object monitor = resolveConflictsMonitor(namespace);
        checkState(monitor != null,
                "Either not inside a repository directory or the staging area is closed");
        synchronized (monitor) {
            Optional<File> fileOp = findOrCreateConflictsFile(namespace);
            checkState(fileOp.isPresent());
            try {
                final File file = fileOp.get();
                Files.append(conflict.toString() + "\n", file, Charsets.UTF_8);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void addConflicts(@Nullable String namespace, Iterable<Conflict> conflicts) {
        final Object monitor = resolveConflictsMonitor(namespace);
        checkState(monitor != null,
                "Either not inside a repository directory or the staging area is closed");
        synchronized (monitor) {
            Optional<File> fileOp = findOrCreateConflictsFile(namespace);
            checkState(fileOp.isPresent());
            final File file = fileOp.get();
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file, true), Charsets.UTF_8)) {
                for (Conflict conflict : conflicts) {
                    writer.write(conflict.toString());
                    writer.write('\n');
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    /**
     * @return the object to synchronize on, or null if not inside a geogig repository
     */
    @Nullable
    private Object resolveConflictsMonitor(@Nullable final String namespace) {
        final File file = resolveConflictsFile(namespace);
        Object monitor = null;
        if (file != null) {
            try {
                monitor = file.getCanonicalPath().intern();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        return monitor;
    }

    /**
     * Removes a conflict from the database.
     * 
     * @param namespace the namespace of the conflict
     * @param path the path of feature whose conflict should be removed
     */
    @Override
    public void removeConflict(@Nullable String namespace, final String path) {
        checkNotNull(path, "path is null");
        final Object monitor = resolveConflictsMonitor(namespace);
        checkState(monitor != null,
                "Either not inside a repository directory or the staging area is closed");
        synchronized (monitor) {
            final File file = resolveConflictsFile(namespace);
            if (file == null || !file.exists()) {
                return;
            }
            try {
                List<Conflict> conflicts = getConflicts(namespace, null);

                StringBuilder sb = new StringBuilder();
                for (Conflict conflict : conflicts) {
                    if (!path.equals(conflict.getPath())) {
                        sb.append(conflict.toString() + "\n");
                    }
                }
                String s = sb.toString();
                if (s.isEmpty()) {
                    file.delete();
                } else {
                    Files.write(s, file, Charsets.UTF_8);
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
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
    public Optional<Conflict> getConflict(@Nullable String namespace, final String path) {
        final Object monitor = resolveConflictsMonitor(namespace);
        if (null == monitor) {
            return Optional.absent();
        }
        synchronized (monitor) {
            File file = resolveConflictsFile(namespace);
            if (file == null || !file.exists()) {
                return Optional.absent();
            }
            Conflict conflict = null;
            try {
                conflict = Files.readLines(file, Charsets.UTF_8, new LineProcessor<Conflict>() {
                    Conflict conflict = null;

                    @Override
                    public Conflict getResult() {
                        return conflict;
                    }

                    @Override
                    public boolean processLine(String s) throws IOException {
                        Conflict c = Conflict.valueOf(s);
                        if (c.getPath().equals(path)) {
                            conflict = c;
                            return false;
                        } else {
                            return true;
                        }
                    }
                });
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            return Optional.fromNullable(conflict);
        }
    }

    private Optional<File> findOrCreateConflictsFile(@Nullable String namespace) {
        final Object monitor = resolveConflictsMonitor(namespace);
        checkState(Thread.holdsLock(monitor));

        final File file = resolveConflictsFile(namespace);
        if (null == file) {
            return Optional.absent();
        }

        if (!file.exists()) {
            try {
                Files.createParentDirs(file);
                file.createNewFile();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

        return Optional.of(file);
    }

    /**
     * @return {@code null} if the database is closed or its location cannot be determined, the
     *         conflicts file that belongs to the given namespace otherwise, which may or may not
     *         exist
     */
    @Nullable
    private File resolveConflictsFile(@Nullable String namespace) {
        if (namespace == null) {
            namespace = "conflicts";
        }
        File file = null;
        if (isOpen()) {
            file = new File(repositoryDirectory, namespace);
        }
        return file;
    }

    /**
     * Removes all conflicts from the database.
     * 
     * @param namespace the namespace of the conflicts to remove
     */
    @Override
    public void removeConflicts(@Nullable String namespace) {
        final Object monitor = resolveConflictsMonitor(namespace);
        checkState(monitor != null,
                "Either not inside a repository directory or the staging area is closed");
        synchronized (monitor) {
            File file = resolveConflictsFile(namespace);
            if (file != null && file.exists()) {
                checkState(file.delete(), "Unable to delete conflicts file %s", file);
            }
        }
    }

    @Override
    public void removeConflicts(@Nullable String namespace, Iterable<String> paths) {
        checkNotNull(paths, "paths is null");
        final Object monitor = resolveConflictsMonitor(namespace);
        checkState(monitor != null,
                "Either not inside a repository directory or the staging area is closed");
        synchronized (monitor) {
            final File conflictsFile = resolveConflictsFile(namespace);
            if (conflictsFile == null || !conflictsFile.exists()) {
                return;
            }

            try {
                final File tmpFile = File.createTempFile("conflicts", ".tmp",
                        conflictsFile.getParentFile());
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(tmpFile), Charsets.UTF_8)) {

                    HashSet<String> pathSet = Sets.newHashSet(paths);

                    Files.readLines(conflictsFile, Charsets.UTF_8, new LineProcessor<Void>() {
                        @Override
                        public Void getResult() {
                            return null;
                        }

                        @Override
                        public boolean processLine(String line) throws IOException {
                            Conflict c = Conflict.valueOf(line);
                            String path = c.getPath();
                            boolean removed = pathSet.remove(path);
                            if (!removed) {
                                writer.write(line);
                                writer.write('\n');
                            }
                            boolean continueProcessing = !pathSet.isEmpty();
                            return continueProcessing;
                        }
                    });

                }
                File backup = new File(conflictsFile.getParentFile(),
                        conflictsFile.getName() + ".bak");
                if (backup.exists()) {
                    backup.delete();
                }
                Files.move(conflictsFile, backup);
                try {
                    Files.move(tmpFile, conflictsFile);
                    backup.delete();
                } catch (IOException cantMove) {
                    Files.move(backup, conflictsFile);
                    throw Throwables.propagate(cantMove);
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }
}
