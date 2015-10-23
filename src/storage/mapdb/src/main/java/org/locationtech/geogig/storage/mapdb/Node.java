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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.ObjectId;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Node class used by {@link Graph}.
 * <p>
 * Every node contains an {@link ObjectId} representing the node in addition to a map of key/value
 * properties representing "extended" attributes.
 * </p>
 * 
 * @author Sebastian Schmidt, SWM Services GmbH
 */
// TODO Check if the properties get persisted inside Mapdb
class Node implements Serializable {

    private static final long serialVersionUID = 1L;

    private ObjectId id;

    private List<Edge> in = Lists.newArrayList();

    private List<Edge> out = Lists.newArrayList();

    private boolean root = false;

    private Map<String, String> props;

    /**
     * Creates a new node with the specified id.
     */
    Node(ObjectId id) {
        this.id = id;
    }

    public ObjectId id() {
        return id;
    }

    public List<Edge> in() {
        return in;
    }

    public List<Edge> out() {
        return out;
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

    @Nullable
    public Map<String, String> props() {
        return props;
    }

    /**
     * Returns all nodes reachable from this node through an outgoing relationship.
     */
    public Iterable<Node> to() {
        return Iterables.transform(out, new Function<Edge, Node>() {
            @Override
            public Node apply(Edge e) {
                return e.dst;
            }
        });
    }

    /**
     * Returns all nodes related to this node through an incoming relationship.
     */
    public Iterable<Node> from() {
        return Iterables.transform(in, new Function<Edge, Node>() {
            @Override
            public Node apply(Edge e) {
                return e.src;
            }
        });
    }

    /**
     * Associates a property with the node.
     */
    public void put(String key, String value) {
        if (props == null) {
            props = Maps.newHashMap();
        }
        props.put(key, value);
    }

    public void putAll(Map<String, String> props) {
        if (props == null) {
            return;
        }
        if (this.props == null) {
            this.props = Maps.newHashMap();
        }
        this.props.putAll(props);
    }

    /**
     * Retrieves a property of the node.
     */
    public Optional<String> get(String key) {
        return Optional.fromNullable(props != null ? props.get(key) : null);
    }

    @Override
    public String toString() {
        return id != null ? id.toString() : "null";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
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

    private void writeObject(ObjectOutputStream oos) throws IOException {
        try {
            oos.write(id.getRawValue());
            oos.writeBoolean(this.root);

            oos.writeObject(this.in);
            oos.writeObject(this.out);
            oos.writeObject(this.props);
        } catch (Exception e) {
            e.printStackTrace();
            throw Throwables.propagate(e);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException {
        byte[] buf = new byte[ObjectId.NUM_BYTES];
        in.readFully(buf);
        this.id = ObjectId.createNoClone(buf);
        this.root = in.readBoolean();
        try {
            this.in = (List<Edge>) in.readObject();
            this.out = (List<Edge>) in.readObject();
            this.props = (Map<String, String>) in.readObject();
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        } catch (Exception e) {
            e.printStackTrace();
            throw Throwables.propagate(e);
        }
    }

}
