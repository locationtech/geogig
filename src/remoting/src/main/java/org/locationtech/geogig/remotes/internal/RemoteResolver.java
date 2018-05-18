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

import java.util.Iterator;
import java.util.ServiceLoader;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.base.Optional;

/**
 * Provides utilities for creating interfaces to remote repositories.
 * <p>
 * Uses the {@link java.util.ServiceLoader} SPI mechanism to look up for {@link RemoteResolver}
 * implementations in the classpath.
 * <p>
 * The first {@link RemoteResolver} whose {@link #resolve} method returns a non absent
 * {@link IRemoteRepo} wins.
 */
public interface RemoteResolver {

    /**
     * Resolves the {@link IRemoteRepo} based on the {@code remoteConfig} and {@code hints} if the
     * implementation can handle the kind of remote (e.g. local repository, HTTP remote, etc).
     * 
     * @param remoteConfig the information for the remote to connect to
     * @param remoteHints hints for the remote connection, currently only the
     *        {@link Hints#REMOTES_READ_ONLY} key might be of use
     * @return the {@link IRemoteRepo} resolved based on the arguments, or {@link Optional#absent()
     *         absent} if this {@code RemoteResolver} can't handle the kind of remote denoted by the
     *         arguments.
     * @apiNote this method should not try to connect to the remote repository nor check for its
     *          existence, but merely return a handle to the {@link IRemoteRepo} whose
     *          {@link IRemoteRepo#open() open()} method would then fail with a
     *          {@link RepositoryConnectionException} if need be.
     */
    public Optional<IRemoteRepo> resolve(Remote remoteConfig, Hints remoteHints);

    /**
     * Constructs an interface to allow access to a remote repository.
     * 
     * @param remote the remote to connect to
     * @param remoteHints hints for the remote repo, like read-only, etc.
     * @return an {@link Optional} of the interface to the remote repository, or
     *         {@link Optional#absent()} if a connection to the remote could not be established.
     */
    public static Optional<IRemoteRepo> newRemote(Remote remote, @Nullable Hints remoteHints) {

        if (remoteHints == null) {
            remoteHints = new Hints();
        }

        Iterator<RemoteResolver> resolvers = ServiceLoader
                .load(RemoteResolver.class, RemoteResolver.class.getClassLoader()).iterator();

        while (resolvers.hasNext()) {
            RemoteResolver resolver = resolvers.next();
            Optional<IRemoteRepo> resolved = resolver.resolve(remote, remoteHints);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.absent();
    }
}
