/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.net.URI;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Preconditions;

public abstract class RepositoryInitializer {

    /**
     * Finds a {@code RepositoryInitializer} that {@link #canHandle(URI) can handle} the given URI,
     * or throws an {@code IllegalArgumentException} if no such initializer can be found.
     * <p>
     * The lookup method uses the standard JAVA SPI (Service Provider Interface) mechanism, by which
     * all the {@code META-INF/services/org.locationtech.geogig.repository.RepositoryInitializer}
     * files in the classpath will be scanned for fully qualified names of implementing classes.
     */
    public static RepositoryInitializer lookup(URI repoURI) throws IllegalArgumentException {

        Preconditions.checkNotNull(repoURI, "Repository URI is null");

        Iterator<RepositoryInitializer> initializers = ServiceLoader.load(
                RepositoryInitializer.class).iterator();

        while (initializers.hasNext()) {
            RepositoryInitializer initializer = initializers.next();
            if (initializer.canHandle(repoURI)) {
                return initializer;
            }
        }
        throw new IllegalArgumentException(
                "No repository initializer found capable of handling this kind of URI: " + repoURI);
    }

    public abstract boolean canHandle(URI repoURI);

    public abstract boolean repoExists(URI repoURI) throws IllegalArgumentException;

    public abstract void initialize(URI repoURI, Context repoContext)
            throws IllegalArgumentException;

    public abstract ConfigDatabase getConfigDatabase(URI repoURI, Context repoContext);

    public static ConfigDatabase resolveConfigDatabase(URI repoURI, Context repoContext) {
        RepositoryInitializer initializer = RepositoryInitializer.lookup(repoURI);
        return initializer.getConfigDatabase(repoURI, repoContext);
    }

    /**
     * @param repositoryLocation the URI with the location of the repository to load
     * @return a {@link Repository} loaded from the given URI, already {@link Repository#open()
     *         open}
     * @throws IllegalArgumentException if no registered {@link RepositoryInitializer}
     *         implementation can load the repository at the given location
     * @throws RepositoryConnectionException if the repository can't be opened
     */
    public static Repository load(URI repositoryLocation) throws RepositoryConnectionException {
        RepositoryInitializer initializer = RepositoryInitializer.lookup(repositoryLocation);
        Repository repository = initializer.open(repositoryLocation);
        return repository;
    }

    public abstract Repository open(URI repositoryLocation) throws RepositoryConnectionException;

}
