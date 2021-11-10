/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;

import lombok.NonNull;

public abstract class FileRepositoryResolver implements RepositoryResolver {

    public @Override boolean canHandle(@NonNull URI repoURI) {
        String scheme = repoURI.getScheme();
        if (null == scheme) {
            File file = toFile(repoURI);
            boolean exists = file.exists();
            boolean directory = file.isDirectory();
            boolean parentExists = file.getParentFile() != null ? file.getParentFile().exists()
                    : false;
            return (exists && directory) || parentExists;
        }
        return canHandleURIScheme(scheme);
    }

    public @Override boolean canHandleURIScheme(String scheme) {
        return "file".equals(scheme);
    }

    private File toFile(URI repoURI) {
        String scheme = repoURI.getScheme();
        File file;
        if (scheme == null) {
            file = new File(repoURI.toString());
        } else {
            file = new File(repoURI);
        }
        return file;
    }

    public @Override boolean repoExists(@NonNull URI repoURI) {
        File directory = toFile(repoURI);
        Optional<URI> lookup = ResolveGeogigURI.lookup(directory);
        return lookup.isPresent();
    }

    private Map<String, String> reposUnderRootDirectory(File rootDirectory) {
        Path basePath = rootDirectory.toPath();
        final Map<String, String> repoNameToRepoIds = new HashMap<String, String>();
        final List<Path> subdirs = new ArrayList<Path>();
        try {
            Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                public @Override FileVisitResult preVisitDirectory(Path dir,
                        BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(basePath)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!dir.getFileName().toString().startsWith(".")) {
                        subdirs.add(dir);
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Path dir : subdirs) {
            final String repoId = dir.getFileName().toString();
            if (repoExists(dir.toUri())) {
                repoNameToRepoIds.put(getName(dir.toUri()), repoId);
            }
        }

        return repoNameToRepoIds;
    }

    public @Override URI buildRepoURI(@NonNull URI rootRepoURI, @NonNull String repoName) {
        final File rootDirectory = toFile(rootRepoURI);
        // Look up repo ID for repo name, if it does not exist, generate a new one
        String repoId = reposUnderRootDirectory(rootDirectory).get(repoName);
        if (repoId == null) {
            SecureRandom rnd = new SecureRandom();
            byte[] bytes = new byte[128];
            rnd.nextBytes(bytes);
            repoId = Hashing.sipHash24().hashBytes(bytes).toString();
        }

        File repoDirectory = new File(rootDirectory, repoId);
        return repoDirectory.toURI();
    }

    public @Override List<String> listRepoNamesUnderRootURI(@NonNull URI rootRepoURI) {
        final File rootDirectory = toFile(rootRepoURI);

        return reposUnderRootDirectory(rootDirectory).keySet().stream()
                .collect(Collectors.toList());
    }

    public @Override String getName(@NonNull URI repoURI) {
        String repoName = null;

        // if the repo exists, get the name from it
        if (repoExists(repoURI)) {
            // it exists, load it and fetch the name
            Hints hints = Hints.readOnly().uri(repoURI);
            Context context = GlobalContextBuilder.builder().build(hints);
            ConfigDatabase configDatabase = context.configDatabase();
            repoName = configDatabase.get("repo.name").orElse(null);
        }
        if (repoName == null) {
            // the repo doesn't exist or name is not configured, derive the name from the
            // location
            File file = toFile(repoURI);
            try {
                file = file.getCanonicalFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (file.getName().equals(".geogig")) {
                file = file.getParentFile();
            }
            repoName = file.getName();
        }

        return repoName;
    }

    public @Override void initialize(@NonNull URI repoURI) throws IllegalArgumentException {

        final boolean repoExisted = repoExists(repoURI);

        final File targetDir = toFile(repoURI);

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IllegalArgumentException(
                    "Can't create directory " + targetDir.getAbsolutePath());
        }

        final File envHome;
        if (repoExisted) {
            // we're at either the repo working dir or a subdirectory of it
            envHome = targetDir;
        } else {
            if (targetDir.getName().equals(".geogig")) {
                envHome = targetDir;
            } else {
                envHome = new File(targetDir, ".geogig");
                if (!envHome.mkdirs()) {
                    throw new RuntimeException("Unable to create .geogig directory at '"
                            + envHome.getAbsolutePath() + "'");
                }
            }
        }
    }

    public @Override Repository open(@NonNull URI repositoryURI)
            throws RepositoryConnectionException {
        return open(repositoryURI, Hints.readWrite());
    }

    public @Override Repository open(@NonNull URI repositoryLocation, @NonNull Hints hints)
            throws RepositoryConnectionException {
        Preconditions.checkArgument(canHandle(repositoryLocation), "Not a file repository: %s",
                repositoryLocation.getScheme());

        if (!repoExists(repositoryLocation)) {
            throw new RepositoryConnectionException(
                    "The provided location is not a geogig repository");
        }

        Context context = GlobalContextBuilder.builder().build(hints.uri(repositoryLocation));
        Geogig geoGIG = Geogig.of(context);

        Repository repository = geoGIG.getRepository();
        repository.open();

        return repository;
    }

    public @Override boolean delete(@NonNull URI repositoryLocation) throws Exception {
        Preconditions.checkArgument(canHandle(repositoryLocation), "Not a file repository: %s",
                repositoryLocation);

        if (!repoExists(repositoryLocation)) {
            return false;
        }

        File workingDir = toFile(repositoryLocation);
        if (workingDir.getName().equals(".geogig")) {
            workingDir = workingDir.getParentFile();
        }
        // If there are other files in the repository folder, only delete the .geogig directory.
        if (workingDir.listFiles().length > 1) {
            workingDir = new File(workingDir, ".geogig");
        }
        deleteDirectoryAndContents(workingDir);

        return true;
    }

    private void deleteDirectoryAndContents(File directory) throws IOException {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                deleteDirectoryAndContents(file);
            } else {
                if (!file.delete()) {
                    throw new IOException("Unable to delete file: " + file.getCanonicalPath());
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Unable to delete directory: " + directory.getCanonicalPath());
        }
    }

    public @Override URI getRootURI(@NonNull URI repoURI) {
        File dotgig = resolveDotGeogigDirectory(repoURI);
        return dotgig.getParentFile().getParentFile().toURI();
    }

    protected File resolveDotGeogigDirectory(@NonNull URI repoURI) {
        File dir = toFile(repoURI);
        if (!".geogig".equals(dir.getName())) {
            dir = new File(dir, ".geogig");
            if (!dir.isDirectory()) {
                throw new IllegalArgumentException(
                        String.format("Not a geogig directory: %s", repoURI));
            }
        }
        return dir;
    }

    public abstract @Override ConfigDatabase resolveConfigDatabase(@NonNull URI repoURI,
            @NonNull Context repoContext, boolean rootUri);

    public abstract @Override ObjectDatabase resolveObjectDatabase(@NonNull URI repoURI,
            Hints hints);

    public abstract @Override IndexDatabase resolveIndexDatabase(@NonNull URI repoURI, Hints hints);

    public abstract @Override RefDatabase resolveRefDatabase(@NonNull URI repoURI, Hints hints);

    public abstract @Override ConflictsDatabase resolveConflictsDatabase(@NonNull URI repoURI,
            Hints hints);
}
