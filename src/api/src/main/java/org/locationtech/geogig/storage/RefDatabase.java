/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;

import lombok.NonNull;

/**
 * Provides an interface for GeoGig reference databases.
 * 
 * @since 1.0
 */
public interface RefDatabase extends Store {

    /**
     * Locks access to the main repository refs.
     */
    public void lock() throws TimeoutException;

    /**
     * Unlocks access to the main repository refs.
     */
    public void unlock();

    /**
     * Retrieves a ref with the specified name.
     * 
     * @param name the name of the ref (e.g. {@code "refs/remotes/origin"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     * @since 2.0
     */
    public Optional<Ref> get(@NonNull String name);

    public List<Ref> getAllPresent(@NonNull Iterable<String> names);

    /**
     * @return all known references, including top level ones like HEAD, WORK_HEAD, and STAGE_HEAD,
     *         but not including transaction references (e.g. the ones under the
     *         {@link Ref#TRANSACTIONS_PREFIX transactions/} prefix.
     */
    public @NonNull List<Ref> getAll();

    /**
     * Retrieves all refs under a specific prefix (.e.g. {@code /}, {@code /refs},
     * {@code /refs/remotes}, etc.
     * 
     * @param prefix the prefix
     * @return the refs that matched the prefix
     */
    public @NonNull List<Ref> getAll(@NonNull String prefix);

    /**
     * Adds a ref to the database.
     * 
     * @param refName the name of the ref
     * @param refValue the value of the ref
     * @return the old value
     */
    public @NonNull RefChange put(@NonNull Ref ref);

    public @NonNull RefChange putRef(@NonNull String name, @NonNull ObjectId value);

    public @NonNull RefChange putSymRef(@NonNull String name, @NonNull String target);

    public @NonNull List<RefChange> putAll(@NonNull Iterable<Ref> refs);

    /**
     * Removes a ref from the database.
     * 
     * @param refName the name of the ref to remove (e.g. {@code "HEAD"},
     *        {@code "refs/remotes/origin"}, etc).
     * @return the value of the ref before removing it, or {@code null} if it didn't exist
     */
    public @NonNull RefChange delete(@NonNull String refName);

    public @NonNull List<RefChange> delete(@NonNull Iterable<String> refNames);

    public @NonNull RefChange delete(@NonNull Ref ref);

    /**
     * Removes all references.
     * 
     * @return the references removed, may be empty.
     */
    public @NonNull List<Ref> deleteAll();

    /**
     * Removes all references under the given {@code namespace} and the namespace itself
     * 
     * @param namespace the refs namespace to remove (e.g. {@code "refs/remotes/origin"}, etc)
     * @return the references removed, may be empty.
     */
    public List<Ref> deleteAll(@NonNull String namespace);
}
