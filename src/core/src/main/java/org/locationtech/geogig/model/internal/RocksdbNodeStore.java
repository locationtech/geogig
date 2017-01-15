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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

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
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

class RocksdbNodeStore {

    private RocksDB db;

    private WriteOptions writeOptions;

    private ReadOptions readOptions;

    private ColumnFamilyHandle column;

    public RocksdbNodeStore(RocksDB db) {
        this.db = db;
        ColumnFamilyDescriptor columnDescriptor = new ColumnFamilyDescriptor(
                "nodes".getBytes(Charsets.UTF_8));
        try {
            column = db.createColumnFamily(columnDescriptor);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }

        this.writeOptions = new WriteOptions();
        writeOptions.setDisableWAL(true);
        writeOptions.setSync(false);

        readOptions = new ReadOptions();
        readOptions.setFillCache(true).setVerifyChecksums(false);
    }

    public void close() {
        batch.close();
        readOptions.close();
        writeOptions.close();
        column.close();
        db = null;
    }

    public DAGNode get(NodeId nodeId) {
        flush();
        byte[] value;
        byte[] key = toKey(nodeId);
        try {
            value = db.get(column, readOptions, key);
            if (value == null) {
                throw new NoSuchElementException("Node " + nodeId + " not found");
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return decode(value);
    }

    public Map<NodeId, DAGNode> getAll(Set<NodeId> nodeIds) {
        if (nodeIds.isEmpty()) {
            return ImmutableMap.of();
        }
        flush();
        Map<NodeId, DAGNode> res = new HashMap<>();
        byte[] valueBuff = new byte[512];
        nodeIds.forEach((id) -> {
            try {
                byte[] val = valueBuff;
                byte[] key = toKey(id);
                int ret = db.get(column, readOptions, key, val);
                Preconditions.checkState(ret != RocksDB.NOT_FOUND);
                if (ret > valueBuff.length) {
                    val = db.get(column, key);
                }
                DAGNode node = decode(val);
                res.put(id, node);
            } catch (IllegalArgumentException | RocksDBException e) {
                throw Throwables.propagate(e);
            }
        });
        return res;
    }

    private WriteBatch batch = new WriteBatch();

    private int batchSize;

    public void put(NodeId nodeId, DAGNode node) {
        synchronized (batch) {
            batch.put(column, toKey(nodeId), encode(node));
            batchSize++;
        }

        flush(1_000);
    }

    public void putAll(Map<NodeId, DAGNode> nodeMappings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        nodeMappings.forEach((nodeId, dagNode) -> {
            out.reset();
            encode(dagNode, out);
            byte[] value = out.toByteArray();
            byte[] key = toKey(nodeId);
            synchronized (batch) {
                batch.put(column, key, value);
                batchSize++;
            }
            flush(1_000);
        });
    }

    private void flush() {
        flush(1);
    }

    private void flush(int limit) {
        if (batchSize >= limit) {
            synchronized (batch) {
                try {
                    db.write(writeOptions, batch);
                } catch (Exception ex) {
                    throw Throwables.propagate(ex);
                }
                batchSize = 0;
                batch.clear();
            }
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
            DAGNode.encode(node, out);
            out.flush();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return outstream.toByteArray();
    }

    private DAGNode decode(byte[] nodeData) {
        DAGNode node;
        try {
            node = DAGNode.decode(ByteStreams.newDataInput(nodeData));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return node;
    }
}
