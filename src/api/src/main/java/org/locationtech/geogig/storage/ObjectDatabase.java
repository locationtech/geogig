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

import org.locationtech.geogig.repository.RepositoryConnectionException;

/**
 * The {@code ObjectDatabase} must be per repository singleton instance providing access to the main
 * repository {@link ObjectStore} as well as extra services such as access to the conflicts and
 * blobs stores.
 */
public interface ObjectDatabase extends ObjectStore {

    /**
     * Performs any setup required before first open, including setting default configuration.
     */
    public void configure() throws RepositoryConnectionException;

    public boolean isReadOnly();

    /**
     * Verify the configuration before opening the database.
     */
    public void checkConfig() throws RepositoryConnectionException;

    public ConflictsDatabase getConflictsDatabase();

    public BlobStore getBlobStore();
}
