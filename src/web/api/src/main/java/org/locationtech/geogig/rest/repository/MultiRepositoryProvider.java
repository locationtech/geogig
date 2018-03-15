/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
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
 * {@link HttpServletRequest} by asking the geoserver's {@link RepositoryManager}
 */
public class MultiRepositoryProvider implements RepositoryProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MultiRepositoryProvider.class);

    private LoadingCache<String, Repository> repositories;

    private final URI rootRepoURI;

    private final RepositoryResolver resolver;

    public MultiRepositoryProvider(final URI rootRepoURI) {
        checkNotNull(rootRepoURI, "root repo URI is null");

        resolver = RepositoryResolver.lookup(rootRepoURI);

        this.rootRepoURI = rootRepoURI;

        try {
            this.repositories = buildCache();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Iterator<String> findRepositories() {
        return resolver.listRepoNamesUnderRootURI(rootRepoURI).iterator();
    }

    @Override
    public Optional<Repository> getGeogig(final String repositoryName) {
        if (null == repositoryName) {
            return Optional.absent();
        }
        return Optional.of(getGeogigByName(repositoryName));
    }

    @Override
    public boolean hasGeoGig(String repositoryName) {
        if (null != repositoryName) {
            Iterator<String> findRepositories = findRepositories();
            while (findRepositories.hasNext()) {
                String next = findRepositories.next();
                if (next.equals(repositoryName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Repository createGeogig(final String repositoryName,
            final Map<String, String> parameters) {
        Optional<Repository> initRepo = InitRequestHandler.createGeoGIG(resolver, rootRepoURI,
                repositoryName, parameters);
        if (initRepo.isPresent()) {
            // init request was sufficient
            return initRepo.get();
        }
        return null;
    }

    public Repository getGeogigByName(final String repositoryName) {
        try {
            return repositories.get(repositoryName);
        } catch (ExecutionException e) {
            LOG.warn("Unable to load repository {}", repositoryName, e);
            throw new RuntimeException(e);
        }
    }

    private static final RemovalListener<String, Repository> REMOVAL_LISTENER = new RemovalListener<String, Repository>() {
        @Override
        public void onRemoval(RemovalNotification<String, Repository> notification) {
            final RemovalCause cause = notification.getCause();
            final String repositoryName = notification.getKey();
            final Repository repo = notification.getValue();
            LOG.info("Disposing repository {}. Cause: {}", repositoryName, cause(cause));
            try {
                if (repo != null && repo.isOpen()) {
                    repo.close();
                }
            } catch (RuntimeException e) {
                LOG.warn("Error closing repository {}", repositoryName, e);
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

    private LoadingCache<String, Repository> buildCache() throws IOException {

        CacheLoader<String, Repository> loader = new CacheLoader<String, Repository>() {

            @Override
            public Repository load(final String repoName) throws Exception {
                Repository repo = loadGeoGIG(repoName);
                return repo;
            }

        };

        LoadingCache<String, Repository> cache = CacheBuilder.newBuilder()//
                .concurrencyLevel(1)//
                .expireAfterAccess(1, TimeUnit.MINUTES)//
                .maximumSize(1024)//
                .removalListener(REMOVAL_LISTENER)//
                .build(loader);

        return cache;
    }

    @VisibleForTesting
    Repository loadGeoGIG(final String repoName) {
        LOG.info(
                "Loading repository " + repoName + " using " + resolver.getClass().getSimpleName());
        Hints hints = new Hints();
        final URI repoURI = resolver.buildRepoURI(rootRepoURI, repoName);
        hints.set(Hints.REPOSITORY_URL, repoURI);
        hints.set(Hints.REPOSITORY_NAME, repoName);

        Context context = GlobalContextBuilder.builder().build(hints);

        Repository repository = context.repository();

        if (!repository.isOpen()) {
            // Only open it if is was an existing repository.
            for (String existingRepo : resolver.listRepoNamesUnderRootURI(rootRepoURI)) {
                if (existingRepo.equals(repoName)) {
                    try {
                        repository.open();
                    } catch (RepositoryConnectionException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                }
            }
        }

        return repository;
    }

    @Override
    public void delete(String repoName) {
        Optional<Repository> geogig = getGeogig(repoName);
        Preconditions.checkState(geogig.isPresent(), "No repository to delete.");

        Repository ggig = geogig.get();
        Optional<URI> repoUri = ggig.command(ResolveGeogigURI.class).call();
        Preconditions.checkState(repoUri.isPresent(), "No repository to delete.");

        ggig.close();
        try {
            GeoGIG.delete(repoUri.get());
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }finally {
            this.repositories.invalidate(repoName);
        }
    }

    @Override
    public void invalidate(String repoName) {
        this.repositories.invalidate(repoName);
    }

    public void invalidateAll() {
        this.repositories.invalidateAll();
    }

    private static class InitRequestHandler {

        private static Optional<Repository> createGeoGIG(RepositoryResolver defaultResolver,
                URI rootRepoURI, String repositoryName,
                Map<String, String> parameters) {
            try {
                final Hints hints = InitRequestUtil.createHintsFromParameters(repositoryName,
                        parameters);
                Optional<Serializable> repositoryUri = hints.get(Hints.REPOSITORY_URL);
                if (!repositoryUri.isPresent()) {
                    URI repoURI = defaultResolver.buildRepoURI(rootRepoURI, repositoryName);
                    hints.set(Hints.REPOSITORY_URL, repoURI);
                    repositoryUri = hints.get(Hints.REPOSITORY_URL);
                }
                final URI repoUri = URI.create(repositoryUri.get().toString());
                final RepositoryResolver resolver = RepositoryResolver.lookup(repoUri);
                final Repository repository = GlobalContextBuilder.builder().build(hints).repository();
                if (resolver.repoExists(repoUri)) {
                    // open it
                    repository.open();
                }
                // now build the repo with the Hints
                return Optional.fromNullable(repository);
            } catch (IOException | URISyntaxException | RepositoryConnectionException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public String getMaskedLocationString(Repository repo, String repoName) {
        if (repo != null) {
            return repo.getLocation() != null ? repo.getLocation().toString() : null;
        }
        return null;
    }

    @Override
    public String getRepositoryId(String repoName) {
        // no ID applicable
        return null;
    }
}