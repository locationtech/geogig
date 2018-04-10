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

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.RepositoryFilter;
import org.locationtech.geogig.storage.AutoCloseableIterator;

/**
 * An implementation of a {@link DiffEntry} iterator that filters entries based on a provided
 * {@link RepositoryFilter}.
 */
public abstract class FilteredDiffIterator implements AutoCloseableIterator<DiffEntry> {

    protected boolean filtered = false;

    private AutoCloseableIterator<DiffEntry> source = null;

    private Repository sourceRepo;

    private RepositoryFilter repoFilter;

    private DiffEntry next = null;

    public final boolean wasFiltered() {
        return filtered;
    }

    /**
     * Constructs a new {@code FilteredDiffIterator}.
     * 
     * @param source the unfiltered iterator
     * @param sourceRepo the repository where objects are stored
     * @param repoFilter the filter to use
     */
    public FilteredDiffIterator(AutoCloseableIterator<DiffEntry> source, Repository sourceRepo,
            RepositoryFilter repoFilter) {
        this.source = source;
        this.sourceRepo = sourceRepo;
        this.repoFilter = repoFilter;
        filtered = false;
    }

    @Override
    public boolean hasNext() {
        if (next == null) {
            next = computeNext();
        }
        return next != null;
    }

    @Override
    public DiffEntry next() {
        if (next == null && !hasNext()) {
            throw new NoSuchElementException();
        }
        DiffEntry returnValue = next;
        next = null;
        return returnValue;
    }

    @Override
    public void close() {
        if (source != null) {
            source.close();
        }
    }

    /**
     * Compute the next {@link DiffEntry} that matches our {@link RepositoryFilter}.
     */
    protected DiffEntry computeNext() {
        while (source.hasNext()) {
            DiffEntry input = source.next();

            // HACK: ignore diff entries reporting a change to a tree, the feature changes will come
            // next and the new tree is built from them. I'm not totally sure this is the best way
            // to handle this situation but the unit tests are written expecting this behavior
            // probably as a side effect of a bug in TreeDiffEntryIterator (used to be called by
            // DiffTree) that doesn't report tree changes even is setReportTrees(true) was set.
            if (input.isChange() && input.getOldObject().getType().equals(TYPE.TREE)) {
                continue;
            }
            NodeRef oldObject = filter(input.getOldObject());
            NodeRef newObject;
            if (oldObject != null) {
                newObject = input.getNewObject();
                if (newObject != null) {
                    // we are tracking this object, but we still need to process the new object
                    RevObject object = sourceRepo.command(RevObjectParse.class)
                            .setObjectId(newObject.getNode().getObjectId()).call().get();

                    RevObject metadata = null;
                    if (newObject.getMetadataId() != ObjectId.NULL) {
                        metadata = sourceRepo.command(RevObjectParse.class)
                                .setObjectId(newObject.getMetadataId()).call().get();
                    }
                    processObject(object);
                    processObject(metadata);
                }
            } else {
                newObject = filter(input.getNewObject());
            }

            if (oldObject == null && newObject == null) {
                filtered = true;
                continue;
            }

            return new DiffEntry(oldObject, newObject);
        }
        return null;
    }

    private NodeRef filter(NodeRef node) {
        if (node == null) {
            return null;
        }

        RevObject object = sourceRepo.objectDatabase().get(node.getObjectId());

        RevObject metadata = null;
        if (!node.getMetadataId().isNull()) {
            metadata = sourceRepo.objectDatabase().get(node.getMetadataId());
        }
        if (node.getType() == TYPE.FEATURE) {
            if (trackingObject(object.getId())) {
                // We are already tracking this object, continue to do so
                return node;
            }

            RevFeatureType revFeatureType = (RevFeatureType) metadata;

            if (!repoFilter.filterObject(revFeatureType, node.getParentPath(), object)) {
                return null;
            }

        }
        processObject(object);
        processObject(metadata);
        return node;
    }

    /**
     * An overridable method for hinting that the given object should be tracked, regardless of
     * whether or not it matches the filter.
     * 
     * @param objectId the id of the object
     * @return true if the object should be tracked, false if it should only be tracked if it
     *         matches the filter
     */
    protected boolean trackingObject(ObjectId objectId) {
        return true;
    }

    /**
     * An overridable method to process all objects that match the filter.
     * 
     * @param object the object to process
     */
    protected void processObject(RevObject object) {

    }

}
