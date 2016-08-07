/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.experimental.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;

class HeapDAGStorageProvider implements DAGStorageProvider {

    ConcurrentMap<NodeId, DAGNode> nodes;

    ConcurrentMap<TreeId, DAG> trees;

    private ObjectStore source;

    private TreeCache treeCache;

    HeapDAGStorageProvider(ObjectStore source) {
        this(source, new TreeCache(source));
    }

    HeapDAGStorageProvider(ObjectStore source, TreeCache treeCache) {
        this.source = source;
        this.treeCache = treeCache;
        this.nodes = new ConcurrentHashMap<>();
        this.trees = new ConcurrentHashMap<>();
    }

    public synchronized void dispose() {
        if (nodes != null) {
            nodes.clear();
            trees.clear();
            nodes = null;
            trees = null;
        }
    }

    @Override
    public RevTree getTree(ObjectId treeId) {
        return source.getTree(treeId);
    }

    private DAG createTree(TreeId treeId, ObjectId originalTreeId) {
        DAG dag = new DAG(originalTreeId);
        DAG existing = trees.putIfAbsent(treeId, dag);
        Preconditions.checkState(existing == null, "DAG %s[%s] already exists: %s", treeId,
                originalTreeId, existing);
        return dag;
    }

    @Override
    public DAG getOrCreateTree(TreeId treeId) {
        return getOrCreateTree(treeId, RevTree.EMPTY_TREE_ID);
    }

    @Override
    public DAG getOrCreateTree(TreeId treeId, ObjectId originalTreeId) {
        DAG dag = trees.get(treeId);
        if (dag == null) {
            dag = createTree(treeId, originalTreeId);
        }
        return dag;
    }

    @Override
    public @Nullable Node getNode(NodeId nodeId) {
        DAGNode dagNode = nodes.get(nodeId);
        Node node = null;
        if (dagNode != null) {
            node = dagNode.resolve(this.treeCache);
        }
        return node;
    }

    @Override
    public void saveNode(NodeId nodeId, Node node) {
        nodes.put(nodeId, DAGNode.of(node));
    }

    @Override
    public void saveNodes(Map<NodeId, DAGNode> nodeMappings) {
        nodes.putAll(nodeMappings);
    }

    @Override
    public long nodeCount() {
        return nodes.size();
    }

    @Override
    public void save(TreeId bucketId, DAG bucketDAG) {
        trees.put(bucketId, bucketDAG);
    }

    @Override
    public TreeCache getTreeCache() {
        return treeCache;
    }
}
