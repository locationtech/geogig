/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.tempstorage.rocksdb;

import java.io.ByteArrayOutputStream;
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
import org.locationtech.geogig.model.internal.DAG;
import org.locationtech.geogig.model.internal.TreeId;
import org.rocksdb.DBOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;
import org.rocksdb.WriteBatchWithIndex;
import org.rocksdb.WriteOptions;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

class RocksdbDAGStore {

    private Supplier<RocksDB> _dbSupplier;

    private RocksDB _db;

    private WriteOptions writeOptions;

    private ReadOptions readOptions;

    private DBOptions batchOptions;

    private WriteBatchWithIndex batch;

    private final int WRITE_THRESHOLD = 100_000;

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
            readOptions.setFillCache(false).setVerifyChecksums(false);
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

    public DAG getOrCreateTree(final TreeId treeId, final ObjectId originalTreeId) {
        byte[] key = toKey(treeId);
        DAG dag;
        lock.readLock().lock();
        try {
            dag = getInternal(treeId, key);
            if (dag == null) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    dag = new DAG(treeId, originalTreeId);
                    putInternal(key, dag);
                    flush();
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.readLock().unlock();
        }
        return dag;
    }

    @Nullable
    private DAG getInternal(TreeId id, final byte[] key) {
        DAG dag = null;
        try {
            byte[] value = batch.getFromBatch(batchOptions, key);
            if (value == null && _db != null) {
                value = _db.get(readOptions, key);
            }
            if (null != value) {
                dag = decode(id, value);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return dag;
    }

    public List<DAG> getTrees(final Set<TreeId> ids) throws NoSuchElementException {
        List<DAG> dags = new ArrayList<>(ids.size());
        lock.readLock().lock();
        try {
            for (TreeId id : ids) {
                DAG dag = getInternal(id, toKey(id));
                Preconditions.checkState(dag != null);
                dags.add(dag);
            }
        } finally {
            lock.readLock().unlock();
        }
        return dags;
    }

    public void save(Map<TreeId, DAG> dags) {
        lock.writeLock().lock();
        try {
            ByteArrayOutputStream buff = new ByteArrayOutputStream();
            ByteArrayDataOutput out = ByteStreams.newDataOutput(buff);
            for (DAG d : dags.values()) {
                buff.reset();
                byte[] key = toKey(d.getId());
                byte[] value = encode(d, out);
                batch.put(key, value);
            }
            flush();
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void putInternal(byte[] key, DAG dag) {
        byte[] value = encode(dag);
        try {
            batch.put(key, value);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] toKey(TreeId treeId) {
        return treeId.bucketIndicesByDepth;
    }

    private DAG decode(TreeId id, byte[] value) {
        DAG dag;
        try {
            dag = DAGSerializer.deserialize(id, ByteStreams.newDataInput(value));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return dag;
    }

    private byte[] encode(DAG bucketDAG) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        return encode(bucketDAG, out);
    }

    private byte[] encode(DAG bucketDAG, ByteArrayDataOutput out) {
        try {
            DAGSerializer.serialize(bucketDAG, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }
}
