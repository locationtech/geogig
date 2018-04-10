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

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.RepositoryFilter;
import org.locationtech.geogig.storage.AutoCloseableIterator;

/**
 * Overrides the basic implementation of {@link FilteredDiffIterator} by providing hints as to which
 * objects should be tracked, as well as copying affected features to the local repository.
 */
class LocalFilteredDiffIterator extends FilteredDiffIterator {

    private Repository destinationRepo;

    /**
     * Constructs a new {@code LocalFilteredDiffIterator}.
     * 
     * @param source the original iterator
     * @param sourceRepo the source full repository
     * @param destinationRepo the sparse repository
     * @param repoFilter the repository filter
     */
    public LocalFilteredDiffIterator(AutoCloseableIterator<DiffEntry> source, Repository sourceRepo,
            Repository destinationRepo, RepositoryFilter repoFilter) {
        super(source, sourceRepo, repoFilter);
        this.destinationRepo = destinationRepo;
    }

    /**
     * Hints that objects that I have in the sparse repository should continue to be tracked.
     * 
     * @param objectId the id of the object
     * @return true if the object should be tracked, false if it should only be tracked if it
     *         matches the filter
     */
    @Override
    protected boolean trackingObject(ObjectId objectId) {
        return destinationRepo.blobExists(objectId);
    }

    /**
     * Adds new objects that match my filter or were tracked to the sparse repository.
     * 
     * @param object the object to process
     */
    @Override
    protected void processObject(RevObject object) {
        if (object != null && !destinationRepo.blobExists(object.getId())) {
            destinationRepo.objectDatabase().put(object);
        }
    }

}
