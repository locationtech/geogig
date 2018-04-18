/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.internal.ObjectStoreDiffObjectIterator;

import com.google.common.annotations.Beta;

/**
 * Base interface for storage and retrieval of revision objects.
 *
 * @since 1.0
 */
public interface ObjectStore extends Closeable {

    /**
     * Opens the database. It's safe to call this method multiple times, and only the first call
     * shall take effect.
     */
    public void open();

    /**
     * Closes the database. This method is idempotent.
     */
    public void close();

    /**
     * @return {@code true} if the database is open, false otherwise
     */
    public boolean isOpen();

    /**
     * Determines if the given {@link ObjectId} exists in the object database.
     * 
     * @param id the id to search for
     * @return true if the object exists, false otherwise
     */
    public boolean exists(ObjectId id);

    /**
     * Searches the database for {@link ObjectId}s that match the given partial id.
     * 
     * @param partialId the partial id to search for, must be at least 8 characters long
     * @return a list of matching results
     */
    public List<ObjectId> lookUp(String partialId);

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @param id the id of the object to get
     * @return the revision object
     * @throws IllegalArgumentException if no blob exists for the given {@code id}
     */
    public RevObject get(ObjectId id) throws IllegalArgumentException;

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @param id the id of the object to get
     * @param type the type of the object to get
     * @return a revision object of the provided type
     * @throws IllegalArgumentException if no blob exists for the given {@code id}, or the object
     *         found is not of the required {@code type}
     */
    public <T extends RevObject> T get(ObjectId id, Class<T> type) throws IllegalArgumentException;

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @param id the id of the object to get
     * @return the object found, or {@code null} if no object is found
     */
    public @Nullable RevObject getIfPresent(ObjectId id);

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @param id the id of the object to get
     * @param type the type of the object to get
     * @return the object found, or {@code null} if no object is found for the given id and type
     *         (note the object may exist and be of another type)
     */
    public @Nullable <T extends RevObject> T getIfPresent(ObjectId id, Class<T> type)
            throws IllegalArgumentException;

    /**
     * Shortcut for {@link #get(ObjectId, Class) get(id, RevTree.class)}
     */
    public RevTree getTree(ObjectId id);

    /**
     * Shortcut for {@link #get(ObjectId, Class) get(id, RevFeature.class)}
     */
    public RevFeature getFeature(ObjectId id);

    /**
     * Shortcut for {@link #get(ObjectId, Class) get(id, RevFeatureType.class)}
     */
    public RevFeatureType getFeatureType(ObjectId id);

    /**
     * Shortcut for {@link #get(ObjectId, Class) get(id, RevCommit.class)}
     */
    public RevCommit getCommit(ObjectId id);

    /**
     * Shortcut for {@link #get(ObjectId, Class) get(id, RevTag.class)}
     */
    public RevTag getTag(ObjectId id);

    /**
     * Adds an object to the database with the given {@link ObjectId id}. If an object with the same
     * id already exists, it will not be inserted.
     * 
     * @param object the object to insert, key'ed by its {@link RevObject#getId() id}
     * @return true if the object was inserted, false otherwise
     */
    public boolean put(RevObject object);

    /**
     * Deletes the object with the provided {@link ObjectId id} from the database.
     * 
     * @param objectId the id of the object to delete
     */
    public void delete(ObjectId objectId);

