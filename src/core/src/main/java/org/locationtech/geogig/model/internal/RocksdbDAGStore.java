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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

class RocksdbDAGStore {

    private RocksDB db;

    private WriteOptions writeOptions;

    private ReadOptions readOptions;

    private ColumnFamilyHandle column;

    public RocksdbDAGStore(RocksDB db) {
        this.db = db;
        ColumnFamilyDescriptor columnDescriptor = new ColumnFamilyDescriptor(
                "trees".getBytes(Charsets.UTF_8));
        try {
            column = db.createColumnFamily(columnDescriptor);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
        writeOptions = new WriteOptions();
        writeOptions.setDisableWAL(true);
        writeOptions.setSync(false);

        readOptions = new ReadOptions();
        readOptions.setFillCache(false).setVerifyChecksums(false);
    }

    public void close() {
        readOptions.close();
        writeOptions.close();
        column.close();
        db = null;
    }

    public DAG getOrCreate(final TreeId treeId, final ObjectId originalTreeId) {
        byte[] key = toKey(treeId);
        DAG dag;
        try {
            dag = getInternal(treeId);
            if (dag == null) {
                dag = new DAG(originalTreeId);
                putInternal(key, dag);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return dag;
    }

    @Nullable
    private DAG getInternal(final TreeId id) {
        DAG dag = null;
        try {
            byte[] key = toKey(id);
            byte[] value = db.get(column, readOptions, key);
            if (null != value) {
                dag = decode(value);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return dag;
    }

    public Map<TreeId, DAG> getTrees(final Set<TreeId> ids) {
        try {
            return getInternal(ids);
        } catch (Exception e) {
            throw Throwables.propagate(Throwables.getRootCause(e));
        }
    }

    private Map<TreeId, DAG> getInternal(final Set<TreeId> ids) {
        Map<TreeId, DAG> res = new HashMap<>();
        byte[] valueBuff = new byte[16 * 1024];
        ids.forEach((id) -> {
            byte[] key = toKey(id);
            byte[] val = valueBuff;
            try {
                int size = db.get(column, readOptions, key, val);
                Preconditions.checkState(RocksDB.NOT_FOUND != size);
                if (size > valueBuff.length) {
                    val = db.get(column, readOptions, key);
                }
                DAG dag = decode(val);
                res.put(id, dag);
            } catch (RocksDBException e) {
                throw Throwables.propagate(e);
            }
        });
        Preconditions.checkState(res.size() == ids.size());

        return res;
    }

    public void putAll(Map<TreeId, DAG> dags) {
        Map<TreeId, DAG> changed = Maps.filterValues(dags, (d) -> d.isMutated());
        // treeCache.putAll(changed);

        try (WriteBatch batch = new WriteBatch()) {
            changed.forEach((id, dag) -> batch.put(column, toKey(id), encode(dag)));
            db.write(writeOptions, batch);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    private void putInternal(byte[] key, DAG dag) {
        byte[] value = encode(dag);
        try {
            db.put(column, writeOptions, key, value);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    private byte[] toKey(TreeId treeId) {
        return treeId.bucketIndicesByDepth;
    }

    private DAG decode(byte[] value) {
        DAG dag;
        try {
            dag = DAG.deserialize(ByteStreams.newDataInput(value));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return dag;
    }

    private byte[] encode(DAG bucketDAG) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        try {
            DAG.serialize(bucketDAG, out);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return out.toByteArray();
    }
}
