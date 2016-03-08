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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
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

/**
 * {@link RepositoryProvider} that looks up the coresponding {@link GeoGIG} instance to a given
 * {@link Request} by asking the geoserver's {@link RepositoryManager}
 */
public class DirectoryRepositoryProvider implements RepositoryProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryRepositoryProvider.class);

    private LoadingCache<String, GeoGIG> repositories;

    private final File repositoriesDirectory;

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
            loadReposiroties(repositoriesDirectory);
        } catch (IOException e) {
            repositories.invalidateAll();
            throw Throwables.propagate(e);
        }
    }

    private void loadReposiroties(File baseDir) throws IOException {
        LOG.debug("Loading repositories under " + baseDir);

        final List<Path> subdirs = new ArrayList<Path>();

        Files.walkFileTree(baseDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {

                if (dir.getFileName().toString().startsWith(".")) {
                    LOG.info("Ignoring hidden directory " + dir);
                } else {
                    subdirs.add(dir);
                }
                return FileVisitResult.SKIP_SUBTREE;
            }
        });

        for (Path dir : subdirs) {
            try {
                final String repoName = dir.getFileName().toString();
                GeoGIG repo = repositories.get(repoName);
                @Nullable
                Repository repository = repo.getRepository();
                if (repository == null) {
                    LOG.info("Ignoring non repository directory " + dir.getFileName());
                    repositories.invalidate(repoName);
                } else {
                    LOG.info("Loaded repository " + dir.getFileName());
                }
            } catch (ExecutionException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public Optional<GeoGIG> getGeogig(Request request) {
        final String repositoryId = getStringAttribute(request, "repository");
        if (null == repositoryId) {
            return Optional.absent();
        }

        try {
            GeoGIG repo = repositories.get(repositoryId);
            return Optional.of(repo);
        } catch (ExecutionException e) {
            LOG.warn("Unable to load repository {}", repositoryId, e);
            throw Throwables.propagate(e);
        }
    }

    private static final RemovalListener<String, GeoGIG> removalListener = new RemovalListener<String, GeoGIG>() {
        @Override
        public void onRemoval(RemovalNotification<String, GeoGIG> notification) {
            final RemovalCause cause = notification.getCause();
            final String repositoryId = notification.getKey();
            final GeoGIG repo = notification.getValue();
            LOG.info("Disposing repository {}. Cause: " + cause(cause));
            try {
                repo.close();
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

    private LoadingCache<String, GeoGIG> buildCache(final File baseDir) throws IOException {

        CacheLoader<String, GeoGIG> loader = new CacheLoader<String, GeoGIG>() {

            private final Path directory = baseDir.toPath();

            @Override
            public GeoGIG load(final String repoName) throws Exception {
                Path repoPath = directory.resolve(repoName);
                GeoGIG repo = loadGeoGIG(repoPath);
                return repo;
            }

        };

        LoadingCache<String, GeoGIG> cache = CacheBuilder.newBuilder()//
                .concurrencyLevel(1)//
                .expireAfterAccess(1, TimeUnit.MINUTES)//
                .maximumSize(1024)//
                .removalListener(removalListener)//
                .build(loader);

        return cache;
    }

    @VisibleForTesting
    GeoGIG loadGeoGIG(final Path repo) {
        LOG.info("Loading repository " + repo);
        Hints hints = new Hints();
        final URI repoURI = repo.toUri();
        hints.set(Hints.REPOSITORY_URL, repoURI);

        Context context = GlobalContextBuilder.builder.build(hints);

        GeoGIG geogig = new GeoGIG(context, repo.toFile());

        if (geogig.command(ResolveGeogigURI.class).call().isPresent()) {
            Repository repository = geogig.getRepository();
            URI location = repository.getLocation();
            Preconditions.checkNotNull(location);
            Preconditions.checkState(repoURI.equals(location));
            LOG.info("Loaded existing repository " + repo);
        } else {
            LOG.info("Using non existing repository " + repo
                    + ". Init will be the only command supported");
        }

        return geogig;
    }

}