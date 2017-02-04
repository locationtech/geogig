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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

class RocksdbDAGStorageProvider implements DAGStorageProvider {

    private final ObjectStore objectStore;

    private final TreeCache treeCache;

    private final RocksdbHandle dagDb;

    private final RocksdbNodeStore nodeStore;

    private final RocksdbDAGStore dagStore;

    RocksdbDAGStorageProvider(ObjectStore source) {
        this(source, new TreeCache(source));
    }

    RocksdbDAGStorageProvider(ObjectStore source, TreeCache treeCache) {
        this.objectStore = source;
        this.treeCache = treeCache;
        Path dagDbDir = null;
        try {
            dagDbDir = Files.createTempDirectory("geogig-dag-store");
            dagDb = RocksdbHandle.create(dagDbDir);

            this.dagStore = new RocksdbDAGStore(dagDb.db);
            this.nodeStore = new RocksdbNodeStore(dagDb.db);
        } catch (Exception e) {
            RocksdbHandle.delete(dagDbDir.toFile());
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void dispose() {
        try {
            dagStore.close();
        } finally {
            try {
                nodeStore.close();
            } finally {
                dagDb.dispose();
            }
        }
    }

    @Override
    public TreeCache getTreeCache() {
        return treeCache;
    }

    @Override
    public List<DAG> getTrees(Set<TreeId> ids) {
        return dagStore.getTrees(ids);
    }

    @Override
    public DAG getOrCreateTree(TreeId treeId, ObjectId originalTreeId) {
        return dagStore.getOrCreate(treeId, originalTreeId);
    }

    @Override
    public void save(Map<TreeId, DAG> dags) {
        dagStore.putAll(dags);
    }

    @Override
    public Map<NodeId, Node> getNodes(final Set<NodeId> nodeIds) {
        Map<NodeId, DAGNode> dagNodes = nodeStore.getAll(nodeIds);
        return Maps.transformValues(dagNodes, (dn) -> dn.resolve(treeCache));
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
