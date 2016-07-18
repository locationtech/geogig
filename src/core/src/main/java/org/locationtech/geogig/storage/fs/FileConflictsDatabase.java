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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

/**
 * 
 */
public class FileConflictsDatabase implements ConflictsDatabase {

    private static final int DEFAULT_LINE_PROCESSING_BATCH_SIZE = 10_000;

    private File repositoryDirectory;

    /**
     * How many lines to load at once
     */
    private final int lineProcessingBatchSize;

    public FileConflictsDatabase(final File repositoryDirectory) {
        this(repositoryDirectory, DEFAULT_LINE_PROCESSING_BATCH_SIZE);
    }

    @VisibleForTesting
    FileConflictsDatabase(final File repositoryDirectory, final int lineProcessingBatchSize) {
        this.repositoryDirectory = repositoryDirectory;
        this.lineProcessingBatchSize = lineProcessingBatchSize;
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
    @Deprecated
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
                                Conflict c = FileConflictsDatabase.valueOf(s);
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

    @Override
    public Iterator<Conflict> getByPrefix(@Nullable String namespace, @Nullable String treePath) {

        return new ConflictsIterator(this, namespace, treePath);
    }

    @Nullable
    private Queue<Conflict> getBatch(@Nullable String namespace, @Nullable String treePath,
            int offset, int limit) throws SQLException {

        checkArgument(offset >= 0);
        checkArgument(limit > 0);

        synchronized (resolveConflictsMonitor(namespace)) {
            final File file = resolveConflictsFile(namespace);

            if (null == file || file.length() == 0L) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), Charsets.UTF_8))) {

                Stream<String> stream = reader.lines().parallel();
                if (null != treePath) {
                    stream = stream.filter(new ConflictPathFilter(treePath));
                }
                stream = stream.skip(offset).limit(limit);

                // used as a concurrent list
                ConcurrentLinkedQueue<Conflict> list = new ConcurrentLinkedQueue<>();
                stream.forEach((line) -> list.offer(FileConflictsDatabase.valueOf(line)));
                return list;
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public long getCountByPrefix(@Nullable String namespace, final @Nullable String treePath) {

        final Object monitor = resolveConflictsMonitor(namespace);

        synchronized (monitor) {
            final File file = resolveConflictsFile(namespace);

            if (null == file || !file.exists() || file.length() == 0) {
                return 0L;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), Charsets.UTF_8))) {

                Stream<String> stream = reader.lines().parallel();

                if (null != treePath) {
                    stream = stream.filter(new ConflictPathFilter(treePath));
                }

                AtomicLong counter = new AtomicLong();
                stream.forEach((l) -> counter.incrementAndGet());
                return counter.get();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    /**
     * Expects a String representing the {@link FileConflictsDatabase#valueOf(String) serialized
     * form} of a conflict, parses it's path and evaluates it for matching against the filter
     * prefix.
     *
     */
    private static class ConflictPathFilter implements Predicate<String> {

        private final PathFilter pathFilter;

        ConflictPathFilter(final String treePath) {
            this.pathFilter = new PathFilter(treePath);
        }

        @Override
        public boolean test(String line) {
            return pathFilter.test(readPath(line));
        }
    }

    /**
     * Expects only the path portion of a conflict and evaluates it against the tree path prefix
     * filter.
     *
     */
    private static class PathFilter implements Predicate<String> {

        private String prefix;

        private String treePath;

        PathFilter(final String treePath) {
            checkNotNull(treePath);
            this.prefix = treePath + "/";
            this.treePath = treePath;
        }

        @Override
        public boolean test(String path) {
            boolean matches = treePath.equals(path) || path.startsWith(prefix);
            return matches;
        }

    }

    private static class ConflictsIterator extends AbstractIterator<Conflict> {

        private final FileConflictsDatabase db;

        private final String namespace;

        private final String treePath;

        private final int pageSize;

        private int offset = 0;

        private int currentPageSize;

        private Iterator<Conflict> page;

        public ConflictsIterator(FileConflictsDatabase db, @Nullable String namespace,
                @Nullable String treePath) {
            this.db = db;
            this.pageSize = db.lineProcessingBatchSize;
            this.namespace = namespace;
            this.treePath = treePath;
            this.page = nextPage();
        }

        @Override
        protected Conflict computeNext() {
            if (page.hasNext()) {
                return page.next();
            }
            if (currentPageSize == 0) {
                return endOfData();
            }
            page = nextPage();
            return computeNext();
        }

        private Iterator<Conflict> nextPage() {
            @Nullable
            Queue<Conflict> batch;
            try {
                batch = db.getBatch(namespace, treePath, offset, pageSize);
            } catch (SQLException e) {
                throw Throwables.propagate(e);
            }
            if (batch == null) {
                this.currentPageSize = 0;
                this.offset = 0;
                return Collections.emptyIterator();
            }
            this.offset += pageSize;
            this.currentPageSize = batch.size();
            return batch.iterator();

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
                Files.append(encode(conflict).append('\n').toString(), file, Charsets.UTF_8);
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
                    writer.write(encode(conflict).append('\n').toString());
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
        if (null == monitor) {
            return;
        }
        synchronized (monitor) {
            final File conflictsFile = resolveConflictsFile(namespace);
            if (conflictsFile == null || conflictsFile.length() == 0L) {
                return;
            }

            final Set<String> paths = ImmutableSet.of(path);
            if (findPresent(conflictsFile, paths).isEmpty()) {
                return;
            }

            removeInternal(conflictsFile, paths);
        }
    }

    private void removeInternal(final File conflictsFile, final Set<String> paths) {
        removeInternal(conflictsFile, (path) -> paths.contains(path));
    }

    /**
     * @param pathMatcher a filter that's given conflict paths and must return true for each path
     *        that shall be removed, and false if the conflict shall be kept
     */
    private void removeInternal(final File conflictsFile, final Predicate<String> pathMatcher) {
        try {
            final File tmpFile = File.createTempFile(conflictsFile.getName(), ".tmp",
                    conflictsFile.getParentFile());

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(conflictsFile), Charsets.UTF_8))) {

                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(tmpFile), Charsets.UTF_8))) {

                    Stream<String> stream;
                    stream = reader.lines().sequential()
                            .filter((line) -> pathMatcher.negate().test(readPath(line)));

                    stream.forEach((l) -> {
                        try {
                            writer.append(l);
                            writer.append('\n');
                        } catch (Exception e) {
                            throw Throwables.propagate(e);
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
            } catch (Exception e) {
                tmpFile.delete();
                throw Throwables.propagate(e);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
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
                        Conflict c = FileConflictsDatabase.valueOf(s);
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
        if (!isOpen()) {
            return null;
        }
        File conflictsFile;
        if (namespace == null) {
            conflictsFile = new File(repositoryDirectory, "conflicts");
        } else {
            conflictsFile = new File(new File(repositoryDirectory, "txconflicts"), namespace);
        }
        return conflictsFile;
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
            if (conflictsFile == null || conflictsFile.length() == 0L) {
                return;
            }
            final int batchSize = 100_000;
            Iterable<List<String>> partitions = Iterables.partition(paths, batchSize);
            for (List<String> p : partitions) {
                Set<String> matches = findPresent(conflictsFile, Sets.newHashSet(p));
                if (!matches.isEmpty()) {
                    removeInternal(conflictsFile, matches);
                }
            }
        }
    }

    @Override
    public Set<String> findConflicts(@Nullable String namespace, Set<String> paths) {
        checkNotNull(paths, "paths is null");
        final Object monitor = resolveConflictsMonitor(namespace);
        checkState(monitor != null,
                "Either not inside a repository directory or the staging area is closed");
        synchronized (monitor) {
            final File conflictsFile = resolveConflictsFile(namespace);
            if (conflictsFile == null || conflictsFile.length() == 0L) {
                return Collections.emptySet();
            }

            Set<String> matches = findPresent(conflictsFile, paths);
            return matches;
        }
    }

    private Set<String> findPresent(final File conflictsFile, final Set<String> queryPaths) {

        Set<String> present = Sets.newConcurrentHashSet();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(conflictsFile), Charsets.UTF_8))) {

            Stream<String> stream = reader.lines().parallel().map((line) -> readPath(line))
                    .filter((path) -> queryPaths.contains(path));

            stream.forEach((presentPath) -> present.add(presentPath));

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return present;
    }

    private static StringBuilder encode(Conflict c) {
        return new StringBuilder(c.getPath()).append('\t').append(c.getAncestor().toString())
                .append('\t').append(c.getOurs().toString()).append('\t')
                .append(c.getTheirs().toString());
    }

    private static String readPath(String line) {
        StringBuilder b = new StringBuilder();
        int index = line.indexOf('\t');
        Preconditions.checkArgument(index > -1, "wrong conflict definitions: %s", line);
        String path = line.substring(0, index);
        return path;
    }

    private static Conflict valueOf(String line) {
        String[] tokens = line.split("\t");
        try {
            Preconditions.checkArgument(tokens.length == 4, "wrong conflict definitions: %s", line);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw e;
        }
        String path = tokens[0];
        ObjectId ancestor = ObjectId.valueOf(tokens[1]);
        ObjectId ours = ObjectId.valueOf(tokens[2]);
        ObjectId theirs = ObjectId.valueOf(tokens[3]);
        return new Conflict(path, ancestor, ours, theirs);
    }

    @Override
    public void removeByPrefix(@Nullable String namespace, @Nullable String pathPrefix) {

        final Object monitor = resolveConflictsMonitor(namespace);
        checkState(monitor != null,
                "Either not inside a repository directory or the staging area is closed");
        synchronized (monitor) {
            final File conflictsFile = resolveConflictsFile(namespace);
            if (conflictsFile == null || conflictsFile.length() == 0L) {
                return;
            }
            Predicate<String> pathFilter = pathPrefix == null ? (p) -> true
                    : new PathFilter(pathPrefix);

            removeInternal(conflictsFile, pathFilter);
        }
    }
}
