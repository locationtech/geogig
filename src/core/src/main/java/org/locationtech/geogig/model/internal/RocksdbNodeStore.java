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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

class RocksdbNodeStore {

    private RocksDB db;

    private WriteOptions writeOptions;

    private ReadOptions readOptions;

    private ColumnFamilyHandle column;

    private BloomFilter bloomFilter;

    private ColumnFamilyOptions colFamilyOptions;

    public RocksdbNodeStore(RocksDB db) {
        this.db = db;
        try {
            // enable bloom filter to speed up RocksDB.get() calls
            BlockBasedTableConfig tableFormatConfig = new BlockBasedTableConfig();
            bloomFilter = new BloomFilter();
            // tableFormatConfig.setFilter(bloomFilter);
            // tableFormatConfig.setBlockSize(16*1024);
            // tableFormatConfig.setBlockCacheSize(4 * 1024 * 1024);

            colFamilyOptions = new ColumnFamilyOptions();
            colFamilyOptions.setTableFormatConfig(tableFormatConfig);

            byte[] tableNameKey = "nodes".getBytes(Charsets.UTF_8);
            ColumnFamilyDescriptor columnDescriptor = new ColumnFamilyDescriptor(tableNameKey,
                    colFamilyOptions);
            column = db.createColumnFamily(columnDescriptor);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }

        this.writeOptions = new WriteOptions();
        writeOptions.setDisableWAL(true);
        writeOptions.setSync(false);

        readOptions = new ReadOptions();
        readOptions.setFillCache(true).setVerifyChecksums(false);
    }

    public void close() {
        readOptions.close();
        writeOptions.close();
        column.close();
        colFamilyOptions.close();
        bloomFilter.close();
        db = null;
    }

    public DAGNode get(NodeId nodeId) {
        byte[] value;
        byte[] key = toKey(nodeId);
        try {
            value = db.get(column, readOptions, key);
            if (value == null) {
                throw new NoSuchElementException("Node " + nodeId + " not found");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return decode(value);
    }

    public Map<NodeId, DAGNode> getAll(Set<NodeId> nodeIds) {
        if (nodeIds.isEmpty()) {
            return ImmutableMap.of();
        }

        Map<byte[], byte[]> map;
        try {
            List<byte[]> keys = nodeIds.stream().map(id -> toKey(id)).collect(Collectors.toList());
            map = db.multiGet(readOptions, Collections.nCopies(nodeIds.size(), column), keys);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }

        Map<String, NodeId> ids = Maps.uniqueIndex(nodeIds, i -> i.name());
        Map<NodeId, DAGNode> res = new HashMap<>();
        map.forEach((key, val) -> {
            NodeId id = ids.get(fromKey(key));
            DAGNode dag = decode(val);
            res.put(id, dag);
        });
        return res;
    }

    public Map<NodeId, DAGNode> getAllOld(Set<NodeId> nodeIds) {
        if (nodeIds.isEmpty()) {
            return ImmutableMap.of();
        }
        Map<NodeId, DAGNode> res = new HashMap<>();
        byte[] valueBuff = new byte[512];
        try {
            for (NodeId id : nodeIds) {
                byte[] val = valueBuff;
                byte[] key = toKey(id);
                int ret = db.get(column, readOptions, key, val);
                Preconditions.checkState(ret != RocksDB.NOT_FOUND);
                if (ret > valueBuff.length) {
                    val = db.get(column, key);
                }
                DAGNode node = decode(val);
                res.put(id, node);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return res;
    }

    public void put(NodeId nodeId, DAGNode node) {
        byte[] key = toKey(nodeId);
        byte[] value = encode(node);
        try {
            db.put(column, writeOptions, key, value);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public void putAll(Map<NodeId, DAGNode> nodeMappings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        nodeMappings.forEach((nodeId, dagNode) -> {
            out.reset();
            encode(dagNode, out);
            byte[] value = out.toByteArray();
            byte[] key = toKey(nodeId);
            try {
                db.put(column, writeOptions, key, value);
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private byte[] toKey(NodeId nodeId) {
        byte[] key = nodeId.name().getBytes(Charsets.UTF_8);
        return key;
    }

    private String fromKey(byte[] key) {
        return new String(key, Charsets.UTF_8);
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
            throw new RuntimeException(e);
        }
        return outstream.toByteArray();
    }

    private DAGNode decode(byte[] nodeData) {
        DAGNode node;
        try {
            node = DAGNode.decode(ByteStreams.newDataInput(nodeData));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return node;
    }
}
