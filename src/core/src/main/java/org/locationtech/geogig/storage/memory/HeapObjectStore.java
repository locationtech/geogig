/* Copyright (c) 2012-2016 Boundless and others.
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
import static com.google.common.base.Preconditions.checkState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV2;
import org.locationtech.geogig.storage.datastream.LZFSerializationFactory;
import org.locationtech.geogig.storage.impl.AbstractObjectStore;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Provides an implementation of a GeoGig object database that utilizes the heap for the storage of
 * objects.
 * 
 * @see AbstractObjectStore
 */
public class HeapObjectStore extends AbstractObjectStore {

    private ConcurrentMap<ObjectId, byte[]> objects;

    public HeapObjectStore() {
        super(new LZFSerializationFactory(DataStreamSerializationFactoryV2.INSTANCE));
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
        checkNotNull(id, "id is null");
        checkState(isOpen(), "db is closed");
        return objects.containsKey(id);
    }

    /**
     * Deletes the object with the provided {@link ObjectId id} from the database.
     * 
     * @param objectId the id of the object to delete
     */
    @Override
    public void delete(ObjectId objectId) {
        checkNotNull(objectId, "objectId is null");
        checkState(isOpen(), "db is closed");
        objects.remove(objectId);
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
        checkNotNull(partialId, "partialId is null");
        Preconditions.checkArgument(partialId.length() > 7,
                "partial id must be at least 8 characters long: ", partialId);
        checkState(isOpen(), "db is closed");
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
    public void deleteAll(Iterator<ObjectId> ids, final BulkOpListener listener) {
        checkNotNull(ids, "ids is null");
        checkNotNull(listener, "listener is null");
        checkState(isOpen(), "db is closed");

        while (ids.hasNext()) {
            ObjectId id = ids.next();
            byte[] removed = this.objects.remove(id);
            if (removed != null) {
                listener.deleted(id);
            } else {
                listener.notFound(id);
            }
        }
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, final BulkOpListener listener) {
        return getAll(ids, listener, RevObject.class);
    }

    @Override
    public <T extends RevObject> Iterator<T> getAll(Iterable<ObjectId> ids, BulkOpListener listener,
            Class<T> type) {
        checkNotNull(ids, "ids is null");
        checkNotNull(listener, "listener is null");
        checkNotNull(type, "type is null");
        checkState(isOpen(), "db is closed");

        return new AbstractIterator<T>() {

            final Iterator<ObjectId> iterator = Lists.newArrayList(ids).iterator();

            @Override
            protected T computeNext() {
                T found = null;
                ObjectId id;
                byte[] raw;
                while (iterator.hasNext() && found == null) {
                    id = iterator.next();
                    raw = objects.get(id);
                    if (raw != null) {
                        try {
                            RevObject obj = serializer().read(id, new ByteArrayInputStream(raw));
                            found = type.isAssignableFrom(obj.getClass()) ? type.cast(obj) : null;
                        } catch (IOException e) {
                            throw Throwables.propagate(e);
                        }
                        if (found == null) {
                            listener.notFound(id);
                        } else {
                            listener.found(found.getId(), raw.length);
                        }
                    } else {
                        listener.notFound(id);
                    }
                }

                return found == null ? endOfData() : found;
            }
        };
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public int size() {
        return this.objects.size();
    }

    public long storageSize() {
        final AtomicLong size = new AtomicLong();
        this.objects.values().forEach((ba) -> size.addAndGet(ba.length));
        return size.get();
    }

}
