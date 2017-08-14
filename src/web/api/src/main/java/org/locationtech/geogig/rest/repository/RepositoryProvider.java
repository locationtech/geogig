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

import java.util.Iterator;
import java.util.Map;

import org.locationtech.geogig.repository.Repository;
import org.restlet.data.Request;

import com.google.common.base.Optional;

public interface RepositoryProvider {

    static final String BASE_REPOSITORY_ROUTE = "repos";

    /**
     * Key used too lookup the {@link RepositoryProvider} instance in the
     * {@link Request#getAttributes() request attributes}
     */
    String KEY = "__REPOSITORY_PROVIDER_KEY__";

    @Deprecated
    public Optional<Repository> getGeogig(Request request);

    public Optional<Repository> getGeogig(final String repositoryName);

    public Repository createGeogig(final String repositoryName,
            final Map<String, String> parameters);

    /**
     * Deletes the repository that resolved from the request argument.
     * <p>
     * Implementation detail: the repository instance is removed from the provider's cache.
     */
    @Deprecated
    void delete(Request request);

    /**
     * Signals to the repository provider that a repository may no longer be valid.
     */
    void invalidate(String repoName);

    /**
     * @return an Iterator that walks through the names of all the repositories provided.
     */
    public Iterator<String> findRepositories();
}