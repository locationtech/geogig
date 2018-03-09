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
import static org.locationtech.geogig.model.Ref.append;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.impl.AbstractRefDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.inject.Inject;

/**
 * Provides an implementation of a GeoGig ref database that utilizes the file system for the storage
 * of refs.
 */
public class FileRefDatabase extends AbstractRefDatabase {

    private static final Charset CHARSET = Charset.forName("UTF-8");

    private final Platform platform;

    private final Hints hints;

    private final ConfigDatabase configDB;

    private File envHome;

    /**
     * Constructs a new {@code FileRefDatabase} with the given platform.
     * 
     * @param platform the platform to use
     */
    @Inject
    public FileRefDatabase(Platform platform, ConfigDatabase configDB, Hints hints) {
        this.platform = platform;
        this.configDB = configDB;
        this.hints = hints;
    }

    /**
     * Creates the reference database.
     */
    @Override
    public void create() {
        Optional<URI> envHome = new ResolveGeogigURI(platform, hints).call();
        checkState(envHome.isPresent(), "Not inside a geogig directory");

        final URI envURL = envHome.get();
        if (!"file".equals(envURL.getScheme())) {
            throw new UnsupportedOperationException(
                    "This References Database works only against file system repositories. "
                            + "Repository location: " + envURL);
        }
        File repoDir = new File(envURL);
        File refs = new File(repoDir, "refs");
        if (!refs.exists() && !refs.mkdir()) {
            throw new IllegalStateException(
                    "Cannot create refs directory '" + refs.getAbsolutePath() + "'");
        }
        this.envHome = repoDir;
    }

    /**
     * Closes the reference database.
     */
    @Override
    public void close() {
        // nothing to close
    }

    /**
     * @param name the name of the ref (e.g. {@code "refs/remotes/origin"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     */
    @Override
    public String getRef(String name) {
        checkNotNull(name);

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
    public String getSymRef(String name) {
        checkNotNull(name);
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
    public void putRef(String refName, String refValue) {
        checkNotNull(refName);
        checkNotNull(refValue);
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
    public void putSymRef(String name, String val) {
        checkNotNull(name);
        checkNotNull(val);
        checkArgument(!name.equals(val), "Trying to store cyclic symbolic ref: %s", name);
        checkArgument(!name.startsWith("ref: "),
                "Wrong value, should not contain 'ref: ': %s -> '%s'", name, val);
        val = "ref: " + val;
        store(name, val);
    }

    /**
     * @param refName the name of the ref to remove (e.g. {@code "HEAD"},
     *        {@code "refs/remotes/origin"}, etc).
     * @return the value of the ref before removing it, or {@code null} if it didn't exist
     */
    @Override
    public String remove(String refName) {
        checkNotNull(refName);
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

        File file = this.envHome;
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
        File refsRoot = this.envHome;
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
    public void configure() throws RepositoryConnectionException {
        StorageType.REF.configure(configDB, "file", "1.0");
    }

    @Override
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.REF.verify(configDB, "file", "1.0");
    }

    @Override
    public String toString() {
        return String.format("%s[geogig dir: %s]", getClass().getSimpleName(), envHome);
    }
}
