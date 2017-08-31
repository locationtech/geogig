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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.rest.repository.InitCommandResource.INIT_CMD;
import static org.locationtech.geogig.web.api.RESTUtils.getStringAttribute;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.rest.repository.InitRequestUtil;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.restlet.data.Method;
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
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Iterator<String> findRepositories() {
        return resolver.listRepoNamesUnderRootURI(rootRepoURI).iterator();
    }

    private boolean isInitRequest(Request request) {
        // if the request is a PUT, and the request path ends in "init", it's an INIT request.
        if (Method.PUT.equals(request.getMethod())) {
            Map<String, Object> attributes = request.getAttributes();
            if (attributes != null && attributes.containsKey("command")) {
                return INIT_CMD.equals(attributes.get("command"));
            } else if (request.getResourceRef() != null) {
                String path = request.getResourceRef().getPath();
                return path != null && path.contains(INIT_CMD);
            }
        }
        return false;
    }

    @Override
    public Optional<Repository> getGeogig(Request request) {
        final String repositoryName = getStringAttribute(request, "repository");
        if (null == repositoryName) {
            return Optional.absent();
        }
        if (isInitRequest(request)) {
            // init request, get a GeoGig repo based on the request
            Optional<Repository> initRepo = InitRequestHandler.createGeoGIG(request);
            if (initRepo.isPresent()) {
                // init request was sufficient
                return initRepo;
            }
        }
        return Optional.of(getGeogig(repositoryName));
    }

    public Repository getGeogig(final String repositoryName) {
        try {
            return repositories.get(repositoryName);
        } catch (ExecutionException e) {
            LOG.warn("Unable to load repository {}", repositoryName, e);
            throw Throwables.propagate(e);
        }
    }

    private static final RemovalListener<String, Repository> removalListener = new RemovalListener<String, Repository>() {
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
                .removalListener(removalListener)//
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
                        throw Throwables.propagate(e);
                    }
                    break;
                }
            }
        }

        return repository;
    }

    @Override
    public void delete(Request request) {
        Optional<Repository> geogig = getGeogig(request);
        Preconditions.checkState(geogig.isPresent(), "No repository to delete.");

        final String repositoryName = getStringAttribute(request, "repository");
        Repository ggig = geogig.get();
        Optional<URI> repoUri = ggig.command(ResolveGeogigURI.class).call();
        Preconditions.checkState(repoUri.isPresent(), "No repository to delete.");

        ggig.close();
        try {
            GeoGIG.delete(repoUri.get());
            this.repositories.invalidate(repositoryName);
        } catch (Exception e) {
            Throwables.propagate(e);
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

        private static Optional<Repository> createGeoGIG(Request request) {
            try {
                final Hints hints = InitRequestUtil.createHintsFromRequest(request);
                final Optional<Serializable> repositoryUri = hints.get(Hints.REPOSITORY_URL);
                if (!repositoryUri.isPresent()) {
                    // didn't successfully build a Repository URI
                    return Optional.absent();
                }
                final URI repoUri = URI.create(repositoryUri.get().toString());
                final RepositoryResolver resolver = RepositoryResolver.lookup(repoUri);
                final Repository repository = GlobalContextBuilder.builder().build(hints)
                        .repository();
                if (resolver.repoExists(repoUri)) {
                    // open it
                    repository.open();
                }
                // now build the repo with the Hints
                return Optional.fromNullable(repository);
            } catch (Exception ex) {
                Throwables.propagate(ex);
            }
            return Optional.absent();
        }
    }
}