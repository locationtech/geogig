/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import java.util.List;

import org.locationtech.geogig.model.ObjectId;

/**
 * A deduplicator identifies duplicates in a stream of ObjectIds.
 */
public interface Deduplicator {
    /**
     * Tests an objectid for being a duplicate. This method does not alter the state of the
     * deduplicator (an unseen objectid is still unseen after isDuplicate has returned false.)
     */
    boolean isDuplicate(ObjectId id);

    /**
     * Marks an objectid as being visited. This method changes the state of the deduplicator; after
     * calling it on an object that object will be considered visited. The return value indicates
     * whether or not the objectid was already a duplicate before calling.
     * 
     * @return {@code true} if the object wasn't already visited and hence was marked as visited,
     *         {@code true} if the object was already visited
     */
    boolean visit(ObjectId id);

    /**
     * Convenience method for filtering out duplicate objectids from a list
     */
    void removeDuplicates(List<ObjectId> ids);

    /**
     * Clear out the memory of this deduplicator so that no objects are considered visited
     */
    void reset();

    /**
     * Release any external resources used to back this deduplicator, and invalidate the
     * deduplicator. After release() has been called, the deduplicator should no longer be used.
     */
    void release();

    boolean visit(ObjectId left, ObjectId right);

    boolean isDuplicate(ObjectId left, ObjectId right);
}
