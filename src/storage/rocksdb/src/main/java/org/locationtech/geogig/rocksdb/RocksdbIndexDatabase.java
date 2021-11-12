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

import java.io.DataInput;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.rocksdb.DBHandle.RocksDBReference;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.impl.IndexInfoSerializer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import lombok.NonNull;

/**
 * 
 * @implNote this class uses the rocksdb "default" column family to store the {@link RevTree}s (as
 *           provided by its superclass), and the "indexMetadata" column family to store
 *           {@link IndexInfo}s.
 */
public class RocksdbIndexDatabase extends RocksdbObjectStore implements IndexDatabase {

    private ColumnFamilyHandle indexMetadataColumn;

    private ColumnFamilyHandle indexMappingsColumn;

    public RocksdbIndexDatabase(@NonNull File dbdir, boolean readOnly) {
        super(dbdir, readOnly);
    }

    public @Override synchronized void open() {
        if (!isOpen()) {
            super.open(Set.of("indexMetadata", "indexMappings"));
            this.indexMetadataColumn = super.dbhandle.getColumnFamily("indexMetadata");
            this.indexMappingsColumn = super.dbhandle.getColumnFamily("indexMappings");
            Preconditions.checkState(this.indexMetadataColumn != null);
            Preconditions.checkState(this.indexMappingsColumn != null);
        }
    }

    private static byte[] indexKey(String treeName, @Nullable String attributeName) {
        StringBuilder sb = new StringBuilder(treeName).append(".");
        if (attributeName != null) {
            sb.append(attributeName);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public @Override IndexInfo createIndexInfo(String treeName, String attributeName,
            IndexType strategy, @Nullable Map<String, Object> metadata) {
        checkWritable();
        IndexInfo index = new IndexInfo(treeName, attributeName, strategy, metadata);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        IndexInfoSerializer.serialize(index, out);
        byte[] key = indexKey(treeName, attributeName);
        byte[] value = out.toByteArray();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            dbRef.db().put(indexMetadataColumn, key, value);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return index;
    }

    public @Override IndexInfo updateIndexInfo(String treeName, String attributeName,
            IndexType strategy, Map<String, Object> metadata) {
        return createIndexInfo(treeName, attributeName, strategy, metadata);
    }

    private IndexInfo readIndex(byte[] indexBytes) {
        DataInput input = ByteStreams.newDataInput(indexBytes);
        IndexInfo index = IndexInfoSerializer.deserialize(input);
        return index;
    }

    public @Override Optional<IndexInfo> getIndexInfo(String treeName, String attributeName) {
        checkOpen();
        byte[] indexKey = indexKey(treeName, attributeName);
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            byte[] indexBytes = dbRef.db().get(indexMetadataColumn, indexKey);
            if (indexBytes != null) {
                return Optional.of(readIndex(indexBytes));
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    public @Override List<IndexInfo> getIndexInfos(String treeName) {
        checkOpen();
        byte[] indexKey = indexKey(treeName, null);
        List<IndexInfo> indexes = new ArrayList<>();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            try (RocksIterator it = dbRef.db().newIterator(indexMetadataColumn)) {
                it.seek(indexKey);
                while (it.isValid()) {
                    byte[] key = it.key();
                    for (int i = 0; i < indexKey.length; i++) {
                        if (indexKey[i] != key[i]) {
                            return indexes;
                        }
                    }
                    indexes.add(readIndex(it.value()));
                    it.next();
                }
            }
        }
        return indexes;
    }

    public @Override List<IndexInfo> getIndexInfos() {
        checkOpen();
        List<IndexInfo> indexes = new ArrayList<>();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            try (RocksIterator it = dbRef.db().newIterator(indexMetadataColumn)) {
                it.seekToFirst();
                while (it.isValid()) {
                    indexes.add(readIndex(it.value()));
                    it.next();
                }
            }
        }
        return indexes;
    }

    public @Override boolean dropIndex(IndexInfo index) {
        checkOpen();
        byte[] indexKey = indexKey(index.getTreeName(), index.getAttributeName());
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            try (RocksIterator it = dbRef.db().newIterator(indexMetadataColumn)) {
                it.seek(indexKey);
                if (it.isValid()) {
                    byte[] key = it.key();
                    if (key.length == indexKey.length) {
                        for (int i = 0; i < indexKey.length; i++) {
                            if (indexKey[i] != key[i]) {
                                return false;
                            }
                        }
                        dbRef.db().delete(indexMetadataColumn, key);
                        clearIndex(index);
                        return true;
                    }
                }
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    public @Override void clearIndex(IndexInfo index) {
        checkOpen();
        byte[] mappingKey = computeIndexTreePrefixLookupKey(index.getId());
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            try (RocksIterator it = dbRef.db().newIterator(indexMappingsColumn)) {
                it.seek(mappingKey);
                while (it.isValid()) {
                    byte[] key = it.key();
                    for (int i = 0; i < mappingKey.length; i++) {
                        if (mappingKey[i] != key[i]) {
                            return;
                        }
                    }
                    it.next();
                    dbRef.db().delete(indexMappingsColumn, key);
                }
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public @Override void addIndexedTree(IndexInfo index, ObjectId originalTree,
            ObjectId indexedTree) {
        byte[] indexTreeLookupId = computeIndexTreeLookupId(index.getId(), originalTree);
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            dbRef.db().put(indexMappingsColumn, indexTreeLookupId, indexedTree.getRawValue());
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public @Override Optional<ObjectId> resolveIndexedTree(IndexInfo index, ObjectId treeId) {
        byte[] indexTreeLookupId = computeIndexTreeLookupId(index.getId(), treeId);

        byte[] indexTreeBytes;
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            indexTreeBytes = dbRef.db().get(indexMappingsColumn, indexTreeLookupId);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }

        if (indexTreeBytes != null) {
            return Optional.of(ObjectId.create(indexTreeBytes));
        }
        return Optional.empty();
    }

    private static ObjectId parseTreeIdFromKey(byte[] key) {
        String stringKey = new String(key, StandardCharsets.UTF_8);
        String stringId = stringKey.substring(stringKey.indexOf('.') + 1);
        return ObjectId.valueOf(stringId);
    }

    private static byte[] computeIndexTreeLookupId(ObjectId indexId, @Nullable ObjectId treeId) {
        StringBuilder sb = new StringBuilder(indexId.toString()).append(".");
        if (treeId != null) {
            sb.append(treeId.toString());
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] computeIndexTreePrefixLookupKey(ObjectId indexId) {
        return computeIndexTreeLookupId(indexId, null);
    }

    public @Override AutoCloseableIterator<IndexTreeMapping> resolveIndexedTrees(IndexInfo index) {
        checkOpen();
        List<IndexTreeMapping> mappings = new ArrayList<>();

        final byte[] keyPrefix = computeIndexTreePrefixLookupKey(index.getId());
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            try (RocksIterator it = dbRef.db().newIterator(indexMappingsColumn)) {
                it.seek(keyPrefix);
                while (it.isValid() && prefixEquals(keyPrefix, it.key())) {
                    ObjectId treeId = parseTreeIdFromKey(it.key());
                    ObjectId indexTreeId = ObjectId.create(it.value());
                    mappings.add(new IndexTreeMapping(treeId, indexTreeId));
                    it.next();
                }
            } catch (Exception e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
        }
        return AutoCloseableIterator.fromIterator(mappings.iterator());
    }

    private boolean prefixEquals(byte[] keyPrefix, byte[] key) {
        int length = keyPrefix.length;
        if (length < key.length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (keyPrefix[i] != key[i]) {
                return false;
            }
        }
        return true;
    }
}
