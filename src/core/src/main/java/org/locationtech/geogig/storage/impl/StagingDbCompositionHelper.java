/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;

public class StagingDbCompositionHelper {

    public static Iterator<RevObject> getAll(final ObjectStore objectDb,
            final ObjectStore stagingDb, final Iterable<ObjectId> ids,
            final BulkOpListener listener) {

        final List<ObjectId> missingInStaging = Lists.newLinkedList();

        final int limit = 10000;

        final BulkOpListener stagingListener = new BulkOpListener.ForwardingListener(listener) {
            @Override
            public void notFound(ObjectId id) {
                missingInStaging.add(id);
            }
        };

        final Iterator<RevObject> foundInStaging = stagingDb.getAll(ids, stagingListener);

        Iterator<RevObject> compositeIterator = new AbstractIterator<RevObject>() {

            Iterator<RevObject> forwardedToObjectDb = Collections.emptyIterator();

            @Override
            protected RevObject computeNext() {
                if (forwardedToObjectDb.hasNext()) {
                    return forwardedToObjectDb.next();
                }
                if (missingInStaging.size() >= limit) {
                    List<ObjectId> missing = new ArrayList<ObjectId>(missingInStaging);
                    missingInStaging.clear();

                    forwardedToObjectDb = objectDb.getAll(missing, listener);
                    return computeNext();
                }
                if (foundInStaging.hasNext()) {
                    return foundInStaging.next();
                } else if (forwardedToObjectDb.hasNext()) {
                    return forwardedToObjectDb.next();
                } else if (!missingInStaging.isEmpty()) {
                    List<ObjectId> missing = new ArrayList<ObjectId>(missingInStaging);
                    missingInStaging.clear();
                    forwardedToObjectDb = objectDb.getAll(missing, listener);
                    return computeNext();
                }
                return endOfData();
            }
        };

        return compositeIterator;
    }
}