    /**
     * Shorthand for {@link #getAll(Iterable, BulkOpListener)} with
     * {@link BulkOpListener#NOOP_LISTENER} as second argument
     */
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids);

    /**
     * Query method to retrieve a collection of objects from the database, given a collection of
     * object identifiers.
     * <p>
     * The returned iterator may not preserve the order of the argument list of ids.
     * <p>
     * The {@link BulkOpListener#found(RevObject, Integer) listener.found} method is going to be
     * called for each object found as the returned iterator is traversed.
     * <p>
     * The {@link BulkOpListener#notFound(ObjectId) listener.notFound} method is to be called for
     * each object not found as the iterator is traversed.
     * <p>
     * Note, however, it's recommended the client code provides unique object ids in the {@code ids}
     * argument, as if it were a {@code java.util.Set}. The behavior on what the {@link ObjectStore}
     * implementation does if repeated ids are requested is undefined. Some implementations may
     * ignore them and return less objects than requested, others return duplicated objects matching
     * the argument id count. In any case the calls to {@code listener.found} and
     * {@code listener.notFound} might be misleading.
     * 
     * @param ids an iterable holding the list of ids to fetch from the database
     * @param listener a listener that gets notified of {@link BulkOpListener#deleted(ObjectId)
     *        deleted} and {@link BulkOpListener#notFound(ObjectId) not found} items
     * @return an iterator with the objects <b>found</b> on the database, in no particular order
     */
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids, BulkOpListener listener);

    /**
     * Query method to retrieve a collection of objects of a given type from the database, given a
     * collection of object identifiers.
     * <p>
     * The returned iterator may not preserve the order of the argument list of ids.
     * <p>
     * The {@link BulkOpListener#found(RevObject, Integer) listener.found} method is going to be
     * called for each object found as the returned iterator is traversed.
     * <p>
     * The {@link BulkOpListener#notFound(ObjectId) listener.notFound} method is to be called for
     * each object not found as the iterator is traversed.
     * <p>
     * If the object for one of the query ids exists but is not of the requested type, it's ignored
     * and reported as {@link BulkOpListener#notFound(ObjectId) not found} to the listener.
     * <p>
     * Note, however, it's recommended the client code provides unique object ids in the {@code ids}
     * argument, as if it were a {@code java.util.Set}. The behavior on what the {@link ObjectStore}
     * implementation does if repeated ids are requested is undefined. Some implementations may
     * ignore them and return less objects than requested, others return duplicated objects matching
     * the argument id count. In any case the calls to {@code listener.found} and
     * {@code listener.notFound} might be misleading.
     * 
     * @param ids an iterable holding the list of ids to fetch from the database
     * @param listener a listener that gets notified of {@link BulkOpListener#deleted(ObjectId)
     *        deleted} and {@link BulkOpListener#notFound(ObjectId) not found} items
     * @return an iterator with the objects <b>found</b> on the database, in no particular order
     */
    public <T extends RevObject> Iterator<T> getAll(Iterable<ObjectId> ids, BulkOpListener listener,
            Class<T> type);

    /**
     * Shorthand for {@link #putAll(Iterator, BulkOpListener)} with
     * {@link BulkOpListener#NOOP_LISTENER} as second argument
     */
    public void putAll(Iterator<? extends RevObject> objects);

    /**
     * Inserts all objects into the object database
     * <p>
     * Objects already present (given its {@link RevObject#getId() id} shall not be inserted.
     * <p>
     * For each object that gets actually inserted (i.e. an object with the same id didn't
     * previously exist), {@link BulkOpListener#inserted(RevObject, Integer) listener.inserted}
     * method is called.
     * <p>
     * This method is not atomic, implementations might insert in batches and if at some point the
     * operation fails, some of the objects might have been persisted and other don't. In any case,
     * the listener's {@link BulkOpListener#inserted inserted} callback shall only be called for an
     * object once it has effectively been stored.
     * 
     * @param objects the objects to request for insertion into the object database
     * @param listener a listener to get notifications of actually inserted objects
     */
    public void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener);

    /**
     * Shorthand for {@link #deleteAll(Iterator, BulkOpListener)} with
     * {@link BulkOpListener#NOOP_LISTENER} as second argument
     */
    public void deleteAll(Iterator<ObjectId> ids);

    /**
     * Requests to delete all objects in the argument list of object ids from the object database.
     * <p>
     * For each object found and deleted, the {@link BulkOpListener#deleted(ObjectId)
     * listener.deleted} method will be called
     * <p>
     * For each object not found, the {@link BulkOpListener#notFound(ObjectId) listener.notFound}
     * method will be called
     * 
     * @param ids the identifiers of objects to delete
     * @param listener a listener to receive notifications of deleted and not found objects
     */
    public void deleteAll(Iterator<ObjectId> ids, BulkOpListener listener);

    @Beta
    public <T extends RevObject> AutoCloseableIterator<ObjectInfo<T>> getObjects(
            Iterator<NodeRef> nodes, BulkOpListener listener, Class<T> type);

    @Beta
    public default <T extends RevObject> AutoCloseableIterator<DiffObjectInfo<T>> getDiffObjects(
            Iterator<DiffEntry> diffEntries, Class<T> type) {
        return new ObjectStoreDiffObjectIterator<T>(diffEntries, type, this);
    }

}