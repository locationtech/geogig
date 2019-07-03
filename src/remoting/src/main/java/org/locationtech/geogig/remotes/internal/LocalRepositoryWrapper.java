/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Repository;

/**
 * Provides an interface to make basic queries to a local repository.
 */
class LocalRepositoryWrapper implements RepositoryWrapper {

    private Repository localRepository;

    /**
     * Constructs a new {@code LocalRepositoryWrapper} using the provided repository.
     * 
     * @param repository the local repository
     */
    public LocalRepositoryWrapper(Repository repository) {
        this.localRepository = repository;
    }

    /**
     * Determines if the provided object exists in the repository.
     * 
     * @param objectId the object to look for
     * @return true if the object existed, false otherwise
     */
    public @Override boolean objectExists(ObjectId objectId) {
        return objectId.isNull() || localRepository.context().objectDatabase().exists(objectId);
    }

    /**
     * Gets the parents of the specified commit from the repository.
     * 
     * @param commit the id of the commit whose parents to retrieve
     * @return a list of parent ids for the commit
     */
    public @Override List<ObjectId> getParents(ObjectId commitId) {
        return localRepository.context().graphDatabase().getParents(commitId);
    }

    /**
     * Gets the depth of the given commit.
     * 
     * @param commitId the commit id
     * @return the depth, or 0 if the commit was not found
     */
    public @Override int getDepth(ObjectId commitId) {
        return localRepository.context().graphDatabase().getDepth(commitId);
    }

    /**
     * Gets the depth of the repository.
     * 
     * @return the depth of the repository, or {@link Optional#empty()} if the repository is not
     *         shallow
     */
    public @Override Optional<Integer> getRepoDepth() {
        return localRepository.getDepth();
    }

}
