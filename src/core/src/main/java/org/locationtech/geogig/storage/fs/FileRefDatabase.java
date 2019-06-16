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
import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.model.Ref.append;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.storage.impl.AbstractRefDatabase;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import lombok.NonNull;

/**
 * Provides an implementation of a GeoGig ref database that utilizes the file system for the storage
 * of refs.
 */
public class FileRefDatabase extends AbstractRefDatabase {

    private static final Charset CHARSET = Charset.forName("UTF-8");

    private final File refsDirectory;

    /**
     * Constructs a new {@code FileRefDatabase} with the given base directory (e.g.
     * {@code /repo/.geogig/refs}).
     */
    public FileRefDatabase(@NonNull File refsDirectory) {
        this(refsDirectory, false);
    }

    public FileRefDatabase(@NonNull File refsDirectory, boolean readOnly) {
        super(readOnly);
        this.refsDirectory = refsDirectory;
    }

    /**
     * Creates the reference database.
     */
    @Override
    public void open() {
        if (isOpen()) {
            return;
        }
        File refs = this.refsDirectory;
        if (!refs.isDirectory()) {
            checkWritable();
            if (!refs.mkdir()) {
                throw new IllegalStateException(
                        "Cannot create refs directory '" + refs.getAbsolutePath() + "'");
            }
        }
        super.open();
    }

    /**
     * @param name the name of the ref (e.g. {@code "refs/remotes/origin"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     */
    @Override
    public String getRef(@NonNull String name) {
        String value = getInternal(name);
        if (value == null) {
            return null;
        }
        try {
            ObjectId.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw e;
        }
        return value;
    }

    /**
     * @param name the name of the symbolic ref (e.g. {@code "HEAD"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     */
    @Override
    public String getSymRef(@NonNull String name) {
        String value = getInternal(name);
        if (value == null) {
            return null;
        }
        if (!value.startsWith("ref: ")) {
            throw new IllegalArgumentException(name + " is not a symbolic ref: '" + value + "'");
        }
        value = value.substring("ref: ".length());
        return value;
    }

    private String getInternal(String name) {
        File refFile = toFile(name);
        if (!refFile.exists() || refFile.isDirectory()) {
            return null;
        }
        String value = readRef(refFile);
        return value;
    }

    /**
     * @param refName the name of the ref
     * @param refValue the value of the ref, must be the hex encoding of an {@link ObjectId}
     * @return {@code null} if the ref didn't exist already, its old value otherwise
     */
    @Override
    public void putRef(@NonNull String refName, @NonNull String refValue) {
        checkWritable();
        try {
            ObjectId.valueOf(refValue);
        } catch (IllegalArgumentException e) {
            throw e;
        }
        store(refName, refValue);
    }

    /**
     * @param name the name of the symbolic ref
     * @param val the value of the symbolic ref
     * @return {@code null} if the ref didn't exist already, its old value otherwise
     */
    @Override
    public void putSymRef(@NonNull String name, @NonNull String val) {
        checkArgument(!name.equals(val), "Trying to store cyclic symbolic ref: %s", name);
        checkArgument(!name.startsWith("ref: "),
                "Wrong value, should not contain 'ref: ': %s -> '%s'", name, val);
        checkWritable();
        val = "ref: " + val;
        store(name, val);
    }

    /**
     * @param refName the name of the ref to remove (e.g. {@code "HEAD"},
     *        {@code "refs/remotes/origin"}, etc).
     * @return the value of the ref before removing it, or {@code null} if it didn't exist
     */
    @Override
    public String remove(@NonNull String refName) {
        checkWritable();
        File refFile = toFile(refName);
        String oldRef;
        if (refFile.exists()) {
            oldRef = readRef(refFile);
            if (oldRef.startsWith("ref: ")) {
                oldRef = oldRef.substring("ref: ".length());
            }
            if (!refFile.delete()) {
                throw new RuntimeException(
                        "Unable to delete ref file '" + refFile.getAbsolutePath() + "'");
            }
        } else {
            oldRef = null;
        }
        return oldRef;
    }

    /**
     * @param refPath
     * @return
     */
    private File toFile(String refPath) {
        String[] path = refPath.split("/");

        File file = this.refsDirectory;
        for (String subpath : path) {
            file = new File(file, subpath);
        }
        return file;
    }

