/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;

class HeapDAGStorageProvider implements DAGStorageProvider {

    private Map<String, Object> nodes;

    private Map<TreeId, DAG> trees;

    private ObjectStore source;

    public HeapDAGStorageProvider(ObjectStore source) {
        this.source = source;
        this.nodes = new ConcurrentHashMap<>();
        this.trees = new ConcurrentHashMap<>();
    }

    public void close() {
        dispose();
    }

    public synchronized void dispose() {
        if (nodes != null) {
            nodes.clear();
            trees.clear();
            nodes = null;
            trees = null;
        }
    }

    public @Override RevTree getTree(ObjectId treeId) {
        return source.getTree(treeId);
    }

    public DAG getTree(TreeId id) throws NoSuchElementException {
        DAG dag = trees.get(id);
        if (dag == null) {
            throw new NoSuchElementException(id.toString());
        }
        return dag;
    }

    public @Override List<DAG> getTrees(List<TreeId> ids) throws NoSuchElementException {
        return ids.stream().map(this::getTree).collect(Collectors.toList());
    }

    public @Override DAG getOrCreateTree(final TreeId treeId, final ObjectId originalTreeId) {
        DAG dag = trees.computeIfAbsent(treeId, id -> new DAG(treeId, originalTreeId));
        return dag;// .clone();
    }

    public @Override Node getNode(NodeId nodeId) {
        Object n = nodes.get(nodeId.name());
        Preconditions.checkState(n != null, "node not fount: " + nodeId.name());
        Node dagNode = n instanceof DAGNode ? ((DAGNode) n).resolve(source) : (Node) n;
        return dagNode;
    }

    public @Override Map<NodeId, Node> getNodes(final Set<NodeId> nodeIds) {
        Map<NodeId, Node> res = new HashMap<>();
        nodeIds.forEach((nid) -> res.put(nid, getNode(nid)));
        return res;
    }

    public @Override void saveNode(NodeId nodeId, DAGNode node) {
        nodes.put(nodeId.name(), node);
    }

    public @Override void saveNode(NodeId nodeId, Node node) {
        nodes.put(nodeId.name(), DAGNode.of(node));
    }

    public @Override void saveNodes(Map<NodeId, DAGNode> nodeMappings) {
        nodeMappings.forEach(this::saveNode);
    }

    public @Override void save(DAG dag) {
        // nothing to do, DAG was created and held by this class
        trees.put(dag.getId(), dag);
    }

    public @Override void save(List<DAG> dags) {
        // nothing to do, DAGs were created and held by this class
        dags.forEach(this::save);
    }

    public long nodeCount() {
        return nodes.size();
    }

}
