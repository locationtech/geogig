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

import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.remotes.internal.FilteredDiffIterator;

/**
 * Iterates over all changes from a {@link BinaryPackedChanges} object.
 */
public class HttpFilteredDiffIterator extends FilteredDiffIterator {

    private Queue<DiffEntry> objects;

    /**
     * Constructs a new {@code HttpFilteredDiffIterator}.
     * 
     * @param in the input stream
     * @param changes the object that will be used to ingest the stream
     */
    public HttpFilteredDiffIterator(InputStream in, BinaryPackedChanges changes) {
        super(null, null, null);
        objects = new LinkedList<DiffEntry>();
        BinaryPackedChanges.Callback callback = new BinaryPackedChanges.Callback() {
            @Override
            public void callback(DiffEntry object) {
                objects.add(object);
            }
        };
        changes.ingest(in, callback);
        filtered = changes.wasFiltered();
    }

    /**
     * Iterate to the next change.
     * 
     * @return the next {@code DiffEntry}
     */
    @Override
    protected DiffEntry computeNext() {
        if (objects.peek() != null) {
            return objects.poll();
        }
        return null;
    }

    @Override
    protected boolean trackingObject(ObjectId objectId) {
        return false;
    }

    @Override
    protected void processObject(RevObject object) {
    }

}
