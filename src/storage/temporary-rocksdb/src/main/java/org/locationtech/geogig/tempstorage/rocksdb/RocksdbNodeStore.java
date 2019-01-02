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
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import org.locationtech.geogig.model.internal.DAGNode;
import org.locationtech.geogig.model.internal.NodeId;
import org.rocksdb.DBOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;
import org.rocksdb.WriteBatchWithIndex;
import org.rocksdb.WriteOptions;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

class RocksdbNodeStore {

    private Supplier<RocksDB> _dbSupplier;

    private RocksDB _db;

    private final int WRITE_THRESHOLD = 10_000;

    private DBOptions batchOptions;

    private WriteBatchWithIndex batch = new WriteBatchWithIndex();

    private WriteOptions writeOptions;

    private ReadOptions readOptions;

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    public RocksdbNodeStore(Supplier<RocksDB> db) {
        this._dbSupplier = db;
        this.batchOptions = new DBOptions();
    }

    public void close() {
        close(batch);
        close(batchOptions);
        close(readOptions);
        close(writeOptions);
        _db = null;
        _dbSupplier = null;
    }

    private void close(RocksObject obj) {
        if (obj != null)
            try {
                obj.close();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
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

    public Map<NodeId, DAGNode> getAll(Set<NodeId> nodeIds) {
        if (nodeIds.isEmpty()) {
            return ImmutableMap.of();
        }
        Map<NodeId, DAGNode> res = new HashMap<>();
        lock.readLock().lock();
        try {
            for (NodeId id : nodeIds) {
                byte[] key = toKey(id);
                byte[] val;
                if (_db == null) {
                    val = batch.getFromBatch(batchOptions, key);
                } else {
                    val = batch.getFromBatchAndDB(db(), readOptions, key);
                }
                Preconditions.checkState(val != null);
                DAGNode node = decode(val);
                res.put(id, node);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        } finally {
            lock.readLock().unlock();
        }
        return res;
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

    public void put(NodeId nodeId, DAGNode node) {
        byte[] key = toKey(nodeId);
        byte[] value = encode(node);
        lock.writeLock().lock();
        try {
            batch.put(key, value);
            flush();
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void putAll(Map<NodeId, DAGNode> nodeMappings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        lock.writeLock().lock();
        try {
            nodeMappings.forEach((nodeId, dagNode) -> {
                out.reset();
                encode(dagNode, out);
                byte[] value = out.toByteArray();
                byte[] key = toKey(nodeId);
                try {
                    batch.put(key, value);
                } catch (RocksDBException e) {
                    throw new RuntimeException(e);
                }
            });
            flush();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private byte[] toKey(NodeId nodeId) {
        byte[] key = nodeId.name().getBytes(Charsets.UTF_8);
        return key;
    }

    private byte[] encode(DAGNode node) {
        return encode(node, new ByteArrayOutputStream());
    }

    private byte[] encode(DAGNode node, ByteArrayOutputStream outstream) {
        DataOutputStream out = new DataOutputStream(outstream);
        try {
            DAGSerializer.encode(node, out);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outstream.toByteArray();
    }

    private DAGNode decode(byte[] nodeData) {
        DAGNode node;
        try {
            node = DAGSerializer.decode(ByteStreams.newDataInput(nodeData));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return node;
    }
}
