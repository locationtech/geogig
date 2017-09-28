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

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

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
    @Override
    public boolean objectExists(ObjectId objectId) {
        return objectId.isNull() || localRepository.objectDatabase().exists(objectId);
    }

    /**
     * Gets the parents of the specified commit from the repository.
     * 
     * @param commit the id of the commit whose parents to retrieve
     * @return a list of parent ids for the commit
     */
    @Override
    public ImmutableList<ObjectId> getParents(ObjectId commitId) {
        return localRepository.graphDatabase().getParents(commitId);
    }

    /**
     * Gets the depth of the given commit.
     * 
     * @param commitId the commit id
     * @return the depth, or 0 if the commit was not found
     */
    @Override
    public int getDepth(ObjectId commitId) {
        return localRepository.graphDatabase().getDepth(commitId);
    }

    /**
     * Gets the depth of the repository.
     * 
     * @return the depth of the repository, or {@link Optional#absent()} if the repository is not
     *         shallow
     */
    @Override
    public Optional<Integer> getRepoDepth() {
        return localRepository.getDepth();
    }

}
