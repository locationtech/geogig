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

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

import java.io.File;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

/**
 * Rapid prototyped Mapdb Graph Database, derived from In Memory Heap Database.
 */
public class MapdbGraphDatabase implements GraphDatabase {

    static final Function<Node, ObjectId> NODE_TO_ID = new Function<Node, ObjectId>() {
        @Override
        public ObjectId apply(Node n) {
            return n.id;
        }
    };

    static final Map<URI, Ref> graphs = Maps.newConcurrentMap();

    protected final Platform platform;
    
    protected final ConfigDatabase config;

    protected ConcurrentNavigableMap<ObjectId,Node> nodes = null;
    
    protected ConcurrentNavigableMap <ObjectId,ObjectId> mappings = null;
    
    protected DB db = null;
    
    Graph graph;

    @Inject
    public MapdbGraphDatabase(ConfigDatabase config, Platform platform) {
        this.config = config;
    	this.platform = platform;
    }

    @Override
    public void open() {
        if (isOpen()) {
            return;
        }

		Optional<URI> repoPath = new ResolveGeogigURI(platform, null).call();
        Preconditions.checkState(repoPath.isPresent(), "Can't find geogig repository home");
        URI uri = repoPath.get();
        Preconditions.checkState("file".equals(uri.getScheme()),
                "Repository URL is not file system based: %s", uri);
        File repoLocation = new File(uri);
        File storeDirectory = new File(repoLocation, "graph");
        
        if (!storeDirectory.exists() && !storeDirectory.mkdirs()) {
            throw new IllegalStateException("Unable to create Environment directory: '"
                    + storeDirectory.getAbsolutePath() + "'");
        }
        
		db = DBMaker.fileDB(new File(storeDirectory,"graphdb.mapdb")).closeOnJvmShutdown()
				.cacheHashTableEnable().make();

		// open existing an collection (or create new)
		nodes = db.treeMap("nodes");
		mappings = db.treeMap("mappings");
        graph = new Graph(nodes, mappings);

    }

    @Override
    public void configure() throws RepositoryConnectionException {
		RepositoryConnectionException.StorageType.OBJECT.configure(config,
				"mapdb", "0.1");
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
    	RepositoryConnectionException.StorageType.OBJECT.verify(config,
				"mapdb", "0.1");
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
        db.commit();
        db.close();
        graph = null;
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
                return new ImmutableList.Builder<ObjectId>().addAll(
                        filter(transform(n.to(), NODE_TO_ID), Predicates.notNull())).build();
            }
        }).or(ImmutableList.<ObjectId> of());
    }

    @Override
    public ImmutableList<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException {
        return graph.get(commitId).transform(new Function<Node, ImmutableList<ObjectId>>() {
            @Override
            public ImmutableList<ObjectId> apply(Node n) {
                return new ImmutableList.Builder<ObjectId>()
                        .addAll(transform(n.from(), NODE_TO_ID)).build();
            }
        }).or(ImmutableList.<ObjectId> of());
    }

    @Override
    public boolean put(ObjectId commitId, ImmutableList<ObjectId> parentIds) {
        Node n = graph.getOrAdd(commitId);

        if (parentIds.isEmpty()) {
            // the root node, only update on first addition
            if (!n.isRoot()) {
                n.setRoot(true);
                db.commit();
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
            db.commit();
            return added;
        }
        return false;
    }

    @Override
    public void map(ObjectId mapped, ObjectId original) {
        graph.map(mapped, original);
        db.commit();
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
        db.commit();
    }

    @Override
    public void truncate() {
        graph.clear();
        db.commit();
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
                edges.add(new GraphEdge(new HeapGraphNode(nodeEdge.src), new HeapGraphNode(
                        nodeEdge.dst)));
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
