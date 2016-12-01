package org.locationtech.geogig.model.experimental.internal;

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

    private boolean closed;

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
        System.err.printf("Biggest tree: %,d bytes\n", biggest);
        this.closed = true;
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

    int biggest;

    private Map<TreeId, DAG> getInternal(final Set<TreeId> ids) {
        Map<TreeId, DAG> res = new HashMap<>();
        byte[] valueBuff = new byte[16 * 1024];
        ids.forEach((id) -> {
            byte[] key = toKey(id);
            byte[] val = valueBuff;
            try {
                int size = db.get(column, readOptions, key, val);
                Preconditions.checkState(RocksDB.NOT_FOUND != size);
                biggest = Math.max(biggest, size);
                if (size > valueBuff.length) {
                    val = db.get(column, readOptions, key);
                }
                DAG dag = decode(val);
                res.put(id, dag);
            } catch (RocksDBException e) {
                throw Throwables.propagate(e);
            }
        });
        // List<byte[]> keys = Lists.newArrayList(Iterables.transform(ids, (id) -> toKey(id)));
        // List<ColumnFamilyHandle> cols = Collections.nCopies(keys.size(), column);
        // Map<byte[], byte[]> raw;
        // try {
        // raw = db.multiGet(cols, keys);
        // } catch (IllegalArgumentException | RocksDBException e) {
        // throw Throwables.propagate(e);
        // }
        // raw.forEach((k, v) -> res.put(fromKey(k), decode(v)));

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

    private TreeId fromKey(byte[] key) {
        return new TreeId(key);
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
