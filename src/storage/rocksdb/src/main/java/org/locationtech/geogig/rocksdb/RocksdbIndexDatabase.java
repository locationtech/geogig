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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.inject.Inject;

public class RocksdbIndexDatabase extends RocksdbObjectStore implements IndexDatabase {

    private static String INDEX_KEY_PREFIX = "index";

    private final ConfigDatabase configdb;

    private Map<String, List<Index>> indexes;

    @Inject
    public RocksdbIndexDatabase(Platform platform, Hints hints, ConfigDatabase configdb) {
        super(platform, hints, "index.rocksdb");
        this.configdb = configdb;
        this.indexes = new HashMap<String, List<Index>>();
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

        try (RocksDBReference dbRef = dbhandle.getReference()) {
            try (RocksIterator it = dbRef.db().newIterator()) {
                it.seek(INDEX_KEY_PREFIX.getBytes());
                while (it.isValid()) {
                    String keyString = new String(it.key());
                    if (keyString.startsWith(INDEX_KEY_PREFIX)) {
                        Index index = readIndex(it.value());
                        addIndex(index);
                        it.next();
                    } else {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        super.close();
        this.indexes.clear();
    }

    @Override
    public Index createIndex(String treeName, String attributeName, IndexType strategy) {
        Index index = new Index(treeName, attributeName, strategy);
        addIndex(index);
        writeIndex(index);
        return index;
    }

    private void writeIndex(Index index) {
        UUID id = UUID.randomUUID();

        StringBuilder indexOutput = new StringBuilder();
        indexOutput.append(index.getTreeName()).append("\n");
        indexOutput.append(index.getAttributeName()).append("\n");
        indexOutput.append(index.getIndexType().toString()).append("\n");

        String indexKey = INDEX_KEY_PREFIX + id.toString();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            dbRef.db().put(indexKey.getBytes(), indexOutput.toString().getBytes());
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    private Index readIndex(byte[] indexBytes) {
        String treeName, attributeName;
        IndexType indexType;
        ByteArrayInputStream stream = null;
        BufferedReader reader = null;
        Index index = null;
        try {
            stream = new ByteArrayInputStream(indexBytes);
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            treeName = reader.readLine();
            attributeName = reader.readLine();
            indexType = IndexType.valueOf(reader.readLine());
            index = new Index(treeName, attributeName, indexType);
        } catch (IOException e) {
            Throwables.propagate(e);
        } finally {
            Closeables.closeQuietly(reader);
            Closeables.closeQuietly(stream);
        }
        return index;
    }

    private void addIndex(Index index) {
        String treeName = index.getTreeName();
        if (indexes.containsKey(treeName)) {
            indexes.get(treeName).add(index);
        } else {
            indexes.put(treeName, Lists.newArrayList(index));
        }
    }

    @Override
    public Optional<Index> getIndex(String treeName, String attributeName) {
        if (indexes.containsKey(treeName)) {
            for (Index index : indexes.get(treeName)) {
                if (index.getAttributeName().equals(attributeName)) {
                    return Optional.of(index);
                }
            }
        }
        return Optional.absent();
    }

    @Override
    public void updateIndex(Index index, ObjectId originalTree, ObjectId indexedTree) {
        ObjectId indexTreeLookupId = computeIndexTreeLookupId(index.getId(), originalTree);
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            dbRef.db().put(indexTreeLookupId.getRawValue(), indexedTree.getRawValue());
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Optional<ObjectId> resolveTreeId(Index index, ObjectId treeId) {
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
