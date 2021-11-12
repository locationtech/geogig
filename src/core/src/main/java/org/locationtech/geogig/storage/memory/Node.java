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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.locationtech.geogig.model.ObjectId;

import com.google.common.collect.Iterables;

import lombok.Getter;

/**
 * Node class used by {@link Graph}.
 * <p>
 * Every node contains an {@link ObjectId} representing the node in addition to a map of key/value
 * properties representing "extended" attributes.
 * </p>
 * 
 * @author Justin Deoliveira, Boundless
 */
class Node {

    final @Getter ObjectId id;

    final List<Edge> in = new ArrayList<>();

    final List<Edge> out = new ArrayList<>();

    boolean root = false;

    Map<String, String> props;

    /**
     * Creates a new node with the specified id.
     */
    Node(ObjectId id) {
        this.id = id;
    }

    /**
     * Determines if this node is marked as a root node.
     */
    public boolean isRoot() {
        return root;
    }

    /**
     * Marks/unmarks a node as a root node.
     */
    public void setRoot(boolean root) {
        this.root = root;
    }

    /**
     * Returns all nodes reachable from this node through an outgoing relationship.
     */
    public Iterable<Node> to() {
        return Iterables.transform(out, (e) -> e.dst);
    }

    /**
     * Returns all nodes related to this node through an incoming relationship.
     */
    public Iterable<Node> from() {
        return Iterables.transform(in, (e) -> e.src);
    }

    /**
     * Associates a property with the node.
     */
    public void put(String key, String value) {
        if (props == null) {
            props = new HashMap<>();
        }
        props.put(key, value);
    }

    /**
     * Retrieves a property of the node.
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(props != null ? props.get(key) : null);
    }

    public @Override String toString() {
        return id != null ? id.toString() : "null";
    }

    public @Override int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    public @Override boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Node other = (Node) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }
}
