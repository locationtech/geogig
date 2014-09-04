/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.Closeables;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;

/**
 * Provides a base implementation for different representations of the {@link ObjectDatabase}.
 * 
 * @see ObjectDatabase
 */
public abstract class AbstractObjectDatabase implements ObjectDatabase {

    protected ObjectSerializingFactory serializationFactory;

    public AbstractObjectDatabase(final ObjectSerializingFactory serializationFactory) {
        Preconditions.checkNotNull(serializationFactory);
        this.serializationFactory = serializationFactory;
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
        Preconditions.checkNotNull(partialId);

        byte[] raw = ObjectId.toRaw(partialId);

        List<ObjectId> baseResults = lookUpInternal(raw);

        // If the length of the partial string is odd, then the last character wasn't considered in
        // the lookup, we need to filter the list further.
        if (partialId.length() % 2 != 0) {
            Iterator<ObjectId> listIterator = baseResults.iterator();
            while (listIterator.hasNext()) {
                ObjectId result = listIterator.next();
                if (!result.toString().startsWith(partialId)) {
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
        Preconditions.checkNotNull(id, "id");

        final ObjectReader<RevObject> reader = serializationFactory.createObjectReader();
        return get(id, reader, true);
    }

    @Override
    public @Nullable RevObject getIfPresent(ObjectId id) {
        Preconditions.checkNotNull(id, "id");

        final ObjectReader<RevObject> reader = serializationFactory.createObjectReader();
        return get(id, reader, false);
    }

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @param id the id of the object to read
     * @param reader the reader of the object
     * @return the object, as read in from the {@link ObjectReader}
     * @see org.locationtech.geogig.storage.ObjectDatabase#get(org.locationtech.geogig.api.ObjectId,
     *      org.locationtech.geogig.storage.ObjectReader)
     */
    @Override
    public <T extends RevObject> T get(final ObjectId id, final Class<T> clazz) {
        Preconditions.checkNotNull(id, "id");
        Preconditions.checkNotNull(clazz, "class");

        final ObjectReader<T> reader = serializationFactory.createObjectReader(getType(clazz));

        return get(id, reader, true);
    }

    @Override
    public @Nullable <T extends RevObject> T getIfPresent(ObjectId id, Class<T> clazz)
            throws IllegalArgumentException {
        Preconditions.checkNotNull(id, "id");
        Preconditions.checkNotNull(clazz, "class");

        final ObjectReader<T> reader = serializationFactory.createObjectReader(getType(clazz));

        return get(id, reader, false);
    }

    private <T extends RevObject> T get(final ObjectId id, final ObjectReader<T> reader,
            boolean failIfNotFound) {
        InputStream raw = getRaw(id, failIfNotFound);
        if (null == raw) {
            return null;
        }
        T object;
        try {
            object = reader.read(id, raw);
        } finally {
            Closeables.closeQuietly(raw);
        }
        Preconditions.checkState(id.equals(object.getId()),
                "Expected id doesn't match parsed id %s, %s. Object: %s", id, object.getId(),
                object);
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

    private RevObject.TYPE getType(Class<? extends RevObject> clazz) {
        return TYPE.valueOf(clazz);
    }

    @Nullable
    private InputStream getRaw(final ObjectId id, boolean failIfNotFound)
            throws IllegalArgumentException {
        InputStream in = getRawInternal(id, failIfNotFound);
        if (null == in) {
            return null;
        }
        try {
            return new LZFInputStream(in);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    protected abstract InputStream getRawInternal(ObjectId id, boolean failIfNotFound)
            throws IllegalArgumentException;

    @Override
    public boolean put(final RevObject object) {
        Preconditions.checkNotNull(object);
        Preconditions.checkArgument(!object.getId().isNull(), "ObjectId is NULL %s", object);

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

        ObjectWriter<RevObject> writer = serializationFactory.createObjectWriter(object.getType());
        LZFOutputStream cOut = new LZFOutputStream(target);
        try {
            writer.write(object, cOut);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            try {
                cOut.flush();
                cOut.close();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        // int size = ((ByteArrayOutputStream) target).size();
        // System.err.printf("%d,%s,%s\n", size, object.getId(), object.getType());
    }

    /**
     * Stores the raw data for the given id <em>only if it does not exist</em> already, and returns
     * whether the object was actually inserted.
     */
    protected abstract boolean putInternal(ObjectId id, byte[] rawData);

    /**
     * @return a newly constructed {@link ObjectInserter} for this database
     * @see org.locationtech.geogig.storage.ObjectDatabase#newObjectInserter()
     */
    @Override
    public ObjectInserter newObjectInserter() {
        return new ObjectInserter(this);
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids) {
        return getAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids) {
        return deleteAll(ids, BulkOpListener.NOOP_LISTENER);
    }
}
