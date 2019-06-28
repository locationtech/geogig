/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.repository;

import java.net.URI;
import java.util.Optional;

import lombok.NonNull;

/**
 * A repository is a collection of commits, each of which is an archive of what the project's
 * working tree looked like at a past date, whether on your machine or someone else's.
 * <p>
 * It also defines HEAD (see below), which identifies the branch or commit the current working tree
 * stemmed from. Lastly, it contains a set of branches and tags, to identify certain commits by
 * name.
 * </p>
 * 
 * @since 1.0
 */
public interface Repository extends CommandFactory {

    String DEPTH_CONFIG_KEY = "core.depth";

    /**
     * Provides an interface for listening for when a repository is opened and closed.
     */
    public static interface RepositoryListener {

        /**
         * Called when the repository was opened.
         * 
         * @param repo the repository that was opened
         */
        public void opened(Repository repo);

        /**
         * Called when the repository was closed.
         */
        public void closed();
    }

    /**
     * @return the context of this repository
     */
    public @NonNull Context context();

    /**
     * Adds a {@link RepositoryListener} to the repository.
     * 
     * @param listener the listener to add
     */
    void addListener(RepositoryListener listener);

    /**
     * @return {@code true} if the repository is open
     */
    boolean isOpen();

    /**
     * Opens the repository.
     * 
     * @throws RepositoryConnectionException
     */
    void open() throws RepositoryConnectionException;

    /**
     * Closes the repository.
     */
    void close();

    /**
     * @return the URI of the repository
     */
    URI getLocation();

    /**
     * Gets the depth of the repository, or {@link Optional#absent} if this is not a shallow clone.
     * 
     * @return the depth
     */
    Optional<Integer> getDepth();

    /**
     * @return true if this is a sparse (mapped) clone.
     */
    boolean isSparse();

}