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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.transaction.TransactionBlobStore;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import lombok.NonNull;

/**
 * Default {@link TransactionBlobStore} implementation, stores blobs directly inside the
 * {@code .geogig/} directory named after the {@link #putBlob} {@code path} arguments, inside the
 * {@code .goegig} subdirectory named after the {@code namespace} argument.
 */
public class FileBlobStore implements TransactionBlobStore {

    private static final String CURRENT_DIR = ".";

    private File repositoryDirectory;

    public FileBlobStore(final Platform platform) {
        Optional<URI> repoPath = new ResolveGeogigURI(platform, null).call();
        Preconditions.checkState(repoPath.isPresent(), "Not inside a geogig directory");
        URI uri = repoPath.get();
        Preconditions.checkState("file".equals(uri.getScheme()),
                "Repository URL is not file system based: %s", uri);
        File repoLocation = new File(uri);
        this.repositoryDirectory = repoLocation;
    }

    public FileBlobStore(File repositoryDirectory) {
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

    public @Override Optional<byte[]> getBlob(String path) {
        return getBlob(CURRENT_DIR, path);
    }

    public @Override Optional<InputStream> getBlobAsStream(String path) {
        return getBlobAsStream(CURRENT_DIR, path);
    }

    public @Override void putBlob(String path, byte[] blob) {
        putBlob(CURRENT_DIR, path, blob);
    }

    public @Override void putBlob(String path, InputStream blob) {
        putBlob(CURRENT_DIR, path, blob);
    }

    public @Override void removeBlob(String path) {
        removeBlob(CURRENT_DIR, path);
    }

    public @Override Optional<byte[]> getBlob(String namespace, String path) {
        File f = toFile(namespace, path);
        byte[] bytes = null;
        if (f.exists()) {
            try {
                bytes = Files.toByteArray(f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return Optional.ofNullable(bytes);
    }

    public @Override Optional<InputStream> getBlobAsStream(String namespace, String path) {
        File f = toFile(namespace, path);
        InputStream in = null;
        if (f.exists()) {
            try {
                in = new FileInputStream(f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return Optional.ofNullable(in);
    }

    public @Override void putBlob(String namespace, String path, byte[] blob) {
        File f = toFile(namespace, path);
        f.getParentFile().mkdirs();
        try {
            Files.write(blob, f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public @Override void putBlob(String namespace, String path, InputStream blob) {
        File f = toFile(namespace, path);
        f.getParentFile().mkdirs();
        try (OutputStream to = new FileOutputStream(f)) {
            ByteStreams.copy(blob, to);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public @Override void removeBlob(String namespace, String path) {
        File f = toFile(namespace, path);
        if (f.exists() && !f.delete()) {
            throw new IllegalStateException("Unable to delete file " + f);
        }
    }

    public @Override void removeBlobs(@NonNull String namespace) {
        Path namespacePath = repositoryDirectory.toPath().resolve(namespace);
        File namespaceDir = namespacePath.toFile();
        if (namespaceDir.exists() && namespaceDir.isDirectory()) {
            try {
                java.nio.file.Files.walkFileTree(namespacePath, new SimpleFileVisitor<Path>() {
                    public @Override FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        java.nio.file.Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    public @Override FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        java.nio.file.Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }

                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private File toFile(@NonNull String namespace, @NonNull String path) {
        Path filePath = repositoryDirectory.toPath().resolve(namespace).resolve(path);
        File file = filePath.toFile().getAbsoluteFile();
        return file;
    }
}
