/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote.http;

import java.net.URL;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.remotes.internal.RepositoryWrapper;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Provides an interface to make basic queries to a remote repository over http.
 */
class HttpRepositoryWrapper implements RepositoryWrapper {

    private URL repositoryURL;

    /**
     * Constructs a new {@code HttpRepositoryWrapper} with the provided URL.
     * 
     * @param repositoryURL the URL of the repository
     */
    public HttpRepositoryWrapper(final URL repositoryURL) {
        this.repositoryURL = repositoryURL;
    }

    /**
     * Determines if the provided object exists in the repository.
     * 
     * @param objectId the object to look for
     * @return true if the object existed, false otherwise
     */
    @Override
    public boolean objectExists(ObjectId objectId) {
        return objectId.isNull() || HttpUtils.networkObjectExists(repositoryURL, objectId);
    }

    /**
     * Gets the parents of the specified commit from the repository.
     * 
     * @param commit the id of the commit whose parents to retrieve
     * @return a list of parent ids for the commit
     */
    @Override
    public ImmutableList<ObjectId> getParents(ObjectId commitId) {
        return HttpUtils.getParents(repositoryURL, commitId);
    }

    /**
     * Gets the depth of the given commit.
     * 
     * @param commitId the commit id
     * @return the depth, or 0 if the commit was not found
     */
    @Override
    public int getDepth(ObjectId commitId) {
        Optional<Integer> depth = HttpUtils.getDepth(repositoryURL, commitId.toString());

        return depth.or(0);
    }

    /**
     * Gets the depth of the repository.
     * 
     * @return the depth of the repository, or {@link Optional#absent()} if the repository is not
     *         shallow
     */
    @Override
    public Optional<Integer> getRepoDepth() {
        return HttpUtils.getDepth(repositoryURL, null);
    }

}
