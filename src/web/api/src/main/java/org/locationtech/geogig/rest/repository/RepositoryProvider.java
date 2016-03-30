/* Copyright (c) 2014 Boundless and others.
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

import org.locationtech.geogig.api.GeoGIG;
import org.restlet.data.Request;

import com.google.common.base.Optional;

public interface RepositoryProvider {

    /**
     * Key used too lookup the {@link RepositoryProvider} instance in the
     * {@link Request#getAttributes() request attributes}
     */
    String KEY = "__REPOSITORY_PROVIDER_KEY__";

    public Optional<GeoGIG> getGeogig(Request request);

    /**
     * Deletes the repository that resolved from the request argument.
     * <p>
     * Implementation detail: the repository instance is removed from the provider's cache.
     */
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