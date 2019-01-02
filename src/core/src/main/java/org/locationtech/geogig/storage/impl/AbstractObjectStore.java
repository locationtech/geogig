/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.RevObjectSerializerProxy;

import com.google.common.io.Closeables;

/**
 * Provides a base implementation for different representations of the {@link ObjectStore}.
 * 
 * @see ObjectStore
 */
public abstract class AbstractObjectStore implements ObjectStore {

    private RevObjectSerializer serializer;

    public AbstractObjectStore() {
        this(new RevObjectSerializerProxy());
    }

    public AbstractObjectStore(final RevObjectSerializer serializer) {
        checkNotNull(serializer);
        this.serializer = serializer;
    }

    protected void setSerializationFactory(RevObjectSerializer serializer) {
        checkNotNull(serializer);
        this.serializer = serializer;
    }

    public RevObjectSerializer serializer() {
        return serializer;
    }

    /**
     * Searches the database for {@link ObjectId}s that match the given partial id.
     * 
     * @param partialId the partial id to search for
     * @return a list of matching results
     * @see org.locationtech.geogig.storage.ObjectDatabase#lookUp(java.lang.String)
     */
    @Override
    public List<ObjectId> lookUp(final String partialId) {
        checkNotNull(partialId, "argument partialId is null");
        checkArgument(partialId.length() > 7, "partial id must be at least 8 characters long: ",
                partialId);
        checkState(isOpen(), "db is closed");

        byte[] raw = ObjectId.toRaw(partialId);

        List<ObjectId> baseResults = lookUpInternal(raw);

        // If the length of the partial string is odd, then the last character wasn't considered in
        // the lookup, we need to filter the list further.
        if (partialId.length() % 2 != 0) {
            Iterator<ObjectId> listIterator = baseResults.iterator();
            while (listIterator.hasNext()) {
                ObjectId lookupMatchCandidate = listIterator.next();
                if (!lookupMatchCandidate.toString().startsWith(partialId)) {
                    listIterator.remove();
                }
            }
        }

        return baseResults;
    }

    /**
     * Searches the database for {@link ObjectId}s that match the given raw byte code.
     * 
     * @param raw raw byte code to search for
     * @return a list of matching results
     */
    protected abstract List<ObjectId> lookUpInternal(byte[] raw);

    @Override
    public RevObject get(ObjectId id) {
        checkNotNull(id, "argument id is null");
        checkState(isOpen(), "db is closed");

        return get(id, true);
    }

    @Override
    public @Nullable RevObject getIfPresent(ObjectId id) {
        checkNotNull(id, "argument id is null");
        checkState(isOpen(), "db is closed");

        return get(id, false);
    }

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @param id the id of the object to read
     * @param reader the reader of the object
     * @return the object, as read in from the {@link ObjectReader}
     * @see org.locationtech.geogig.storage.ObjectDatabase#get(org.locationtech.geogig.model.ObjectId,
     *      org.locationtech.geogig.storage.impl.ObjectReader)
     */
    @Override
    public <T extends RevObject> T get(final ObjectId id, final Class<T> clazz) {
        checkNotNull(id, "argument id is null");
        checkNotNull(clazz, "argument class is null");
        checkState(isOpen(), "db is closed");

        RevObject obj = null;
        try {
            obj = get(id, true);
            return clazz.cast(obj);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    String.format("object %s does not exist as a %s (%s)", id,
                            clazz.getSimpleName(), obj.getType()));
        }
    }

    @Override
    public @Nullable <T extends RevObject> T getIfPresent(ObjectId id, Class<T> clazz)
            throws IllegalArgumentException {
        checkNotNull(id, "argument id is null");
        checkNotNull(clazz, "argument class is null");
        checkState(isOpen(), "db is closed");
        try {
            return clazz.cast(get(id, false));
        } catch (ClassCastException e) {
            return null;
        }

    }

    protected RevObject get(final ObjectId id, boolean failIfNotFound) {
        InputStream raw = getRaw(id, failIfNotFound);
        if (null == raw) {
            return null;
        }
        RevObject object;
        try {
            object = serializer().read(id, raw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Closeables.closeQuietly(raw);
        }
        return object;
    }

    @Override
    public RevTree getTree(ObjectId id) {
        return get(id, RevTree.class);
    }

    @Override
    public RevFeature getFeature(ObjectId id) {
        return get(id, RevFeature.class);
    }

    @Override
    public RevFeatureType getFeatureType(ObjectId id) {
        return get(id, RevFeatureType.class);
    }

    @Override
    public RevCommit getCommit(ObjectId id) {
        return get(id, RevCommit.class);
    }

    @Override
    public RevTag getTag(ObjectId id) {
        return get(id, RevTag.class);
    }

    @Nullable
    private InputStream getRaw(final ObjectId id, boolean failIfNotFound) {
        return getRawInternal(id, failIfNotFound);
    }

    /**
     * @throws IllegalArgumentException
     */
    protected abstract InputStream getRawInternal(ObjectId id, boolean failIfNotFound);

    @Override
    public boolean put(final RevObject object) {
        checkNotNull(object, "argument object is null");
        checkArgument(!object.getId().isNull(), "ObjectId is NULL %s", object);
        checkState(isOpen(), "db is closed");

        ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
        writeObject(object, rawOut);
        final ObjectId id = object.getId();
        final byte[] rawData = rawOut.toByteArray();
        final boolean inserted = putInternal(id, rawData);
        return inserted;
    }

    /**
     * This default implementation calls {@link #putInternal(ObjectId, byte[])} for each object;
     * subclasses may override if appropriate.
     */
    @Override
    public void putAll(Iterator<? extends RevObject> objects, final BulkOpListener listener) {
        checkNotNull(objects, "objects is null");
        checkNotNull(listener, "listener is null");
        checkState(isOpen(), "db is closed");

        ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
        while (objects.hasNext()) {
            RevObject object = objects.next();
            rawOut.reset();

            writeObject(object, rawOut);
            final byte[] rawData = rawOut.toByteArray();

            final ObjectId id = object.getId();
            final boolean added = putInternal(id, rawData);
            if (added) {
                listener.inserted(object.getId(), rawData.length);
            } else {
                listener.found(object.getId(), null);
            }
        }
    }

    protected void writeObject(RevObject object, OutputStream target) {
        try {
            serializer().write(object, target);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stores the raw data for the given id <em>only if it does not exist</em> already, and returns
     * whether the object was actually inserted.
     */
    protected abstract boolean putInternal(ObjectId id, byte[] rawData);

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids) {
        checkState(isOpen(), "db is closed");
        return getAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids) {
        checkState(isOpen(), "db is closed");
        deleteAll(ids, BulkOpListener.NOOP_LISTENER);
    }
}
