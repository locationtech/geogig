/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.model.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

class RocksdbDAGStore {

    private RocksDB db;

    private WriteOptions writeOptions;

    private ReadOptions readOptions;

    private ColumnFamilyHandle column;

    private BloomFilter bloomFilter;

    private ColumnFamilyOptions colFamilyOptions;

    public RocksdbDAGStore(RocksDB db) {
        this.db = db;
        try {
            // enable bloom filter to speed up RocksDB.get() calls
            BlockBasedTableConfig tableFormatConfig = new BlockBasedTableConfig();
            bloomFilter = new BloomFilter();
            // tableFormatConfig.setFilter(bloomFilter);
            // tableFormatConfig.setBlockSize(64 * 1024);
            // tableFormatConfig.setBlockCacheSize(4 * 1024 * 1024);

            colFamilyOptions = new ColumnFamilyOptions();
            colFamilyOptions.setTableFormatConfig(tableFormatConfig);

            byte[] tableNameKey = "trees".getBytes(Charsets.UTF_8);
            ColumnFamilyDescriptor columnDescriptor = new ColumnFamilyDescriptor(tableNameKey,
                    colFamilyOptions);
            column = db.createColumnFamily(columnDescriptor);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        writeOptions = new WriteOptions();
        writeOptions.setDisableWAL(true);
        writeOptions.setSync(false);

        readOptions = new ReadOptions();
        readOptions.setFillCache(true).setVerifyChecksums(false);
    }

    public void close() {
        readOptions.close();
        writeOptions.close();
        column.close();
        bloomFilter.close();
        colFamilyOptions.close();
        db = null;
    }

    public DAG getOrCreate(final TreeId treeId, final ObjectId originalTreeId) {
        byte[] key = toKey(treeId);
        DAG dag;
        try {
            dag = getInternal(treeId, key);
            if (dag == null) {
                dag = new DAG(treeId, originalTreeId);
                putInternal(key, dag);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return dag;
    }

    @Nullable
    private DAG getInternal(TreeId id, final byte[] key) {
        DAG dag = null;
        try {
            byte[] value = db.get(column, readOptions, key);
            if (null != value) {
                dag = decode(id, value);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return dag;
    }

    public List<DAG> getTrees(final Set<TreeId> ids) throws NoSuchElementException {
        return getInternal2(ids);
    }

    private List<DAG> getInternal2(final Set<TreeId> ids) throws NoSuchElementException {
        Map<byte[], byte[]> multiGet;
        try {
            multiGet = db.multiGet(readOptions, Collections.nCopies(ids.size(), column),
                    Lists.transform(new ArrayList<>(ids), id -> toKey(id)));

        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        Preconditions.checkState(multiGet.size() == ids.size());

        return multiGet.entrySet().stream().map(e -> decode(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public void putAll(Map<TreeId, DAG> dags) {
        Map<TreeId, DAG> changed = Maps.filterValues(dags, (d) -> d.isMutated());

        try (WriteBatch batch = new WriteBatch()) {
            for (Map.Entry<TreeId, DAG> e : changed.entrySet()) {
                batch.put(column, toKey(e.getKey()), encode(e.getValue()));
                // db.put(column, writeOptions, toKey(e.getKey()), encode(e.getValue()));
            }
            db.write(writeOptions, batch);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    private void putInternal(byte[] key, DAG dag) {
        byte[] value = encode(dag);
        try {
            db.put(column, writeOptions, key, value);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] toKey(TreeId treeId) {
        return treeId.bucketIndicesByDepth;
    }

    private TreeId fromKey(byte[] key) {
        return new TreeId(key);
    }

    private DAG decode(byte[] id, byte[] value) {
        return decode(fromKey(id), value);
    }

    private DAG decode(TreeId id, byte[] value) {
        DAG dag;
        try {
            dag = DAG.deserialize(id, ByteStreams.newDataInput(value));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return dag;
    }

    private byte[] encode(DAG bucketDAG) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        try {
            DAG.serialize(bucketDAG, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }
}
