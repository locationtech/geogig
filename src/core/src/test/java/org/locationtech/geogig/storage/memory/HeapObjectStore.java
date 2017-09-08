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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterators.getNext;
import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterators.spliteratorUnknownSize;
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.impl.AbstractObjectStore;

import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Provides an implementation of a GeoGig object database that utilizes the heap for the storage of
 * objects.
 * 
 * @see AbstractObjectStore
 */
public class HeapObjectStore implements ObjectStore {

    private ConcurrentMap<ObjectId, RevObject> objects;

    public HeapObjectStore() {
        //
    }

    /**
     * Closes the database.
     * 
     * @see org.locationtech.geogig.storage.ObjectDatabase#close()
     */
    public @Override void close() {
        if (objects != null) {
            objects.clear();
            objects = null;
        }
    }

    /**
     * @return true if the database is open, false otherwise
     */
    public @Override boolean isOpen() {
        return objects != null;
    }

    /**
     * Opens the database for use by GeoGig.
     */
    public @Override void open() {
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
    public @Override boolean exists(ObjectId id) {
        checkNotNull(id, "id is null");
        checkState(isOpen(), "db is closed");
        return objects.containsKey(id);
    }

    private <T extends RevObject> T get(ObjectId id, Class<T> type, boolean failIfAbsent)
            throws IllegalArgumentException {
        checkNotNull(id, "argument id is null");
        checkNotNull(type, "argument class is null");
        checkState(isOpen(), "db is closed");
        RevObject o = objects.get(id);

        if (null != o && !type.isAssignableFrom(o.getClass())) {
            o = null;
        }
        final boolean fail = o == null && failIfAbsent;

        if (fail) {
            throw new IllegalArgumentException("object does not exist: " + id);
        }
        return o == null ? null : type.cast(o);
    }

    public @Override RevObject get(ObjectId id) throws IllegalArgumentException {
        return get(id, RevObject.class, true);
    }

    public @Override <T extends RevObject> T get(ObjectId id, Class<T> type)
            throws IllegalArgumentException {
        return get(id, type, true);
    }

    public @Override @Nullable RevObject getIfPresent(ObjectId id) {
        return get(id, RevObject.class, false);
    }

    public @Override @Nullable <T extends RevObject> T getIfPresent(ObjectId id, Class<T> type)
            throws IllegalArgumentException {
        return get(id, type, false);
    }

    /**
     * Deletes the object with the provided {@link ObjectId id} from the database.
     * 
     * @param objectId the id of the object to delete
     */
    public @Override void delete(ObjectId objectId) {
        checkNotNull(objectId, "objectId is null");
        checkState(isOpen(), "db is closed");
        objects.remove(objectId);
    }

    /**
     * Searches the database for {@link ObjectId}s that match the given partial id.
     * 
     * @param partialId the partial id to search for
     * @return a list of matching results
     */
    public @Override List<ObjectId> lookUp(final String partialId) {
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

    public @Override boolean put(final RevObject object) {
        checkNotNull(object, "argument object is null");
        checkArgument(!object.getId().isNull(), "ObjectId is NULL");
        checkState(isOpen(), "db is closed");

        ObjectId id = object.getId();
        RevObject existing = objects.putIfAbsent(id, object);
        return null == existing;
    }

    public @Override void putAll(Iterator<? extends RevObject> objects) {
        putAll(objects, NOOP_LISTENER);
    }

    public @Override void putAll(Iterator<? extends RevObject> objects,
            final BulkOpListener listener) {
        checkNotNull(objects, "objects is null");
        checkNotNull(listener, "listener is null");
        checkState(isOpen(), "db is closed");

        objects.forEachRemaining((o) -> {
            if (put(o)) {
                listener.inserted(o.getId(), null);
            } else {
                listener.found(o.getId(), null);
            }
        });
    }

    public @Override void deleteAll(Iterator<ObjectId> ids) {
        deleteAll(ids, NOOP_LISTENER);
    }

    public @Override void deleteAll(Iterator<ObjectId> ids, final BulkOpListener listener) {
        checkNotNull(ids, "ids is null");
        checkNotNull(listener, "listener is null");
        checkState(isOpen(), "db is closed");

        while (ids.hasNext()) {
            ObjectId id = ids.next();
            RevObject removed = this.objects.remove(id);
            if (removed == null) {
                listener.notFound(id);
            } else {
                listener.deleted(id);
            }
        }
    }

    public @Override Iterator<RevObject> getAll(Iterable<ObjectId> ids) {
        return getAll(ids, NOOP_LISTENER);
    }

    public @Override Iterator<RevObject> getAll(final Iterable<ObjectId> ids,
            final BulkOpListener listener) {
        return getAll(ids, listener, RevObject.class);
    }

    public @Override <T extends RevObject> Iterator<T> getAll(final Iterable<ObjectId> ids,
            final BulkOpListener listener, final Class<T> type) {
        checkNotNull(ids, "ids is null");
        checkNotNull(listener, "listener is null");
        checkNotNull(type, "type is null");
        checkState(isOpen(), "db is closed");

        final int characteristics = IMMUTABLE | NONNULL | DISTINCT;
        final boolean parallel = false;

        Stream<T> stream;
        stream = StreamSupport
                .stream(spliteratorUnknownSize(ids.iterator(), characteristics), parallel)//
                .map((id) -> {
                    T obj = getIfPresent(id, type);
                    if (null == obj) {
                        listener.notFound(id);
                    } else {
                        listener.found(id, null);
                    }
                    return obj;
                })//
                .filter((o) -> o != null);

        return stream.iterator();
    }

    public @Override String toString() {
        return getClass().getSimpleName();
    }

    public int size() {
        return this.objects.size();
    }

    public @Override <T extends RevObject> AutoCloseableIterator<ObjectInfo<T>> getObjects(
            Iterator<NodeRef> refs, BulkOpListener listener, Class<T> type) {

        checkNotNull(refs, "refs is null");
        checkNotNull(listener, "listener is null");
        checkNotNull(type, "type is null");
        checkState(isOpen(), "Database is closed");

        Iterator<ObjectInfo<T>> it = new AbstractIterator<ObjectInfo<T>>() {
            @Override
            protected ObjectInfo<T> computeNext() {
                checkState(isOpen(), "Database is closed");
                NodeRef ref;
                while ((ref = getNext(refs, null)) != null) {
                    ObjectId id = ref.getObjectId();
                    RevObject obj = getIfPresent(id);
                    if (obj == null || !type.isInstance(obj)) {
                        listener.notFound(id);
                    } else {
                        listener.found(id, null);
                        return ObjectInfo.of(ref, type.cast(obj));
                    }
                }
                return endOfData();
            }
        };

        return AutoCloseableIterator.fromIterator(it);
    }

    public @Override RevTree getTree(ObjectId id) {
        return get(id, RevTree.class, true);
    }

    public @Override RevFeature getFeature(ObjectId id) {
        return get(id, RevFeature.class, true);
    }

    public @Override RevFeatureType getFeatureType(ObjectId id) {
        return get(id, RevFeatureType.class, true);
    }

    public @Override RevCommit getCommit(ObjectId id) {
        return get(id, RevCommit.class, true);
    }

    public @Override RevTag getTag(ObjectId id) {
        return get(id, RevTag.class, true);
    }
}
