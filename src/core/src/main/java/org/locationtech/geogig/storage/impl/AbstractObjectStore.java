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

import static java.util.Objects.requireNonNull;
import static org.locationtech.geogig.base.Preconditions.checkArgument;

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
import org.locationtech.geogig.storage.AbstractStore;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.RevObjectSerializer;

import com.google.common.io.Closeables;

import lombok.NonNull;

/**
 * Provides a base implementation for different representations of the {@link ObjectStore}.
 * 
 * @see ObjectStore
 */
public abstract class AbstractObjectStore extends AbstractStore implements ObjectStore {

    private RevObjectSerializer serializer;

    public AbstractObjectStore(final @NonNull RevObjectSerializer serializer, boolean readOnly) {
        super(readOnly);
        this.serializer = serializer;
    }

    protected void setSerializationFactory(@NonNull RevObjectSerializer serializer) {
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
    public @Override List<ObjectId> lookUp(final String partialId) {
        requireNonNull(partialId, "argument partialId is null");
        checkArgument(partialId.length() > 7, "partial id must be at least 8 characters long: ",
                partialId);
        checkOpen();

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

    public @Override RevObject get(ObjectId id) {
        requireNonNull(id, "argument id is null");
        checkOpen();

        return get(id, true);
    }

    public @Override @Nullable RevObject getIfPresent(ObjectId id) {
        requireNonNull(id, "argument id is null");
        checkOpen();

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
    public @Override <T extends RevObject> T get(final ObjectId id, final Class<T> clazz) {
        requireNonNull(id, "argument id is null");
        requireNonNull(clazz, "argument class is null");
        checkOpen();

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

    public @Override @Nullable <T extends RevObject> T getIfPresent(ObjectId id, Class<T> clazz)
            throws IllegalArgumentException {
        requireNonNull(id, "argument id is null");
        requireNonNull(clazz, "argument class is null");
        checkOpen();
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

    public @Override RevTree getTree(ObjectId id) {
        return get(id, RevTree.class);
    }

    public @Override RevFeature getFeature(ObjectId id) {
        return get(id, RevFeature.class);
    }

    public @Override RevFeatureType getFeatureType(ObjectId id) {
        return get(id, RevFeatureType.class);
    }

    public @Override RevCommit getCommit(ObjectId id) {
        return get(id, RevCommit.class);
    }

    public @Override RevTag getTag(ObjectId id) {
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

    public @Override boolean put(final RevObject object) {
        requireNonNull(object, "argument object is null");
        checkArgument(!object.getId().isNull(), "ObjectId is NULL %s", object);
        checkOpen();

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
    public @Override void putAll(Iterator<? extends RevObject> objects,
            final BulkOpListener listener) {
        requireNonNull(objects, "objects is null");
        requireNonNull(listener, "listener is null");
        checkOpen();

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

    public @Override Iterator<RevObject> getAll(final Iterable<ObjectId> ids) {
        checkOpen();
        return getAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    public @Override void putAll(Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    public @Override void deleteAll(Iterator<ObjectId> ids) {
        checkOpen();
        deleteAll(ids, BulkOpListener.NOOP_LISTENER);
    }
}
