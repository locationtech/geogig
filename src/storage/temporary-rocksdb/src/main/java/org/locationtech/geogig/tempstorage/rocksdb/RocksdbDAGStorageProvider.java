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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.internal.DAG;
import org.locationtech.geogig.model.internal.DAGNode;
import org.locationtech.geogig.model.internal.DAGStorageProvider;
import org.locationtech.geogig.model.internal.NodeId;
import org.locationtech.geogig.model.internal.TreeId;
import org.locationtech.geogig.storage.ObjectStore;
import org.rocksdb.RocksDB;

import com.google.common.base.Throwables;

class RocksdbDAGStorageProvider implements DAGStorageProvider {

    private final ObjectStore objectStore;

    private Path directory;

    private RocksdbHandle dagDb, nodeDb;

    private final RocksdbNodeStore nodeStore;

    private final RocksdbDAGStore dagStore;

    public RocksdbDAGStorageProvider(ObjectStore source) {
        this.objectStore = source;
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

    public @Override void dispose() {
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

    public @Override DAG getTree(TreeId id) throws NoSuchElementException {
        return dagStore.getTree(id);
    }

    public @Override List<DAG> getTrees(List<TreeId> ids) throws NoSuchElementException {
        return dagStore.getTrees(ids);
    }

    public @Override DAG getOrCreateTree(TreeId treeId, ObjectId originalTreeId) {
        return dagStore.getOrCreateTree(treeId, originalTreeId);
    }

    public @Override void save(DAG dag) {
        save(Collections.singletonList(dag));
    }

    public @Override void save(List<DAG> dags) {
        dagStore.save(dags);
    }

    public @Override Node getNode(NodeId nodeId) {
        DAGNode dagNode = nodeStore.get(nodeId);
        Preconditions.checkState(dagNode != null);
        return dagNode.resolve(objectStore);
    }

    public @Override Map<NodeId, Node> getNodes(final Set<NodeId> nodeIds) {
        Map<NodeId, DAGNode> dagNodes = nodeStore.getAll(nodeIds);
        Map<NodeId, Node> res = new HashMap<>();
        dagNodes.forEach((id, node) -> res.put(id, node.resolve(objectStore)));
        return res;
    }

    public @Override void saveNode(NodeId nodeId, DAGNode node) {
        nodeStore.put(nodeId, node);
    }

    public @Override void saveNode(NodeId nodeId, Node node) {
        nodeStore.put(nodeId, DAGNode.of(node));
    }

    public @Override void saveNodes(Map<NodeId, DAGNode> nodeMappings) {
        nodeStore.putAll(nodeMappings);
    }

    @Nullable
    public @Override RevTree getTree(ObjectId originalId) {
        return objectStore.getTree(originalId);
    }
}
