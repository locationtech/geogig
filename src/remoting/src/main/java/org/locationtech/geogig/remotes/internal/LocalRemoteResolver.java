/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import java.net.URI;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryResolver;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * {@link RemoteResolver} for "local" repositories (that is, the ones that can be opened by a
 * {@link RepositoryResolver})
 */
public class LocalRemoteResolver implements RemoteResolver {

    public static @VisibleForTesting IRemoteRepo resolve(Remote remote, Repository remoteRepo) {
        return new LocalRemoteRepo(remote, remoteRepo);
    }

    public @Override Optional<IRemoteRepo> resolve(Remote remote, Hints remoteHints) {

        final String fetchURL = remote.getFetchURL();
        final URI fetchURI = URI.create(fetchURL);
        final String scheme = fetchURI.getScheme();
        Preconditions.checkNotNull(scheme, "Fetch URI doesn't declare scheme: %s", fetchURL);
        if (RepositoryResolver.resolverAvailableForURIScheme(scheme)) {
            IRemoteRepo remoteRepo = null;

            if (remote.getMapped()) {
                remoteRepo = new LocalMappedRemoteRepo(remote, fetchURI);
            } else {
                remoteRepo = new LocalRemoteRepo(remote, fetchURI);
            }
            return Optional.of(remoteRepo);
        }

        return Optional.absent();
    }
}
