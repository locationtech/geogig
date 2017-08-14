/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.restlet.data.Request;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;

public class SingleRepositoryProvider implements RepositoryProvider {

    private Repository repo;

    public SingleRepositoryProvider(Repository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<Repository> getGeogig(Request request) {
        return Optional.of(repo);
    }

    @Override
    public Optional<Repository> getGeogig(final String repositoryName) {
        return Optional.of(repo);
    }

    @Override
    public Repository createGeogig(final String repositoryName,
            final Map<String, String> parameters) {
        throw new UnsupportedOperationException(
                "Cannot create a repository with the single repo provider.");
    }

    @Override
    public void delete(Request request) {
        Repository repo = getGeogig(request).orNull();
        Preconditions.checkState(repo != null, "No repository to delete.");
        Optional<URI> repoUri = repo.command(ResolveGeogigURI.class).call();
        Preconditions.checkState(repoUri.isPresent(), "No repository to delete.");
        repo.close();
        try {
            GeoGIG.delete(repoUri.get());
            this.repo = null;
        } catch (Exception e) {
            Throwables.propagate(e);
        }

    }

    @Override
    public void invalidate(String repoName) {
        // Do nothing
    }

    @Override
    public Iterator<String> findRepositories() {
        if (repo == null) {
            return ImmutableSet.<String> of().iterator();
        }
        String repoName = repo.command(ResolveRepositoryName.class).call();
        return Iterators.singletonIterator(repoName);
    }
}
