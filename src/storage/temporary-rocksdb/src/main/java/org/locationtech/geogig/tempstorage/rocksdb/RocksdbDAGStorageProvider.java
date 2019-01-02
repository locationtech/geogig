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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.internal.DAG;
import org.locationtech.geogig.model.internal.DAGNode;
import org.locationtech.geogig.model.internal.DAGStorageProvider;
import org.locationtech.geogig.model.internal.NodeId;
import org.locationtech.geogig.model.internal.TreeCache;
import org.locationtech.geogig.model.internal.TreeId;
import org.locationtech.geogig.storage.ObjectStore;
import org.rocksdb.RocksDB;

import com.google.common.base.Throwables;

class RocksdbDAGStorageProvider implements DAGStorageProvider {

    private final ObjectStore objectStore;

    private final TreeCache treeCache;

    private Path directory;

    private RocksdbHandle dagDb, nodeDb;

    private final RocksdbNodeStore nodeStore;

    private final RocksdbDAGStore dagStore;

    public RocksdbDAGStorageProvider(ObjectStore source) {
        this(source, new TreeCache(source));
    }

    public RocksdbDAGStorageProvider(ObjectStore source, TreeCache treeCache) {
        this.objectStore = source;
        this.treeCache = treeCache;
        this.dagStore = new RocksdbDAGStore(this::getOrCreateDagDb);
        this.nodeStore = new RocksdbNodeStore(this::getOrCreateNodeDb);
    }

    private RocksDB getOrCreateNodeDb() {
        if (nodeDb == null) {
            nodeDb = createDb("node");
        }
        return nodeDb.db;
    }

    private RocksDB getOrCreateDagDb() {
        if (dagDb == null) {
            dagDb = createDb("dag");
        }
        return nodeDb.db;
    }

    private synchronized RocksdbHandle createDb(String name) {
        try {
            if (directory == null) {
                directory = Files.createTempDirectory("geogig-tmp-tree-store");
            }
            Path dbdir = directory.resolve(name);
            RocksdbHandle db = RocksdbHandle.create(dbdir);
            return db;
        } catch (Exception e) {
            RocksdbHandle.delete(directory.toFile());
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dispose() {
        if (nodeStore != null) {
            nodeStore.close();
        }
        if (dagStore != null) {
            dagStore.close();
        }
        close(dagDb);
        close(nodeDb);
        if (directory != null) {
            RocksdbHandle.delete(directory.toFile());
        }
    }

    private void close(RocksdbHandle handle) {
        if (handle != null) {
            handle.dispose();
        }
    }

    @Override
    public TreeCache getTreeCache() {
        return treeCache;
    }

    @Override
    public List<DAG> getTrees(Set<TreeId> ids) throws NoSuchElementException {
        return dagStore.getTrees(ids);
    }

    @Override
    public DAG getOrCreateTree(TreeId treeId, ObjectId originalTreeId) {
        return dagStore.getOrCreateTree(treeId, originalTreeId);
    }

    @Override
    public void save(Map<TreeId, DAG> dags) {
        dagStore.save(dags);
    }

    @Override
    public Map<NodeId, Node> getNodes(final Set<NodeId> nodeIds) {
        Map<NodeId, DAGNode> dagNodes = nodeStore.getAll(nodeIds);
        Map<NodeId, Node> res = new HashMap<>();
        dagNodes.forEach((id, node) -> res.put(id, node.resolve(treeCache)));
        return res;
    }

    @Override
    public void saveNode(NodeId nodeId, Node node) {
        nodeStore.put(nodeId, DAGNode.of(node));
    }

    @Override
    public void saveNodes(Map<NodeId, DAGNode> nodeMappings) {
        nodeStore.putAll(nodeMappings);
    }

    @Override
    @Nullable
    public RevTree getTree(ObjectId originalId) {
        return objectStore.getTree(originalId);
    }
}
