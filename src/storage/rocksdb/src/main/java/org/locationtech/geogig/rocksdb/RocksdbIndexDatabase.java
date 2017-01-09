/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import static org.locationtech.geogig.rocksdb.RocksdbStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.rocksdb.RocksdbStorageProvider.VERSION;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Index.IndexType;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.StorageType;

import com.google.common.base.Optional;
import com.google.inject.Inject;

public class RocksdbIndexDatabase extends RocksdbObjectStore implements IndexDatabase {

    private final ConfigDatabase configdb;

    @Inject
    public RocksdbIndexDatabase(Platform platform, Hints hints, ConfigDatabase configdb) {
        super(platform, hints);
        this.configdb = configdb;
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.INDEX.configure(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        StorageType.INDEX.verify(configdb, FORMAT_NAME, VERSION);
    }

    @Override
    public boolean isReadOnly() {
        return super.readOnly;
    }

    @Override
    public synchronized void open() {
        if (isOpen()) {
            return;
        }
        super.open();
    }

    @Override
    public synchronized void close() {
        super.close();
    }

    @Override
    public Index createIndex(String featureType, String attribute, IndexType strategy) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public Optional<Index> getIndex(String treeName, String attributeName) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void updateIndex(Index index, ObjectId originalTree, ObjectId indexedTree) {

    }

    @Override
    public Optional<ObjectId> resolveTreeId(Index index, ObjectId treeId) {

        return Optional.absent();
    }
}
