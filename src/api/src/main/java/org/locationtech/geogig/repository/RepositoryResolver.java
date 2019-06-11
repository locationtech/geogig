/* Copyright (c) 2015-2016 Boundless and others.
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
import java.util.List;

import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.RefDatabase;

import lombok.NonNull;

public interface RepositoryResolver {

    boolean canHandle(URI repoURI);

    boolean canHandleURIScheme(String scheme);

    boolean repoExists(URI repoURI) throws IllegalArgumentException;

    /**
     * Builds the URI of a repository with the given name and root URI.
     * 
     * @param rootRepoURI the root URI
     * @param repoName the repository name
     * @return the URI of the repository
     */
    URI buildRepoURI(URI rootRepoURI, String repoName);

    /**
     * List all repositories under the root URI. The list will contain the names of all the
     * repositories.
     * 
     * @param rootRepoURI the root URI
     * @return a list of repository names under the root URI.
     */
    List<String> listRepoNamesUnderRootURI(URI rootRepoURI);

    String getName(URI repoURI);

    void initialize(URI repoURI, Context repoContext) throws IllegalArgumentException;

    Repository open(URI repositoryLocation) throws RepositoryConnectionException;

    /**
     * Deletes the repository addressed by the given URI.
     * <p>
     * 
     * @param repositoryLocation Repository URI location
     * @return {@code true} if the repository was deleted, {@code false} is the repository didn't
     *         exist.
     * @throws IllegalArgumentException if this implementation can't handle the given URI
     * @throws Exception if an error happens while deleting the repository, in which case it may
     *         have left in an inconsistent state.
     */
    boolean delete(URI repositoryLocation) throws Exception;

    /**
     * Gets a config database for the given URI.
     * 
     * @param repoURI the repository URI
     * @param repoContext the repository context
     * @param rootUri {@code true} if {@code repoURI} represents a root URI to a group of
     *        repositories
     * @return the config database
     */
    ConfigDatabase resolveConfigDatabase(URI repoURI, Context repoContext, boolean rootUri);

    /**
     * Gets a config database for a single repository.
     * 
     * @param repoURI the repository URI
     * @param repoContext the repository context
     * @return the config database
     */
    public default ConfigDatabase resolveConfigDatabase(URI repoURI, Context repoContext) {
        return resolveConfigDatabase(repoURI, repoContext, false);
    }

    ObjectDatabase resolveObjectDatabase(@NonNull URI repoURI, Hints hints);

    IndexDatabase resolveIndexDatabase(@NonNull URI repoURI, Hints hints);

    RefDatabase resolveRefDatabase(@NonNull URI repoURI, Hints hints);

    ConflictsDatabase resolveConflictsDatabase(@NonNull URI repoURI, Hints hints);

    public default boolean isReadOnly(Hints hints) {
        return hints != null && hints.getBoolean(Hints.OBJECTS_READ_ONLY);
    }

}
