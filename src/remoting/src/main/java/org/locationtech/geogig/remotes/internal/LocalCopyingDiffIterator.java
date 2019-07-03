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

import java.util.NoSuchElementException;

import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.AutoCloseableIterator;

/**
 * An iterator that copies all new objects from a source repository to a destination repository.
 */
class LocalCopyingDiffIterator implements AutoCloseableIterator<DiffEntry> {

    private AutoCloseableIterator<DiffEntry> source;

    private Geogig sourceRepo;

    private Geogig destinationRepo;

    private DiffEntry next;

    /**
     * Constructs a new {@code LocalCopyingDiffIterator}.
     * 
     * @param source the {@link DiffEntry} iterator
     * @param sourceRepo the source repository
     * @param destinationRepo the destination repository
     */
    public LocalCopyingDiffIterator(AutoCloseableIterator<DiffEntry> source, Repository sourceRepo,
            Repository destinationRepo) {
        this.source = source;
        this.sourceRepo = Geogig.of(sourceRepo.context());
        this.destinationRepo = Geogig.of(destinationRepo.context());
    }

    public @Override boolean hasNext() {
        if (next == null) {
            next = computeNext();
        }
        return next != null;
    }

    public @Override DiffEntry next() {
        if (next == null && !hasNext()) {
            throw new NoSuchElementException();
        }
        DiffEntry returnValue = next;
        next = null;
        return returnValue;
    }

    public @Override void close() {
        source.close();
    }

    /**
     * @return the next {@link DiffEntry}
     */
    private DiffEntry computeNext() {
        if (source.hasNext()) {
            DiffEntry next = source.next();
            if (next.getNewObject() != null) {
                NodeRef newObject = next.getNewObject();
                RevObject object = sourceRepo.objects().get(newObject.getNode().getObjectId());

                RevObject metadata = null;
                if (newObject.getMetadataId() != ObjectId.NULL) {
                    metadata = sourceRepo.objects().get(newObject.getMetadataId());
                }

                if (!destinationRepo.objects().exists(object.getId())) {
                    destinationRepo.objects().put(object);
                }
                if (metadata != null && !destinationRepo.objects().exists(metadata.getId())) {
                    destinationRepo.objects().put(metadata);
                }
            }
            return next;
        }
        return null;
    }
}
