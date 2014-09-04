/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.storage.AbstractObjectDatabase;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ning.compress.lzf.LZFInputStream;

/**
 * Provides an implementation of a GeoGig object database that utilizes the heap for the storage of
 * objects.
 * 
 * @see AbstractObjectDatabase
 */
public class HeapObjectDatabse extends AbstractObjectDatabase implements ObjectDatabase {

    private ConcurrentMap<ObjectId, byte[]> objects;

    public HeapObjectDatabse() {
        super(DataStreamSerializationFactoryV1.INSTANCE);
    }

    /**
     * Closes the database.
     * 
     * @see org.locationtech.geogig.storage.ObjectDatabase#close()
     */
    @Override
    public void close() {
        if (objects != null) {
            objects.clear();
            objects = null;
        }
    }

    /**
     * @return true if the database is open, false otherwise
     */
    @Override
    public boolean isOpen() {
        return objects != null;
    }

    /**
     * Opens the database for use by GeoGig.
     */
    @Override
    public void open() {
        if (isOpen()) {
            return;
        }
        objects = Maps.newConcurrentMap();
    }

    /**
     * Determines if the given {@link ObjectId} exists in the object database.
     * 
     * @param id the id to search for
     * @return true if the object exists, false otherwise
     */
    @Override
    public boolean exists(ObjectId id) {
        checkNotNull(id);
        return objects.containsKey(id);
    }

    /**
     * Deletes the object with the provided {@link ObjectId id} from the database.
     * 
     * @param objectId the id of the object to delete
     * @return true if the object was deleted, false if it was not found
     */
    @Override
    public boolean delete(ObjectId objectId) {
        checkNotNull(objectId);
        return objects.remove(objectId) != null;
    }

    @Override
    protected List<ObjectId> lookUpInternal(byte[] raw) {
        throw new UnsupportedOperationException("we override lookup directly");
    }

    /**
     * Searches the database for {@link ObjectId}s that match the given partial id.
     * 
     * @param partialId the partial id to search for
     * @return a list of matching results
     */
    @Override
    public List<ObjectId> lookUp(final String partialId) {
        Preconditions.checkNotNull(partialId);
        List<ObjectId> matches = Lists.newLinkedList();
        for (ObjectId id : objects.keySet()) {
            if (id.toString().startsWith(partialId)) {
                matches.add(id);
            }
        }
        return matches;
    }

    @Override
    protected InputStream getRawInternal(ObjectId id, boolean failIfNotFound)
            throws IllegalArgumentException {
        byte[] data = objects.get(id);
        if (data == null) {
            if (failIfNotFound) {
                throw new IllegalArgumentException(id + " does not exist");
            }
            return null;
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    protected boolean putInternal(ObjectId id, byte[] rawData) {
        byte[] previousValue = objects.putIfAbsent(id, rawData);
        return previousValue == null;
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids, final BulkOpListener listener) {
        long count = 0;
        while (ids.hasNext()) {
            ObjectId id = ids.next();
            byte[] removed = this.objects.remove(id);
            if (removed != null) {
                count++;
                listener.deleted(id);
            } else {
                listener.notFound(id);
            }
        }
        return count;
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, final BulkOpListener listener) {

        return new AbstractIterator<RevObject>() {
            final Iterator<ObjectId> iterator = ids.iterator();

            @Override
            protected RevObject computeNext() {
                RevObject found = null;
                ObjectId id;
                byte[] raw;
                while (iterator.hasNext() && found == null) {
                    id = iterator.next();
                    raw = objects.get(id);
                    if (raw != null) {
                        try {
                            found = serializationFactory.createObjectReader().read(id,
                                    new LZFInputStream(new ByteArrayInputStream(raw)));
                        } catch (IOException e) {
                            throw Throwables.propagate(e);
                        }
                        listener.found(found.getId(), raw.length);
                    } else {
                        listener.notFound(id);
                    }
                }
                return found == null ? endOfData() : found;
            }
        };
    }

    @Override
    public void configure() {
        // No-op
    }

    @Override
    public void checkConfig() {
        // No-op
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
