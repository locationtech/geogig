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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.rocksdb.DBOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;
import org.rocksdb.WriteBatchWithIndex;
import org.rocksdb.WriteOptions;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

class RocksdbDAGStore {

    private Supplier<RocksDB> _dbSupplier;

    private RocksDB _db;

    private WriteOptions writeOptions;

    private ReadOptions readOptions;

    private DBOptions batchOptions;

    private WriteBatchWithIndex batch;

    private final int WRITE_THRESHOLD = 10_000;

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    public RocksdbDAGStore(Supplier<RocksDB> db) {
        this._dbSupplier = db;
        boolean overwriteKey = true;
        this.batchOptions = new DBOptions();
        this.batch = new WriteBatchWithIndex(overwriteKey);
    }

    private RocksDB db() {
        if (_db == null) {
            writeOptions = new WriteOptions();
            writeOptions.setDisableWAL(true);
            writeOptions.setSync(false);

            readOptions = new ReadOptions();
            readOptions.setFillCache(true).setVerifyChecksums(false);
            _db = _dbSupplier.get();
        }
        return _db;
    }

    public void close() {
        lock.writeLock().lock();
        try {
            close(batch);
            close(batchOptions);
            close(readOptions);
            close(writeOptions);
        } finally {
            lock.writeLock().unlock();
        }
        _db = null;
        _dbSupplier = null;
    }

    private void flush() {
        lock.writeLock().lock();
        try {
            if (batch.count() >= WRITE_THRESHOLD) {
                db().write(writeOptions, batch);
                batch.clear();
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void close(RocksObject obj) {
        if (obj != null)
            try {
                obj.close();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
    }

    public DAG getOrCreate(final TreeId treeId, final ObjectId originalTreeId) {
        byte[] key = toKey(treeId);
        DAG dag;
        try {
            dag = getInternal(treeId, key);
            if (dag == null) {
                dag = new DAG(treeId, originalTreeId);
                putInternal(key, dag);
                flush();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return dag;
    }

    @Nullable
    private DAG getInternal(TreeId id, final byte[] key) {
        DAG dag = null;
        lock.readLock().lock();
        try {
            byte[] value;
            if (_db == null) {
                value = batch.getFromBatch(batchOptions, key);
            } else {
                value = batch.getFromBatchAndDB(_db, readOptions, key);
            }
            if (null != value) {
                dag = decode(id, value);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.readLock().unlock();
        }
        return dag;
    }

    public List<DAG> getTrees(final Set<TreeId> ids) throws NoSuchElementException {
        List<DAG> dags = new ArrayList<>(ids.size());
        for (TreeId id : ids) {
            DAG dag = getInternal(id, toKey(id));
            Preconditions.checkState(dag != null);
            dags.add(dag);
        }
        return dags;
    }

    public void putAll(Map<TreeId, DAG> dags) {
        Map<TreeId, DAG> changed = Maps.filterValues(dags, (d) -> d.isMutated());
        lock.writeLock().lock();
        try {
            for (Map.Entry<TreeId, DAG> e : changed.entrySet()) {
                putInternal(toKey(e.getKey()), e.getValue());
            }
            flush();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void putInternal(byte[] key, DAG dag) {
        byte[] value = encode(dag);
        lock.writeLock().lock();
        try {
            batch.put(key, value);
        } finally {
            lock.writeLock().unlock();
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
