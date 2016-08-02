/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.rest.repository.RESTUtils.getStringAttribute;

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.GeoGIG;
import org.locationtech.geogig.repository.GlobalContextBuilder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.restlet.data.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.hash.Hashing;

/**
 * {@link RepositoryProvider} that looks up the coresponding {@link GeoGIG} instance to a given
 * {@link Request} by asking the geoserver's {@link RepositoryManager}
 */
public class DirectoryRepositoryProvider implements RepositoryProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryRepositoryProvider.class);

    private LoadingCache<String, Repository> repositories;

    private final File repositoriesDirectory;

    private Map<String, String> nameToRepoId = new ConcurrentHashMap<String, String>();

    public DirectoryRepositoryProvider(final File repositoriesDirectory) {

        checkNotNull(repositoriesDirectory, "repositoriesDirectory is null");
        checkArgument(repositoriesDirectory.exists(), "%s does not exist", repositoriesDirectory);
        checkArgument(repositoriesDirectory.isDirectory(), "%s does not a directory",
                repositoriesDirectory);
        checkArgument(repositoriesDirectory.canRead(), "%s can't be read by this process",
                repositoriesDirectory);

        this.repositoriesDirectory = repositoriesDirectory;

        try {
            this.repositories = buildCache(repositoriesDirectory);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        try {
            loadRepositories();
        } catch (IOException e) {
            repositories.invalidateAll();
            throw Throwables.propagate(e);
        }
    }

    private void loadRepositories() throws IOException {
        LOG.debug("Loading repositories under " + repositoriesDirectory);

        final List<Path> subdirs = new ArrayList<Path>();

        final Path basePath = repositoriesDirectory.toPath();
        Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (dir.equals(basePath)) {
                    return FileVisitResult.CONTINUE;
                }
                if (dir.getFileName().toString().startsWith(".")) {
                    LOG.info("Ignoring hidden directory " + dir);
                } else {
                    subdirs.add(dir);
                }
                return FileVisitResult.SKIP_SUBTREE;
            }
        });

        nameToRepoId.clear();

        for (Path dir : subdirs) {
            try {
                final String repoId = dir.getFileName().toString();
                Repository repository = repositories.get(repoId);
                if (repository == null || !repository.isOpen()) {
                    LOG.info("Ignoring non repository directory " + dir.getFileName());
                    repositories.invalidate(repoId);
                } else {
                    String repoName = repository.command(ResolveRepositoryName.class).call();
                    Preconditions.checkState(!nameToRepoId.containsKey(repoName),
                            "Duplicate repo name found: " + repoName);
                    nameToRepoId.put(repoName, repoId);
                    LOG.info("Loaded repository " + dir.getFileName());
                }
            } catch (ExecutionException e) {
                Throwables.propagate(e);
            }
        }
    }

    @Override
    public Iterator<String> findRepositories() {
        try {
            loadRepositories();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return nameToRepoId.keySet().iterator();
    }

    @Override
    public Optional<Repository> getGeogig(Request request) {
        final String repositoryName = getStringAttribute(request, "repository");
        if (null == repositoryName) {
            return Optional.absent();
        }

        return Optional.of(getGeogig(repositoryName));
    }

    public Repository getGeogig(final String repositoryName) {
        try {
            if (!nameToRepoId.containsKey(repositoryName)) {
                loadRepositories();
            }
            Repository repo = null;
            if (nameToRepoId.containsKey(repositoryName)) {
                String repoId = nameToRepoId.get(repositoryName);
                repo = repositories.getIfPresent(repoId);
                if (repo == null) {
                    repo = repositories.get(repoId);
                } else if (!repo.isOpen()) {
                    repositories.invalidate(repoId);
                    repo = repositories.get(repoId);
                }
            } else {
                SecureRandom rnd = new SecureRandom();
                byte[] bytes = new byte[128];
                rnd.nextBytes(bytes);
                String repoId = Hashing.sipHash24().hashBytes(bytes).toString();
                nameToRepoId.put(repositoryName, repoId);
                repo = repositories.get(repoId);
            }

            return repo;
        } catch (ExecutionException e) {
            LOG.warn("Unable to load repository {}", repositoryName, e);
            throw Throwables.propagate(e);
        } catch (IOException e) {
            repositories.invalidateAll();
            throw Throwables.propagate(e);
        }
    }

    private static final RemovalListener<String, Repository> removalListener = new RemovalListener<String, Repository>() {
        @Override
        public void onRemoval(RemovalNotification<String, Repository> notification) {
            final RemovalCause cause = notification.getCause();
            final String repositoryId = notification.getKey();
            final Repository repo = notification.getValue();
            LOG.info("Disposing repository {}. Cause: " + cause(cause));
            try {
                if (repo != null) {
                    repo.close();
                }
            } catch (RuntimeException e) {
                LOG.warn("Error closing repository {}", repositoryId, e);
            }
        }

        private String cause(RemovalCause cause) {
            switch (cause) {
            case COLLECTED:
                return "removed automatically because its key or value was garbage-collected";
            case EXPIRED:
                return "expiration timestamp has passed";
            case EXPLICIT:
                return "manually removed by remove() or invalidateAll()";
            case REPLACED:
                return "manually replaced";
            case SIZE:
                return "evicted due to cache size constraints";
            default:
                return "Unknown";
            }
        }
    };

    private LoadingCache<String, Repository> buildCache(final File baseDir) throws IOException {

        CacheLoader<String, Repository> loader = new CacheLoader<String, Repository>() {

            private final Path directory = baseDir.toPath();

            @Override
            public Repository load(final String repoId) throws Exception {
                Path repoPath = directory.resolve(repoId);
                Repository repo = loadGeoGIG(repoId, repoPath);
                return repo;
            }

        };

        LoadingCache<String, Repository> cache = CacheBuilder.newBuilder()//
                .concurrencyLevel(1)//
                .expireAfterAccess(1, TimeUnit.MINUTES)//
                .maximumSize(1024)//
                .removalListener(removalListener)//
                .build(loader);

        return cache;
    }

    @VisibleForTesting
    Repository loadGeoGIG(final String repoId, final Path repo) {
        LOG.info("Loading repository " + repo);
        Hints hints = new Hints();
        final URI repoURI = repo.toUri();
        hints.set(Hints.REPOSITORY_URL, repoURI);
        for (Entry<String, String> entry : nameToRepoId.entrySet()) {
            if (repoId.equals(entry.getValue())) {
                hints.set(Hints.REPOSITORY_NAME, entry.getKey());
                break;
            }
        }

        Context context = GlobalContextBuilder.builder().build(hints);

        Repository repository = context.repository();
        // if (repo.toFile().exists()) {
        // try {
        // repository.open();
        // } catch (Exception e) {
        // throw Throwables.propagate(e);
        // }
        // }

        Optional<URI> resolvedRepoURI = repository.command(ResolveGeogigURI.class).call();

        if (resolvedRepoURI.isPresent() && new File(resolvedRepoURI.get()).exists()) {
            if (!repository.isOpen()) {
                try {
                    repository.open();
                } catch (RepositoryConnectionException e) {
                    throw Throwables.propagate(e);
                }
            }
            URI location = repository.getLocation();
            Preconditions.checkNotNull(location);
            LOG.info("Loaded existing repository " + repo);
        } else {
            LOG.info("Using non existing repository " + repo
                    + ". Init will be the only command supported");
        }

        return repository;
    }

    @Override
    public void delete(Request request) {
        Optional<Repository> geogig = getGeogig(request);
        Preconditions.checkState(geogig.isPresent(), "No repository to delete.");

        final String repositoryName = getStringAttribute(request, "repository");
        final String repoId = nameToRepoId.get(repositoryName);
        Repository ggig = geogig.get();
        Optional<URI> repoUri = ggig.command(ResolveGeogigURI.class).call();
        Preconditions.checkState(repoUri.isPresent(), "No repository to delete.");

        ggig.close();
        try {
            GeoGIG.delete(repoUri.get());
            this.repositories.invalidate(repoId);
            nameToRepoId.remove(repositoryName);
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    @Override
    public void invalidate(String repoName) {
        if (nameToRepoId.containsKey(repoName)) {
            final String repoId = nameToRepoId.get(repoName);
            this.repositories.invalidate(repoId);
            nameToRepoId.remove(repoName);
        }
    }

}