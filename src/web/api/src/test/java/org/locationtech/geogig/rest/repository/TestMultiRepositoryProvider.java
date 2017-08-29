/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.junit.rules.ExternalResource;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.web.api.TestRepository;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * {@link RepositoryProvider} that looks up the coresponding {@link GeoGIG} instance to a given
 * {@link HttpServletRequest} by asking the geoserver's {@link RepositoryManager}
 */
public class TestMultiRepositoryProvider extends ExternalResource implements RepositoryProvider {
    
    private Map<String, TestRepository> repositories;
    
    @Override
    protected void before() throws Throwable {
        this.repositories = new HashMap<String, TestRepository>();
    }

    @Override
    protected void after() {
        for (TestRepository repo : repositories.values()) {
            repo.after();
        }
        repositories.clear();
    }

    @Override
    public Iterator<String> findRepositories() {
        return repositories.keySet().iterator();
    }

    @Deprecated
    @Override
    public Optional<Repository> getGeogig(org.restlet.data.Request request) {
        return null;
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
            return repositories.containsKey(repositoryName);
        }
        return false;
    }

    @Override
    public Repository createGeogig(final String repositoryName,
            final Map<String, String> parameters) {
        TestRepository repository = new TestRepository();
        try {
            repository.before();
        } catch (Throwable e) {
            Throwables.propagate(e);
        }
        repository.setRepoName(repositoryName);
        GeoGIG geogig = repository.getGeogig(false);
        repositories.put(repositoryName, repository);
        return geogig.getOrCreateRepository();
    }

    public TestRepository getTestRepository(String repositoryName) {
        return repositories.get(repositoryName);
    }

    public Repository getGeogigByName(final String repositoryName) {
        return repositories.get(repositoryName).getGeogig().getRepository();
    }

    @Deprecated
    @Override
    public void delete(org.restlet.data.Request request) {
    }

    @Override
    public void invalidate(String repoName) {
        throw new UnsupportedOperationException();
    }

    public void invalidateAll() {
        throw new UnsupportedOperationException();
    }
}