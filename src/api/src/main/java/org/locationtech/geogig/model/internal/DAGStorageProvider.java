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

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;

public interface DAGStorageProvider {

    public TreeCache getTreeCache();

    public List<DAG> getTrees(Set<TreeId> ids) throws NoSuchElementException;

    public DAG getOrCreateTree(TreeId treeId, ObjectId originalTreeId);

    public void save(Map<TreeId, DAG> dags);

    public Map<NodeId, Node> getNodes(Set<NodeId> nodeIds);

    public void saveNode(NodeId nodeId, Node node);

    public void saveNodes(Map<NodeId, DAGNode> nodeMappings);

    public void dispose();

    @Nullable
    public RevTree getTree(ObjectId originalId);

}
