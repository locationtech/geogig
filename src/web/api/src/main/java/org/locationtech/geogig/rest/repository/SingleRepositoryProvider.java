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

import java.net.URI;
import java.util.Iterator;

import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.ResolveRepositoryName;
import org.locationtech.geogig.repository.GeoGIG;
import org.restlet.data.Request;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;

public class SingleRepositoryProvider implements RepositoryProvider {

    private GeoGIG geogig;

    public SingleRepositoryProvider(GeoGIG geogig) {
        this.geogig = geogig;
    }

    @Override
    public Optional<GeoGIG> getGeogig(Request request) {
        return Optional.fromNullable(geogig);
    }

    @Override
    public void delete(Request request) {
        Optional<GeoGIG> geogig = getGeogig(request);
        Preconditions.checkState(geogig.isPresent(), "No repository to delete.");
        GeoGIG ggig = geogig.get();
        Optional<URI> repoUri = ggig.command(ResolveGeogigURI.class).call();
        Preconditions.checkState(repoUri.isPresent(), "No repository to delete.");
        ggig.close();
        try {
            GeoGIG.delete(repoUri.get());
            this.geogig = null;
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
        if (geogig == null) {
            return ImmutableSet.<String> of().iterator();
        }
        String repoName = geogig.command(ResolveRepositoryName.class).call();
        return Iterators.singletonIterator(repoName);
    }
}