    private String readRef(final File refFile) {
        // make sure no other thread changes the ref as we read it
        try {
            synchronized (refFile.getCanonicalPath().intern()) {
                return Files.asCharSource(refFile, CHARSET).readFirstLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param refName the full name of the ref (e.g.
     *        {@code refs/heads/master, HEAD, transaction/<tx id>/refs/orig/refs/heads/master, etc.}
     * @param refValue
     */
    private void store(String refName, String refValue) {
        final File refFile = toFile(refName);
        try {
            synchronized (refFile.getCanonicalPath().intern()) {
                Files.createParentDirs(refFile);
                checkState(refFile.exists() || refFile.createNewFile(),
                        "Unable to create file for ref %s", refFile);

                FileOutputStream fout = new FileOutputStream(refFile);
                try {
                    FileDescriptor fd = fout.getFD();
                    fout.write((refValue + "\n").getBytes(CHARSET));
                    fout.flush();
                    // force change to be persisted to disk
                    fd.sync();
                } finally {
                    fout.close();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, String> getAll() {
        Builder<String, String> builder = ImmutableMap.<String, String> builder();

        builder.putAll(getAll(Ref.HEADS_PREFIX));
        builder.putAll(getAll(Ref.TAGS_PREFIX));
        builder.putAll(getAll(Ref.REMOTES_PREFIX));

        addIfPresent(builder, Ref.CHERRY_PICK_HEAD);
        addIfPresent(builder, Ref.ORIG_HEAD);
        addIfPresent(builder, Ref.HEAD);
        addIfPresent(builder, Ref.WORK_HEAD);
        addIfPresent(builder, Ref.STAGE_HEAD);
        addIfPresent(builder, Ref.MERGE_HEAD);

        ImmutableMap<String, String> all = builder.build();
        return all;
    }

    private void addIfPresent(Builder<String, String> builder, String name) {
        String value = getInternal(name);
        if (value != null) {
            if (value.startsWith("ref: ")) {
                value = value.substring("ref: ".length());
            }
            builder.put(name, value);
        }
    }

    /**
     * @return all references under the specified namespace
     */
    @Override
    public Map<String, String> getAll(String namespace) {
        Preconditions.checkNotNull(namespace, "namespace can't be null");
        File refsRoot = this.refsDirectory;
        if (namespace.endsWith("/")) {
            namespace = namespace.substring(0, namespace.length() - 1);
        }
        Map<String, String> refs = Maps.newTreeMap();
        findRefs(refsRoot, namespace, refs);
        return ImmutableMap.copyOf(refs);
    }

    private void findRefs(final File refsRoot, final String namespace,
            final Map<String, String> target) {
        String[] subdirs = namespace.split("/");
        File nsDir = refsRoot;
        for (String subdir : subdirs) {
            nsDir = new File(nsDir, subdir);
            if (!nsDir.exists() || !nsDir.isDirectory()) {
                return;
            }
        }
        addAll(nsDir, namespace, target);
    }

    private void addAll(File nsDir, String prefix,
            Map<String/* name */, String/* value */> target) {
        File[] children = nsDir.listFiles();
        for (File f : children) {
            final String fileName = f.getName();
            if (f.isDirectory()) {
                String namespace = append(prefix, fileName);
                addAll(f, namespace, target);
            } else if (fileName.length() == 0 || fileName.charAt(0) != '.') {
                String refName = append(prefix, fileName);
                String refValue = readRef(f);
                if (refValue.startsWith("ref: ")) {
                    refValue = refValue.substring("ref: ".length());
                }

                target.put(refName, refValue);
            }
        }
    }

    @Override
    public Map<String, String> removeAll(String namespace) {
        Preconditions.checkNotNull(namespace, "provided namespace is null");
        checkWritable();
        Map<String, String> oldvalues = getAll(namespace);
        final File file = toFile(namespace);
        if (file.exists() && file.isDirectory()) {
            deleteDir(file);
        }
        return oldvalues;
    }

    /**
     * @param directory
     */
    private void deleteDir(final File directory) {
        if (!directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            throw new RuntimeException("Unable to list files of " + directory);
        }
        for (File f : files) {
            if (f.isDirectory()) {
                deleteDir(f);
            } else if (!f.delete()) {
                throw new RuntimeException("Unable to delete file " + f.getAbsolutePath());
            }
        }
        if (!directory.delete()) {
            throw new RuntimeException("Unable to delete directory " + directory.getAbsolutePath());
        }
    }

    @Override
    public String toString() {
        return String.format("%s[geogig dir: %s]", getClass().getSimpleName(), refsDirectory);
    }
}
