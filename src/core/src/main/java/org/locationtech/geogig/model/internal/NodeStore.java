/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 */

package org.locationtech.geogig.model.internal;

import java.util.Map;
import java.util.Set;

/**
 * Interface for NodeStore implementations.
 */
public interface NodeStore {

    void close();

    DAGNode get(NodeId nodeId);

    Map<NodeId, DAGNode> getAll(Set<NodeId> nodeIds);

    void put(NodeId nodeId, DAGNode node);

    void putAll(Map<NodeId, DAGNode> nodeMappings);
}
