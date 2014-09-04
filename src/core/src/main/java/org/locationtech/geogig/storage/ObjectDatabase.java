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

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.di.Singleton;
import org.locationtech.geogig.repository.RepositoryConnectionException;

/**
 * Provides an interface for implementations of GeoGig object databases.
 */
@Singleton
public interface ObjectDatabase extends Closeable {

    /**
     * Opens the database. It's safe to call this method multiple times, and only the first call
     * shall take effect.
     */
    public void open();

    /**
     * Performs any setup required before first open, including setting default configuration.
     */
    public void configure() throws RepositoryConnectionException;

    /**
     * Verify the configuration before opening the database.
     */
    public void checkConfig() throws RepositoryConnectionException;

    /**
     * @return true if the database is open, false otherwise
     */
    public boolean isOpen();

    /**
     * Closes the database.
     */
    public void close();

    /**
     * Determines if the given {@link ObjectId} exists in the object database.
     * 
     * @param id the id to search for
     * @return true if the object exists, false otherwise
     */
    public boolean exists(final ObjectId id);

    /**
     * Searches the database for {@link ObjectId}s that match the given partial id.
     * 
     * @param partialId the partial id to search for
     * @return a list of matching results
     */
    public List<ObjectId> lookUp(final String partialId);

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @throws IllegalArgumentException if no blob exists for the given {@code id}
     */
    public RevObject get(ObjectId id) throws IllegalArgumentException;

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @throws IllegalArgumentException if no blob exists for the given {@code id}, or the object
     *         found is not of the required {@code type}
     */
    public <T extends RevObject> T get(ObjectId id, Class<T> type) throws IllegalArgumentException;

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @return the object found, or {@code null} if no object is found
     */
    public @Nullable
    RevObject getIfPresent(ObjectId id);

    /**
     * Reads an object with the given {@link ObjectId id} out of the database.
     * 
     * @return the object found, or {@code null} if no object is found
     * @throws IllegalArgumentException if the object is found but is not of the required
     *         {@code type}
     */
    public @Nullable
    <T extends RevObject> T getIfPresent(ObjectId id, Class<T> type)
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
    public boolean put(final RevObject object);

    /**
     * @return a newly constructed {@link ObjectInserter} for this database
     */
    @Deprecated
    public ObjectInserter newObjectInserter();

    /**
     * Deletes the object with the provided {@link ObjectId id} from the database.
     * 
     * @param objectId the id of the object to delete
     * @return true if the object was deleted, false if it was not found
     */
    public boolean delete(ObjectId objectId);

    /**
     * Shorthand for {@link #getAll(Iterable, BulkOpListener)} with
     * {@link BulkOpListener#NOOP_LISTENER} as second argument
     */
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids);

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
     * 
     * @param ids an iterable holding the list of ids to remove from the database
     * @param listener a listener that gets notified of {@link BulkOpListener#deleted(ObjectId)
     *        deleted} and {@link BulkOpListener#notFound(ObjectId) not found} items
     * @return an iterator with the objects <b>found</b> on the database, in no particular order
     */
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, BulkOpListener listener);

    /**
     * Shorthand for {@link #putAll(Iterator, BulkOpListener)} with
     * {@link BulkOpListener#NOOP_LISTENER} as second argument
     */
    public void putAll(Iterator<? extends RevObject> objects);

    /**
     * Requests to insert all objects into the object database
     * <p>
     * Objects already present (given its {@link RevObject#getId() id} shall not be inserted.
     * <p>
     * For each object that gets actually inserted (i.e. an object with the same id didn't
     * previously exist), {@link BulkOpListener#inserted(RevObject, Integer) listener.inserted}
     * method is called.
     * 
     * @param objects the objects to request for insertion into the object database
     * @param listener a listener to get notifications of actually inserted objects
     */
    public void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener);

    /**
     * Shorthand for {@link #deleteAll(Iterator, BulkOpListener)} with
     * {@link BulkOpListener#NOOP_LISTENER} as second argument
     */
    public long deleteAll(Iterator<ObjectId> ids);

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
     * @return the number of objects actually deleted
     */
    public long deleteAll(Iterator<ObjectId> ids, BulkOpListener listener);
}
