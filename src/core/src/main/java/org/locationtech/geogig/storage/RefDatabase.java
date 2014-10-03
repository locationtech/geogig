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
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.di.Singleton;
import org.locationtech.geogig.repository.RepositoryConnectionException;

/**
 * Provides an interface for GeoGig reference databases.
 * 
 */
@Singleton
public interface RefDatabase extends Closeable {

    /**
     * Locks access to the main repository refs.
     */
    public abstract void lock() throws TimeoutException;

    /**
     * Performs any setup required before first open() including default configuration
     */
    public abstract void configure() throws RepositoryConnectionException;

    /**
     * Verify the configuration before opening.
     */
    public abstract void checkConfig() throws RepositoryConnectionException;

    /**
     * Unlocks access to the main repository refs.
     */
    public abstract void unlock();

    /**
     * Creates the reference database.
     */
    public abstract void create();

    /**
     * Closes the reference database.
     */
    public abstract void close();

    /**
     * @param name the name of the ref (e.g. {@code "refs/remotes/origin"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     */
    public abstract String getRef(String name);

    /**
     * @param name the name of the symbolic ref (e.g. {@code "HEAD"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     */
    public abstract String getSymRef(String name);

    /**
     * @param refName the name of the ref
     * @param refValue the value of the ref
     * @return {@code null} if the ref didn't exist already, its old value otherwise
     */
    public abstract void putRef(String refName, String refValue);

    /**
     * @param name the name of the ref
     * @param val the value of the ref
     * @return {@code null} if the ref didn't exist already, its old value otherwise
     */
    public abstract void putSymRef(String name, String val);

    /**
     * @param refName the name of the ref to remove (e.g. {@code "HEAD"},
     *        {@code "refs/remotes/origin"}, etc).
     * @return the value of the ref before removing it, or {@code null} if it didn't exist
     */
    public abstract String remove(String refName);

    /**
     * @return all known references, including top level ones like HEAD, WORK_HEAD, and STAGE_HEAD,
     *         but not including transaction references (e.g. the ones under the
     *         {@link Ref#TRANSACTIONS_PREFIX transactions/} prefix.
     */
    public abstract Map<String, String> getAll();

    /**
     * All refs under a specific prefix (.e.g. {@code /}, {@code /refs}, {@code /refs/remotes}, etc
     * 
     * @param prefix
     * @return
     */
    public abstract Map<String, String> getAll(final String prefix);

    /**
     * Removes all references under the given {@code namespace} and the namespace itself
     * 
     * @param namespace the refs namespace to remote
     * @return the references removed, may be empty.
     */
    public abstract Map<String, String> removeAll(String namespace);
}
