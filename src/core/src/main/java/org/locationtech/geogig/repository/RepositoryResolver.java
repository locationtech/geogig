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
import java.util.List;
import java.util.ServiceLoader;

import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Preconditions;

public abstract class RepositoryResolver {

    /**
     * Finds a {@code RepositoryResolver} that {@link #canHandle(URI) can handle} the given URI, or
     * throws an {@code IllegalArgumentException} if no such initializer can be found.
     * <p>
     * The lookup method uses the standard JAVA SPI (Service Provider Interface) mechanism, by which
     * all the {@code META-INF/services/org.locationtech.geogig.repository.RepositoryResolver} files
     * in the classpath will be scanned for fully qualified names of implementing classes.
     * 
     * @throws IllegalArgumentException if no repository resolver is found capable of handling the
     *         given URI
     */
    public static RepositoryResolver lookup(URI repoURI) throws IllegalArgumentException {

        Preconditions.checkNotNull(repoURI, "Repository URI is null");

        Iterator<RepositoryResolver> initializers = ServiceLoader.load(RepositoryResolver.class)
                .iterator();

        while (initializers.hasNext()) {
            RepositoryResolver initializer = initializers.next();
            if (initializer.canHandle(repoURI)) {
                return initializer;
            }
        }
        throw new IllegalArgumentException(
                "No repository initializer found capable of handling this kind of URI: " + repoURI);
    }

    public abstract boolean canHandle(URI repoURI);

    public abstract boolean repoExists(URI repoURI) throws IllegalArgumentException;

    /**
     * Builds the URI of a repository with the given name and root URI.
     * 
     * @param rootRepoURI the root URI
     * @param repoName the repository name
     * @return the URI of the repository
     */
    public abstract URI buildRepoURI(URI rootRepoURI, String repoName);

    /**
     * List all repositories under the root URI. The list will contain the names of all the
     * repositories.
     * 
     * @param rootRepoURI the root URI
     * @return a list of repository names under the root URI.
     */
    public abstract List<String> listRepoNamesUnderRootURI(URI rootRepoURI);

    public abstract String getName(URI repoURI);

    public abstract void initialize(URI repoURI, Context repoContext)
            throws IllegalArgumentException;

    public abstract ConfigDatabase getConfigDatabase(URI repoURI, Context repoContext);

    public static ConfigDatabase resolveConfigDatabase(URI repoURI, Context repoContext) {
        RepositoryResolver initializer = RepositoryResolver.lookup(repoURI);
        return initializer.getConfigDatabase(repoURI, repoContext);
    }

    /**
     * @param repositoryLocation the URI with the location of the repository to load
     * @return a {@link Repository} loaded from the given URI, already {@link Repository#open()
     *         open}
     * @throws IllegalArgumentException if no registered {@link RepositoryResolver} implementation
     *         can load the repository at the given location
     * @throws RepositoryConnectionException if the repository can't be opened
     */
    public static Repository load(URI repositoryLocation) throws RepositoryConnectionException {
        RepositoryResolver initializer = RepositoryResolver.lookup(repositoryLocation);
        Repository repository = initializer.open(repositoryLocation);
        return repository;
    }

    public abstract Repository open(URI repositoryLocation) throws RepositoryConnectionException;

    /**
     * Deletes the repository addressed by the given URI.
     * <p>
     * 
     * @return {@code true} if the repository was deleted, {@code false} is the repository didn't
     *         exist.
     * @throws IllegalArgumentException if this implementation can't handle the given URI
     * @throws Exception if an error happens while deleting the repository, in which case it may
     *         have left in an inconsistent state.
     */
    public abstract boolean delete(URI repositoryLocation) throws Exception;

}
