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

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;

public class FileRepositoryResolver extends RepositoryResolver {

    @Override
    public boolean canHandle(URI repoURI) {
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

    @Override
    public boolean canHandleURIScheme(String scheme) {
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

    @Override
    public boolean repoExists(URI repoURI) {
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
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
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

    @Override
    public URI buildRepoURI(URI rootRepoURI, String repoName) {
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

    @Override
    public List<String> listRepoNamesUnderRootURI(URI rootRepoURI) {
        final File rootDirectory = toFile(rootRepoURI);

        return Lists.newLinkedList(reposUnderRootDirectory(rootDirectory).keySet());
    }

    @Override
    public String getName(URI repoURI) {
        String repoName = null;

        // if the repo exists, get the name from it
        if (repoExists(repoURI)) {
            // it exists, load it and fetch the name
            Hints hints = Hints.readOnly().uri(repoURI);
            Context context = GlobalContextBuilder.builder().build(hints);
            ConfigDatabase configDatabase = context.configDatabase();
            repoName = configDatabase.get("repo.name").orNull();
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

    @Override
    public void initialize(URI repoURI, Context repoContext) throws IllegalArgumentException {

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

    @Override
    public ConfigDatabase getConfigDatabase(URI repoURI, Context repoContext, boolean rootUri) {
        Hints hints = new Hints().uri(repoURI);
        Platform platform = repoContext.platform();
        return new IniFileConfigDatabase(platform, hints, rootUri);
    }

    @Override
    public Repository open(URI repositoryLocation) throws RepositoryConnectionException {
        Preconditions.checkArgument(canHandle(repositoryLocation), "Not a file repository: %s",
                repositoryLocation.getScheme());

        if (!repoExists(repositoryLocation)) {
            throw new RepositoryConnectionException(
                    "The provided location is not a geogig repository");
        }

        Context context = GlobalContextBuilder.builder().build(new Hints().uri(repositoryLocation));
        GeoGIG geoGIG = new GeoGIG(context);

        Repository repository = geoGIG.getRepository();
        repository.open();

        return repository;
    }

    @Override
    public boolean delete(URI repositoryLocation) throws Exception {
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
}
