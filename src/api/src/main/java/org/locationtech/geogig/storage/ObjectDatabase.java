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

import org.locationtech.geogig.repository.RepositoryConnectionException;

/**
 * The {@code ObjectDatabase} must be per repository singleton instance providing access to the main
 * repository {@link ObjectStore} as well as extra services such as access to the conflicts and
 * blobs stores.
 * 
 * @since 1.0
 */
public interface ObjectDatabase extends ObjectStore {

    /**
     * Performs any setup required before first open, including setting default configuration.
     */
    public void configure() throws RepositoryConnectionException;

    /**
     * @return {@code true} if the {@code ObjectDatabase} is read-only
     */
    public boolean isReadOnly();

    /**
     * Verify the configuration before opening the database.
     * 
     * @return {@code true} if the config was set, {@code false} otherwise
     * @throws RepositoryConnectionException if the config is incompatible
     */
    public boolean checkConfig() throws RepositoryConnectionException;

    public GraphDatabase getGraphDatabase();

    /**
     * @return the {@link BlobStore} associated with this {@code ObjectDatabase}
     */
    public BlobStore getBlobStore();
}
