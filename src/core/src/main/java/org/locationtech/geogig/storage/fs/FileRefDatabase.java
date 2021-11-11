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

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.SYNC;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.locationtech.geogig.model.Ref.CHERRY_PICK_HEAD;
import static org.locationtech.geogig.model.Ref.HEAD;
import static org.locationtech.geogig.model.Ref.MERGE_HEAD;
import static org.locationtech.geogig.model.Ref.ORIG_HEAD;
import static org.locationtech.geogig.model.Ref.STAGE_HEAD;
import static org.locationtech.geogig.model.Ref.WORK_HEAD;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.impl.SimpleLockingRefDatabase;

import com.google.common.collect.Streams;

import lombok.NonNull;

/**
 * Provides an implementation of a GeoGig ref database that utilizes the file system for the storage
 * of refs.
 */
public class FileRefDatabase extends SimpleLockingRefDatabase {

    private static final List<String> NO_NS_NAMES = Arrays.asList(CHERRY_PICK_HEAD, ORIG_HEAD, HEAD,
            WORK_HEAD, STAGE_HEAD, MERGE_HEAD);

    private final Path refsDirectory;

    /**
     * Constructs a new {@code FileRefDatabase} with the given base directory (e.g.
     * {@code /repo/.geogig/refs}).
     */
    public FileRefDatabase(@NonNull File refsDirectory) {
        this(refsDirectory, false);
    }

    public FileRefDatabase(@NonNull File refsDirectory, boolean readOnly) {
        super(readOnly);
        this.refsDirectory = refsDirectory.toPath();
    }

    /**
     * Creates the reference database.
     */
    public @Override void open() {
        if (isOpen()) {
            return;
        }
        Path refs = this.refsDirectory;
        if (!Files.isDirectory(refs)) {
            Preconditions.checkState(!isReadOnly(),
                    "Database does not exist and readOnly access requested");
            try {
                Files.createDirectory(refs);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Cannot create refs directory '" + refs.toString() + "'", e);
            }
        }
        super.open();
    }

    public @Override Optional<Ref> get(@NonNull String name) {
        checkOpen();
        return Optional.ofNullable(getInternal(name));
    }

    public @Override @NonNull RefChange put(@NonNull Ref ref) {
        checkWritable();
        Ref current;
        try {
            current = getInternal(ref.getName());
        } catch (IllegalStateException symRefTargetNotFound) {
            current = null;
        }
        String value;
        if (ref instanceof SymRef) {
            value = "ref: " + ref.peel().getName();
        } else {
            value = ref.getObjectId().toString();
        }
        store(ref.getName(), value);
        return RefChange.of(ref.getName(), current, ref);
    }

    public @Override @NonNull List<RefChange> putAll(@NonNull Iterable<Ref> refs) {
        checkWritable();
        return Streams.stream(refs).map(this::put).collect(Collectors.toList());
    }

    public @Override @NonNull RefChange putRef(@NonNull String name, @NonNull ObjectId id) {
        return put(new Ref(name, id));
    }

    public @Override @NonNull RefChange putSymRef(@NonNull String name, @NonNull String target) {
        Ref targetRef = get(target).orElseThrow(
                () -> new IllegalArgumentException("Target ref does not exist: " + target));
        return put(new SymRef(name, targetRef));
    }

    public @Override @NonNull RefChange delete(@NonNull String refName) {
        checkWritable();
        Optional<Ref> oldRef;
        try {
            oldRef = get(refName);
        } catch (IllegalStateException symRefPointsToNonExistingRef) {
            oldRef = Optional.empty();
        }
        if (oldRef.isPresent()) {
            Path refFile = toFile(refName);
            try {
                if (!Files.deleteIfExists(refFile)) {
                    throw new RuntimeException(
                            "Unable to delete ref file '" + refFile.toAbsolutePath() + "'");
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return RefChange.of(refName, oldRef, Optional.empty());
    }

    public @Override @NonNull RefChange delete(@NonNull Ref ref) {
        return delete(ref.getName());
    }

    public @Override List<Ref> deleteAll(@NonNull String namespace) {
        List<Ref> matches = getAll(namespace);
        matches.forEach(this::delete);
        return matches;
    }

    private Path toFile(String refPath) {
        return this.refsDirectory.resolve(refPath);
    }

    private String readRef(final Path refFile) {
        try {
            return Files.readAllLines(refFile, StandardCharsets.UTF_8).get(0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Ref getInternal(String name) {
        Path refFile = toFile(name);
        if (!Files.isRegularFile(refFile)) {
            return null;
        }
        String value = readRef(refFile);
        if (value.startsWith("ref: ")) {
            String targetName = value.substring("ref: ".length());
            Ref target = get(targetName).orElseThrow(() -> new IllegalStateException(String
                    .format("SymRef %s points to a non exsisting ref: %s", name, targetName)));
            return new SymRef(name, target);

        }
        return new Ref(name, ObjectId.valueOf(value));
    }

    private void store(String refName, String refValue) {
        final Path refFile = toFile(refName);
        try {
            Files.createDirectories(refFile.getParent());
            Files.write(refFile, refValue.getBytes(StandardCharsets.UTF_8), //
                    CREATE, TRUNCATE_EXISTING, WRITE, SYNC);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public @Override List<Ref> getAll() {
        checkOpen();
        List<Ref> all = new ArrayList<>();

        all.addAll(getAll(Ref.HEADS_PREFIX));
        all.addAll(getAll(Ref.TAGS_PREFIX));
        all.addAll(getAll(Ref.REMOTES_PREFIX));

        List<Ref> commonNamespacelessRefs = getAllPresent(NO_NS_NAMES);
        all.addAll(commonNamespacelessRefs);
        return all;
    }

    public @Override List<Ref> getAll(@NonNull String namespace) {
        checkOpen();
        if ("".equals(namespace)) {
            return getAll();
        }
        final List<String> names = new ArrayList<>();
        final Path nsRoot = this.refsDirectory.resolve(namespace);
        if (Files.isDirectory(nsRoot)) {
            try {
                FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        String name = refsDirectory.relativize(file).toString().replace('\\', '/');
                        names.add(name);
                        return FileVisitResult.CONTINUE;
                    }
                };
                Files.walkFileTree(nsRoot, visitor);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return getAllPresent(names);
    }

    public @Override List<Ref> getAllPresent(@NonNull Iterable<String> names) {
        checkOpen();
        return Streams.stream(names).parallel().map(this::getInternal).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public @Override String toString() {
        return String.format("%s[geogig dir: %s]", getClass().getSimpleName(), refsDirectory);
    }

    public @Override @NonNull List<RefChange> delete(@NonNull Iterable<String> refNames) {
        checkWritable();
        return Streams.stream(refNames).map(this::delete).collect(Collectors.toList());
    }

    public @Override @NonNull List<Ref> deleteAll() {
        checkWritable();
        List<Ref> all = getAll();
        all.forEach(this::delete);
        return all;
    }
}
