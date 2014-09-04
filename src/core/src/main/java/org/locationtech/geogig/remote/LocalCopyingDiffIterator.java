/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote;

import java.util.Iterator;

import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.repository.Repository;

import com.google.common.collect.AbstractIterator;

/**
 * An iterator that copies all new objects from a source repository to a destination repository.
 */
class LocalCopyingDiffIterator extends AbstractIterator<DiffEntry> {

    private Iterator<DiffEntry> source;

    private Repository sourceRepo;

    private Repository destinationRepo;

    /**
     * Constructs a new {@code LocalCopyingDiffIterator}.
     * 
     * @param source the {@link DiffEntry} iterator
     * @param sourceRepo the source repository
     * @param destinationRepo the destination repository
     */
    public LocalCopyingDiffIterator(Iterator<DiffEntry> source, Repository sourceRepo,
            Repository destinationRepo) {
        this.source = source;
        this.sourceRepo = sourceRepo;
        this.destinationRepo = destinationRepo;
    }

    /**
     * @return the next {@link DiffEntry}
     */
    protected DiffEntry computeNext() {
        if (source.hasNext()) {
            DiffEntry next = source.next();
            if (next.getNewObject() != null) {
                NodeRef newObject = next.getNewObject();
                RevObject object = sourceRepo.command(RevObjectParse.class)
                        .setObjectId(newObject.getNode().getObjectId()).call().get();

                RevObject metadata = null;
                if (newObject.getMetadataId() != ObjectId.NULL) {
                    metadata = sourceRepo.command(RevObjectParse.class)
                            .setObjectId(newObject.getMetadataId()).call().get();
                }

                if (!destinationRepo.blobExists(object.getId())) {
                    destinationRepo.objectDatabase().put(object);
                }
                if (metadata != null && !destinationRepo.blobExists(metadata.getId())) {
                    destinationRepo.objectDatabase().put(metadata);
                }
            }
            return next;
        }
        return endOfData();
    }
}
