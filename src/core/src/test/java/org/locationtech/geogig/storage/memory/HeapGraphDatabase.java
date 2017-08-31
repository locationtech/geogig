/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

import java.net.URI;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.GraphDatabase;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;

/**
 * Provides an default in memory implementation of a GeoGig Graph Database.
 */
public class HeapGraphDatabase implements GraphDatabase {

    static final Function<Node, ObjectId> NODE_TO_ID = (n) -> n.id;

    static final Map<URI, Ref> graphs = Maps.newConcurrentMap();

    final @Nullable Platform platform;

    Graph graph;

    public HeapGraphDatabase(@Nullable Platform platform) {
        this.platform = platform;
    }

    @Override
    public void open() {
        if (isOpen()) {
            return;
        }

        Optional<URI> url = platform == null ? Optional.absent()
                : new ResolveGeogigURI(platform, null).call();
        if (url.isPresent()) {
            synchronized (graphs) {
                URI key = url.get();
                if (!graphs.containsKey(key)) {
                    graphs.put(key, new Ref(new Graph()));
                }
                graph = graphs.get(key).acquire();
            }
        } else {
            graph = new Graph();
        }

    }

    @Override
    public boolean isOpen() {
        return graph != null;
    }

    @Override
    public void close() {
        if (!isOpen()) {
            return;
        }
        graph = null;
        Optional<URI> url = platform == null ? Optional.absent()
                : new ResolveGeogigURI(platform, null).call();
        if (url.isPresent()) {
            synchronized (graphs) {
                URI key = url.get();
                Ref ref = graphs.get(key);
                if (ref != null && ref.release() <= -1) {
                    ref.destroy();
                    graphs.remove(key);
                }
            }
        }
    }

    @Override
    public boolean exists(ObjectId commitId) {
        return graph.get(commitId).isPresent();
    }

    @Override
    public ImmutableList<ObjectId> getParents(ObjectId commitId) throws IllegalArgumentException {
        return graph.get(commitId).transform(new Function<Node, ImmutableList<ObjectId>>() {
            @Override
            public ImmutableList<ObjectId> apply(Node n) {
                // transform outgoing nodes to id
                // filter for null to skip fake root node
                return new ImmutableList.Builder<ObjectId>()
                        .addAll(filter(transform(n.to(), NODE_TO_ID), Predicates.notNull()))
                        .build();
            }
        }).or(ImmutableList.<ObjectId> of());
    }

    @Override
    public ImmutableList<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException {
        return graph.get(commitId).transform(new Function<Node, ImmutableList<ObjectId>>() {
            @Override
            public ImmutableList<ObjectId> apply(Node n) {
                return new ImmutableList.Builder<ObjectId>().addAll(transform(n.from(), NODE_TO_ID))
                        .build();
            }
        }).or(ImmutableList.<ObjectId> of());
    }

    @Override
    public boolean put(ObjectId commitId, ImmutableList<ObjectId> parentIds) {
        Node n = graph.getOrAdd(commitId);
        synchronized (n) {
            if (parentIds.isEmpty()) {
                // the root node, only update on first addition
                if (!n.isRoot()) {
                    n.setRoot(true);
                    return true;
                }
            }

            // has the node been attached to graph?
            if (Iterables.isEmpty(n.to())) {
                // nope, attach it
                for (ObjectId parent : parentIds) {
                    Node p = graph.getOrAdd(parent);
                    graph.newEdge(n, p);
                }

                // only mark as updated if it is actually attached
                boolean added = !Iterables.isEmpty(n.to());
                return added;
            }
        }
        return false;
    }

    @Override
    public void map(ObjectId mapped, ObjectId original) {
        graph.map(mapped, original);
    }

    @Override
    public ObjectId getMapping(ObjectId commitId) {
        return Optional.fromNullable(graph.getMapping(commitId)).or(ObjectId.NULL);
    }

    @Override
    public int getDepth(ObjectId commitId) {
        Preconditions.checkNotNull(commitId);
        Optional<Node> nodeOpt = graph.get(commitId);
        Preconditions.checkArgument(nodeOpt.isPresent(), "No graph entry for commit %s on %s",
                commitId, this.toString());
        Node node = nodeOpt.get();
        PathToRootWalker walker = new PathToRootWalker(node);
        int depth = 0;
        O: while (walker.hasNext()) {
            for (Node n : walker.next()) {
                if (Iterables.size(n.to()) == 0) {
                    break O;
                }
            }
            depth++;
        }
        return depth;
    }

    @Override
    public void setProperty(ObjectId commitId, String propertyName, String propertyValue) {
        graph.get(commitId).get().put(propertyName, propertyValue);
        ;
    }

    @Override
    public void truncate() {
        graph.clear();
    }

    static class Ref {

        int count;

        Graph graph;

        Ref(Graph g) {
            graph = g;
            count = 0;
        }

        Graph acquire() {
            count++;
            return graph;
        }

        int release() {
            return --count;
        }

        void destroy() {
            graph = null;
        }
    }

    protected class HeapGraphNode extends GraphNode {

        Node node;

        public HeapGraphNode(Node node) {
            this.node = node;
        }

        @Override
        public ObjectId getIdentifier() {
            return node.id;
        }

        @Override
        public Iterator<GraphEdge> getEdges(final Direction direction) {
            Iterator<Edge> nodeEdges;
            switch (direction) {
            case OUT:
                nodeEdges = node.out.iterator();
                break;
            case IN:
                nodeEdges = node.in.iterator();
                break;
            default:
                nodeEdges = Iterators.concat(node.in.iterator(), node.out.iterator());
            }
            List<GraphEdge> edges = new LinkedList<GraphEdge>();
            while (nodeEdges.hasNext()) {
                Edge nodeEdge = nodeEdges.next();
                Node src = nodeEdge.src;
                Node dst = nodeEdge.dst;
                edges.add(new GraphEdge(new HeapGraphNode(src), new HeapGraphNode(dst)));
            }
            return edges.iterator();
        }

        @Override
        public boolean isSparse() {
            return node.props != null && node.props.containsKey(SPARSE_FLAG)
                    && Boolean.valueOf(node.props.get(SPARSE_FLAG));
        }
    }

    @Override
    public GraphNode getNode(ObjectId id) {
        return new HeapGraphNode(graph.get(id).get());
    }
}
