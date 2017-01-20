/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 */
package org.locationtech.geogig.model.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * For test cases
 */
public class HashNodeStore implements NodeStore {

    Map<NodeId, DAGNode> saved = new HashMap<>();
    boolean closed = false;

    @Override
    public void close() {
        closed = true;
        saved.clear();
    }

    @Override
    public DAGNode get(NodeId nodeId) {
        return saved.get(nodeId);
    }

    @Override
    public Map<NodeId, DAGNode> getAll(Set<NodeId> nodeIds) {
        Map<NodeId, DAGNode> result = new HashMap<>();
        for (NodeId id : nodeIds) {
            result.put(id, get(id));
        }
        return result;
    }

    @Override
    public void put(NodeId nodeId, DAGNode node) {
        saved.put(nodeId, node);
    }

    @Override
    public void putAll(Map<NodeId, DAGNode> nodeMappings) {
        for (Map.Entry<NodeId, DAGNode> item : nodeMappings.entrySet()) {
            put(item.getKey(), item.getValue());
        }

    }
}
