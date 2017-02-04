/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StorageType;

import com.google.inject.Inject;

/**
 * Provides an implementation of a GeoGig object database that utilizes the file system for the
 * storage of objects.
 * 
 * @see FileObjectStore
 */
public class FileObjectDatabase extends FileObjectStore implements ObjectDatabase {

    private final ConfigDatabase configDB;

    private FileConflictsDatabase conflicts;

    private FileBlobStore blobStore;

    /**
     * Constructs a new {@code FileObjectDatabase} using the given platform.
     * 
     * @param platform the platform to use.
     */
    @Inject
    public FileObjectDatabase(final Platform platform, final ConfigDatabase configDB,
            final Hints hints) {
        super(platform, "objects", configDB, hints);
        this.configDB = configDB;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Opens the database for use by GeoGig.
     */
    @Override
    public void open() {
        super.open();
        try {
            conflicts.open();
        } finally {
            try {
                blobStore.open();
            } catch (RuntimeException e) {
                conflicts.close();
                throw e;
            }
        }
    }

    /**
     * Closes the database.
     */
    @Override
    public void close() {
        try {
            if (conflicts != null) {
                conflicts.close();
            }
        } finally {
            blobStore.close();
        }
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.OBJECT.configure(configDB, "file", "1.0");
    }

    @Override
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.OBJECT.verify(configDB, "file", "1.0");
    }


    @Override
    public ConflictsDatabase getConflictsDatabase() {
        return conflicts;
    }

    @Override
    public BlobStore getBlobStore() {
        return blobStore;
    }
}
