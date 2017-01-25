/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.VERSION;

import java.net.URISyntaxException;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StorageType;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

/**
 * PostgreSQL implementation for {@link ObjectDatabase}.
 * <p>
 * TODO: document/force use of {@code SET constraint_exclusion=ON}
 */
public class PGObjectDatabase extends PGObjectStore implements ObjectDatabase {
    private PGConflictsDatabase conflicts;

    private PGBlobStore blobStore;

    private final boolean readOnly;

    @Inject
    public PGObjectDatabase(final ConfigDatabase configdb, final Hints hints)
            throws URISyntaxException {
        this(configdb, Environment.get(hints), readOnly(hints));
    }

    private static boolean readOnly(Hints hints) {
        return hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
    }

    public PGObjectDatabase(final ConfigDatabase configdb, final Environment config,
            final boolean readOnly) {
        super(configdb, config);
        this.readOnly = readOnly;
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.OBJECT.configure(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.OBJECT.verify(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public void open() {
        super.open();
        if (dataSource == null) {
            return;
        }

        final int repositoryId = config.getRepositoryId();
        final String conflictsTable = config.getTables().conflicts();
        final String blobsTable = config.getTables().blobs();

        conflicts = new PGConflictsDatabase(dataSource, conflictsTable, repositoryId);
        blobStore = new PGBlobStore(dataSource, blobsTable, repositoryId);
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void close() {
        if (dataSource != null) {
            conflicts = null;
        }
        super.close();
    }

    @Override
    public ConflictsDatabase getConflictsDatabase() {
        Preconditions.checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();
        return conflicts;
    }

    @Override
    public PGBlobStore getBlobStore() {
        Preconditions.checkState(isOpen(), "Database is closed");
        config.checkRepositoryExists();
        return blobStore;
    }

    @Override
    public void checkWritable() {
        checkOpen();
        if (readOnly) {
            throw new IllegalStateException("db is read only.");
        }
    }
}