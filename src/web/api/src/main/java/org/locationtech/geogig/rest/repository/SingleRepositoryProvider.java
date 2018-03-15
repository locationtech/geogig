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
    public Optional<Repository> getGeogig(final String repositoryName) {
        return Optional.fromNullable(repo);
    }

    @Override
    public boolean hasGeoGig(String repositoryName) {
        if (repo != null) {
            String repoName = repo.command(ResolveRepositoryName.class).call();
            return repoName.equals(repositoryName);
        }
        return false;
    }

    @Override
    public Repository createGeogig(final String repositoryName,
            final Map<String, String> parameters) {
        throw new UnsupportedOperationException(
                "Cannot create a repository with the single repo provider.");
    }

    @Override
    public void delete(String repoName) {
        Preconditions.checkState(repo != null, "No repository to delete.");
        Optional<URI> repoUri = repo.command(ResolveGeogigURI.class).call();
        Preconditions.checkState(repoUri.isPresent(), "No repository to delete.");
        repo.close();
        try {
            GeoGIG.delete(repoUri.get());
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }finally {
            this.repo = null;
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
