/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import java.util.concurrent.ConcurrentMap;

import org.locationtech.geogig.model.ObjectId;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * In memory directed graph implementation.
 * <p>
 * This class isn't used outside of {@link HeapGraphDatabase}.
 * </p>
 * 
 * @author Justin Deoliveira, Boundless
 *
 */
class Graph {

    final ConcurrentMap<ObjectId, Node> nodes;

    final ConcurrentMap<ObjectId, ObjectId> mappings;

    /**
     * Creates an empty graph.
     */
    Graph() {
        nodes = Maps.newConcurrentMap();
        mappings = Maps.newConcurrentMap();
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
        return Optional.fromNullable(nodes.get(id));
    }

    /**
     * Creates a new node in the graph.
     * 
     * @param id The id of the new node.
     */
    public Node newNode(ObjectId id) {
        Preconditions.checkNotNull(id);
        Preconditions.checkState(nodes.get(id) == null);
        Node n = new Node(id);
        Node prev = nodes.putIfAbsent(id, n);
        return prev == null ? n : prev;
    }

    /**
     * Relates two nodes in the graph.
     * 
     * @param src The source (origin) node.
     * @param dst The destination (end) node.
     */
    public Edge newEdge(Node src, Node dst) {
        Edge e = new Edge(src, dst);
        src.out.add(e);
        dst.in.add(e);
        return e;
    }

    /**
     * Creates an mapping/alias.
     */
    public void map(ObjectId mapped, ObjectId original) {
        mappings.put(mapped, original);
    }

    /**
     * Returns a mapping, or <code>null</code> if one does not exist.
     *
     */
    public ObjectId getMapping(ObjectId commitId) {
        return mappings.get(commitId);
    }

    /**
     * Clears the contents of the graph.
     */
    public void clear() {
        nodes.clear();
        mappings.clear();
    }

}
