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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.rocksdb.DBHandle.RocksDBReference;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.impl.IndexSerializer;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

public class RocksdbIndexDatabase extends RocksdbObjectStore implements IndexDatabase {

    private static final String INDEX_PREFIX = "index";

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

    private static byte[] indexKey(String treeName, @Nullable String attributeName) {
        return (INDEX_PREFIX + "." + treeName + "." + (attributeName != null ? attributeName : ""))
                .getBytes();
    }

    @Override
    public IndexInfo createIndex(String treeName, String attributeName, IndexType strategy,
            @Nullable Map<String, Object> metadata) {
        checkWritable();
        IndexInfo index = new IndexInfo(treeName, attributeName, strategy, metadata);
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
            DataOutput out = ByteStreams.newDataOutput(outStream);
            IndexSerializer.serialize(index, out);
            try (RocksDBReference dbRef = dbhandle.getReference()) {
                dbRef.db().put(indexKey(treeName, attributeName), outStream.toByteArray());
            } catch (RocksDBException e) {
                throw Throwables.propagate(e);
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return index;
    }

    private IndexInfo readIndex(byte[] indexBytes) {
        try (InputStream inputStream = new ByteArrayInputStream(indexBytes)) {
            DataInput in = new DataInputStream(inputStream);
            IndexInfo index = IndexSerializer.deserialize(in);
            return index;
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return null;
    }

    @Override
    public Optional<IndexInfo> getIndex(String treeName, String attributeName) {
        checkOpen();
        byte[] indexKey = indexKey(treeName, attributeName);
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            byte[] indexBytes = dbRef.db().get(indexKey);
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
            try (RocksIterator it = dbRef.db().newIterator()) {
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
            dbRef.db().put(indexTreeLookupId.getRawValue(), indexedTree.getRawValue());
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Optional<ObjectId> resolveIndexedTree(IndexInfo index, ObjectId treeId) {
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
