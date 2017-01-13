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

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Index.IndexType;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.rocksdb.DBHandle.RocksDBReference;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.impl.IndexSerializer;
import org.rocksdb.RocksDBException;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.hash.Hasher;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

public class RocksdbIndexDatabase extends RocksdbObjectStore implements IndexDatabase {

    private final ConfigDatabase configdb;

    @Inject
    public RocksdbIndexDatabase(Platform platform, Hints hints, ConfigDatabase configdb) {
        super(platform, hints, "index.rocksdb");
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
    public Index createIndex(String treeName, String attributeName, IndexType strategy,
            @Nullable Map<String, Object> metadata) {
        Index index = new Index(treeName, attributeName, strategy, metadata);
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
            DataOutput out = ByteStreams.newDataOutput(outStream);
            IndexSerializer.serialize(index, out);
            this.putInternal(index.getId(), outStream.toByteArray());
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return index;
    }

    @Override
    public Optional<Index> getIndex(String treeName, String attributeName) {
        ObjectId indexId = Index.getIndexId(treeName, attributeName);
        try (InputStream inputStream = this.getRawInternal(indexId, false)) {
            if (inputStream != null) {
                DataInput in = new DataInputStream(inputStream);
                Index index = IndexSerializer.deserialize(in);
                return Optional.of(index);
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return Optional.absent();
    }

    @Override
    public void addIndexedTree(Index index, ObjectId originalTree, ObjectId indexedTree) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), originalTree);
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            dbRef.db().put(indexTreeLookupId.getRawValue(), indexedTree.getRawValue());
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Optional<ObjectId> resolveIndexedTree(Index index, ObjectId treeId) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), treeId);
        InputStream indexTreeStream = this.getRawInternal(indexTreeLookupId, false);
        if (indexTreeStream != null) {
            try {
                byte[] indexTreeBytes = ByteStreams.toByteArray(indexTreeStream);
                indexTreeStream.close();
                return Optional.of(ObjectId.createNoClone(indexTreeBytes));
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
        return Optional.absent();
    }

    private ObjectId computeIndexTreeLookupId(ObjectId indexId, ObjectId treeId) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        hasher.putBytes(indexId.getRawValue());
        hasher.putBytes(treeId.getRawValue());
        return ObjectId.createNoClone(hasher.hash().asBytes());
    }
}
