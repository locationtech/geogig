/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.mapdb;

import java.util.Map;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.storage.memory.HeapGraphDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * directed graph implementation backed by a Map.
 * <p>
 * This class isn't used outside of {@link HeapGraphDatabase}.
 * </p>
 * 
 * @author Sebastian Schmidt, SWM Services GmbH
 *
 */
class Graph {

    final Map<byte[], Node> nodes;

    final Map<byte[], byte[]> mappings;

    /**
     * Creates an empty graph.
     */
    Graph(Map<byte[], Node> nodes, Map<byte[], byte[]> mappings) {
        this.nodes = nodes;
        this.mappings = mappings;
    }

    /**
     * Gets a node in the graph by its object id, creating a new node if one does already exist.
     */
    public Node getOrAdd(ObjectId id) {
        Optional<Node> n = get(id);
        return n.isPresent() ? n.get() : newNode(id);
    }

    /**
     * Looks up a node in the graph by its identifier.
     */
    public Optional<Node> get(ObjectId id) {
        return Optional.fromNullable(nodes.get(id.getRawValue()));
    }

    /**
     * Creates a new node in the graph.
     * 
     * @param id The id of the new node.
     */
    public Node newNode(ObjectId id) {
        Preconditions.checkNotNull(id);
        Preconditions.checkState(nodes.get(id.getRawValue()) == null);
        Node n = new Node(id);
        nodes.put(id.getRawValue(), n);
        return n;
    }

    /**
     * Relates two nodes in the graph.
     * 
     * @param src The source (origin) node.
     * @param dst The destination (end) node.
     */
    public Edge newEdge(Node src, Node dst) {
        Edge e = new Edge(src, dst);
        src.out().add(e);
        dst.in().add(e);
        return e;
    }

    /**
     * Creates an mapping/alias.
     */
    public void map(ObjectId mapped, ObjectId original) {
        mappings.put(mapped.getRawValue(), original.getRawValue());
    }

    /**
     * Returns a mapping, or <code>null</code> if one does not exist.
     *
     */
    public ObjectId getMapping(ObjectId commitId) {
        byte[] mapping = mappings.get(commitId.getRawValue());
        return mapping == null ? null : ObjectId.createNoClone(mapping);
    }

    /**
     * Clears the contents of the graph.
     */
    public void clear() {
        nodes.clear();
        mappings.clear();
    }

}
