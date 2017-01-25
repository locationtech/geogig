/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import static org.locationtech.geogig.rocksdb.RocksdbStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.rocksdb.RocksdbStorageProvider.VERSION;

import java.io.File;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StorageType;

import com.google.inject.Inject;

public class RocksdbObjectDatabase extends RocksdbObjectStore implements ObjectDatabase {

    private RocksdbConflictsDatabase conflicts;

    private RocksdbBlobStore blobs;

    private final ConfigDatabase configdb;

    @Inject
    public RocksdbObjectDatabase(Platform platform, Hints hints, ConfigDatabase configdb) {
        super(platform, hints);
        this.configdb = configdb;
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
    public boolean isReadOnly() {
        return super.readOnly;
    }

    @Override
    public RocksdbConflictsDatabase getConflictsDatabase() {
        return conflicts;
    }

    @Override
    public RocksdbBlobStore getBlobStore() {
        return blobs;
    }

    @Override
    public synchronized void open() {
        if (isOpen()) {
            return;
        }
        super.open();
        File basedir = new File(super.path).getParentFile();
        File conflictsDir = new File(basedir, "conflicts");
        File blobsDir = new File(super.path, "blobs");
        conflictsDir.mkdir();
        blobsDir.mkdir();
        this.conflicts = new RocksdbConflictsDatabase(conflictsDir);
        this.blobs = new RocksdbBlobStore(blobsDir, super.readOnly);
    }

    @Override
    public synchronized void close() {
        try {
            super.close();
        } finally {
            RocksdbConflictsDatabase conflicts = this.conflicts;
            RocksdbBlobStore blobs = this.blobs;
            this.conflicts = null;
            this.blobs = null;
            try {
                if (conflicts != null) {
                    conflicts.close();
                }
            } finally {
                if (blobs != null) {
                    blobs.close();
                }
            }
        }
    }
}
