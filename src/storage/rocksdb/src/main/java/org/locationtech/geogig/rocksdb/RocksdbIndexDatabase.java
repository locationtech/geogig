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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.rocksdb.DBHandle.RocksDBReference;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.impl.IndexInfoSerializer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

/**
 * 
 * @implNote this class uses the rocksdb "default" column family to store the {@link RevTree}s (as
 *           provided by its superclass), and the "indexMetadata" column family to store
 *           {@link IndexInfo}s.
 */
public class RocksdbIndexDatabase extends RocksdbObjectStore implements IndexDatabase {

    private final ConfigDatabase configdb;

    private ColumnFamilyHandle indexMetadataColumn;

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
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.INDEX.verify(configdb, FORMAT_NAME, VERSION);
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
        super.open(Collections.singleton("indexMetadata"));
        this.indexMetadataColumn = super.dbhandle.getColumnFamily("indexMetadata");
        Preconditions.checkState(this.indexMetadataColumn != null);
    }

    @Override
    public synchronized void close() {
        super.close();
    }

    private static byte[] indexKey(String treeName, @Nullable String attributeName) {
        return (treeName + "." + (attributeName == null ? "" : attributeName))
                .getBytes(Charsets.UTF_8);
    }

    @Override
    public IndexInfo createIndex(String treeName, String attributeName, IndexType strategy,
            @Nullable Map<String, Object> metadata) {
        checkWritable();
        IndexInfo index = new IndexInfo(treeName, attributeName, strategy, metadata);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        IndexInfoSerializer.serialize(index, out);
        byte[] key = indexKey(treeName, attributeName);
        byte[] value = out.toByteArray();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            dbRef.db().put(indexMetadataColumn, key, value);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
        return index;
    }

    private IndexInfo readIndex(byte[] indexBytes) {
        IndexInfo index = IndexInfoSerializer.deserialize(ByteStreams.newDataInput(indexBytes));
        return index;
    }

    @Override
    public Optional<IndexInfo> getIndex(String treeName, String attributeName) {
        checkOpen();
        byte[] indexKey = indexKey(treeName, attributeName);
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            byte[] indexBytes = dbRef.db().get(indexMetadataColumn, indexKey);
            if (indexBytes != null) {
                return Optional.of(readIndex(indexBytes));
            }
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
        return Optional.absent();
    }

    @Override
    public List<IndexInfo> getIndexes(String treeName) {
        checkOpen();
        byte[] indexKey = indexKey(treeName, null);
        List<IndexInfo> indexes = Lists.newArrayList();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            try (RocksIterator it = dbRef.db().newIterator(indexMetadataColumn)) {
                it.seek(indexKey);
                while (it.isValid()) {
                    byte[] key = it.key();
                    for (int i = 0; i < indexKey.length; i++) {
                        if (indexKey[i] != key[i]) {
                            break;
                        }
                    }
                    indexes.add(readIndex(it.value()));
                    it.next();
                }
            }
        }
        return indexes;
    }

    @Override
    public void addIndexedTree(IndexInfo index, ObjectId originalTree, ObjectId indexedTree) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), originalTree);
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            dbRef.db().put(indexMetadataColumn, indexTreeLookupId.getRawValue(),
                    indexedTree.getRawValue());
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Optional<ObjectId> resolveIndexedTree(IndexInfo index, ObjectId treeId) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), treeId);

        byte[] indexTreeBytes;
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            indexTreeBytes = dbRef.db().get(indexMetadataColumn, indexTreeLookupId.getRawValue());
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }

        if (indexTreeBytes != null) {
            return Optional.of(ObjectId.createNoClone(indexTreeBytes));
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
