/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.rocksdb.DBHandle.RocksDBReference;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.datastream.Varint;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteBatchWithIndex;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

public class RocksdbGraphDatabase implements GraphDatabase {

    private static final Logger LOG = LoggerFactory.getLogger(RocksdbGraphDatabase.class);

    private static final GraphNodeBinding BINDING = new GraphNodeBinding();

    private final File dbdir;

    private final boolean readOnly;

    private boolean open;

    private DBHandle dbhandle;

    @Inject
    public RocksdbGraphDatabase(Platform platform, Hints hints) {
        this.readOnly = hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
        Optional<URI> uri = new ResolveGeogigURI(platform, hints).call();
        Preconditions.checkArgument(uri.isPresent(), "not in a geogig directory");
        Preconditions.checkArgument("file".equals(uri.get().getScheme()),
                "Repository URI is not file://");

        File basedir = new File(uri.get());
        this.dbdir = new File(basedir, "graph.rocksdb");
    }

    RocksdbGraphDatabase(File dbdir, boolean readOnly) {
        this.dbdir = dbdir;
        this.readOnly = readOnly;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void open() {
        if (isOpen()) {
            return;
        }
        String dbpath = dbdir.getAbsolutePath();
        DBConfig opts = new DBConfig(dbpath, readOnly);
        this.dbhandle = RocksConnectionManager.INSTANCE.acquire(opts);
        this.open = true;
    }

    @Override
    public synchronized void close() {
        if (!isOpen()) {
            return;
        }
        this.open = false;
        RocksConnectionManager.INSTANCE.release(dbhandle);
        this.dbhandle = null;
    }

    private static final byte[] NODATA = new byte[0];

    @Override
    public boolean exists(ObjectId commitId) {
        byte[] key = commitId.getRawValue();
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            int size = dbRef.db().get(key, NODATA);
            return size != RocksDB.NOT_FOUND;
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ImmutableList<ObjectId> getParents(ObjectId commitId) throws IllegalArgumentException {
        NodeData node = getNodeInternal(commitId, false);
        if (node != null) {
            return ImmutableList.copyOf(node.outgoing);
        }
        return ImmutableList.of();
    }

    @Override
    public ImmutableList<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException {
        NodeData node = getNodeInternal(commitId, false);
        if (node != null) {
            return ImmutableList.copyOf(node.incoming);
        }
        return ImmutableList.of();
    }

    @Override
    public boolean put(ObjectId commitId, ImmutableList<ObjectId> parentIds) {
        try (WriteBatchWithIndex batch = new WriteBatchWithIndex(); //
                RocksDBReference dbRef = dbhandle.getReference();
                WriteOptions wo = new WriteOptions()) {
            wo.setSync(true);

            boolean updated = put(dbRef, commitId, parentIds, batch);
            dbRef.db().write(wo, batch);
            return updated;
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Atomically inserts the graph entries for all the provided commits.
     * 
     * @param commits the commits to create the graph entries for (one entry for each commit/parent)
     * 
     * @implNote uses a {@link WriteBatchWithIndex} to query uncommitted data through
     *           {@link WriteBatchWithIndex#getFromBatchAndDB getFromBatchAndDB}
     */
    public void putAll(Iterable<RevCommit> commits) {
        int count = 0;
        final Stopwatch sw = LOG.isTraceEnabled() ? Stopwatch.createStarted() : null;
        try (WriteBatchWithIndex batch = new WriteBatchWithIndex(); //
                RocksDBReference dbRef = dbhandle.getReference();
                WriteOptions wo = new WriteOptions()) {
            wo.setSync(true);
            for (RevCommit c : commits) {
                ObjectId commitId = c.getId();
                ImmutableList<ObjectId> parentIds = c.getParentIds();
                put(dbRef, commitId, parentIds, batch);
                count++;
            }
            dbRef.db().write(wo, batch);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace(String.format("Inserted %,d graph mappings in %s", count, sw.stop()));
        }
    }

    private boolean put(RocksDBReference dbref, ObjectId commitId,
            ImmutableList<ObjectId> parentIds, WriteBatchWithIndex batch) {

        @Nullable
        NodeData node = getNodeInternal(dbref, commitId, false, batch);

        boolean updated = false;
        if (node == null) {
            node = new NodeData(commitId, parentIds);
            updated = true;
        }
        try {
            for (ObjectId parent : parentIds) {
                if (!node.outgoing.contains(parent)) {
                    node.outgoing.add(parent);
                    updated = true;
                }
                NodeData parentNode = getNodeInternal(dbref, parent, false, batch);
                if (parentNode == null) {
                    parentNode = new NodeData(parent);
                    updated = true;
                }
                if (!parentNode.incoming.contains(commitId)) {
                    parentNode.incoming.add(commitId);
                    updated = true;
                }
                batch.put(parent.getRawValue(), BINDING.objectToEntry(parentNode));
            }
            batch.put(commitId.getRawValue(), BINDING.objectToEntry(node));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return updated;
    }

    @Override
    public void map(ObjectId mapped, ObjectId original) {
        NodeData node = getNodeInternal(mapped, false);
        if (node == null) {
            // didn't exist
            node = new NodeData(mapped);
        }
        node.mappedTo = original;
        try {
            putNodeInternal(mapped, node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ObjectId getMapping(ObjectId commitId) {
        NodeData node = getNodeInternal(commitId, true);
        return node.mappedTo;
    }

    @Override
    public int getDepth(ObjectId commitId) {
        int depth = 0;

        try (RocksDBReference dbRef = dbhandle.getReference()) {
            Queue<ObjectId> q = Lists.newLinkedList();
            NodeData node = getNodeInternal(dbRef, commitId, true, null);
            Iterables.addAll(q, node.outgoing);

            List<ObjectId> next = Lists.newArrayList();
            while (!q.isEmpty()) {
                depth++;
                while (!q.isEmpty()) {
                    ObjectId n = q.poll();
                    NodeData parentNode = getNodeInternal(dbRef, n, true, null);
                    List<ObjectId> parents = Lists.newArrayList(parentNode.outgoing);
                    if (parents.size() == 0) {
                        return depth;
                    }

                    Iterables.addAll(next, parents);
                }

                q.addAll(next);
                next.clear();
            }
        }

        return depth;
    }

    @Override
    public void setProperty(ObjectId commitId, String propertyName, String propertyValue) {
        NodeData node = getNodeInternal(commitId, true);
        node.properties.put(propertyName, propertyValue);
        try {
            putNodeInternal(commitId, node);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public GraphNode getNode(ObjectId id) {
        return new RocksGraphNode(getNodeInternal(id, true));
    }

    @Override
    public void truncate() {
        try (RocksDBReference dbRef = dbhandle.getReference()) {
            try (RocksIterator it = dbRef.db().newIterator()) {
                it.seekToFirst();
                try (WriteOptions wo = new WriteOptions(); //
                        WriteBatch batch = new WriteBatch()) {
                    wo.setSync(true);
                    while (it.isValid()) {
                        batch.remove(it.key());
                        it.next();
                    }
                    dbRef.db().write(wo, batch);
                }
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nullable
    private NodeData getNodeInternal(final ObjectId id, final boolean failIfNotFound) {

        try (RocksDBReference dbRef = dbhandle.getReference()) {
            return getNodeInternal(dbRef, id, failIfNotFound, null);
        }

    }

    private NodeData getNodeInternal(final RocksDBReference dbRef, final ObjectId id,
            final boolean failIfNotFound, @Nullable WriteBatchWithIndex batch) {
        Preconditions.checkNotNull(id, "id");
        byte[] key = id.getRawValue();
        byte[] data;
        try (ReadOptions ro = new ReadOptions()) {
            final RocksDB db = dbRef.db();
            if (batch == null) {
                data = db.get(key);
            } else {
                data = batch.getFromBatchAndDB(db, ro, key);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        if (null == data) {
            if (failIfNotFound) {
                throw new IllegalArgumentException("Graph Object does not exist: " + id);
            }
            return null;
        }
        NodeData node = BINDING.entryToObject(data);
        return node;
    }

    private void putNodeInternal(final ObjectId id, final NodeData node) throws IOException {

        byte[] key = id.getRawValue();
        byte[] data = BINDING.objectToEntry(node);

        try (RocksDBReference dbRef = dbhandle.getReference()) {
            dbRef.db().put(key, data);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    private class RocksGraphNode extends GraphNode {
        NodeData node;

        List<GraphEdge> edges;

        public RocksGraphNode(NodeData node) {
            this.node = node;
            this.edges = null;
        }

        @Override
        public ObjectId getIdentifier() {
            return node.id;
        }

        @Override
        public Iterator<GraphEdge> getEdges(final Direction direction) {
            if (edges == null) {
                edges = new LinkedList<GraphEdge>();
                Iterator<ObjectId> nodeEdges = node.incoming.iterator();
                while (nodeEdges.hasNext()) {
                    ObjectId otherNode = nodeEdges.next();
                    edges.add(new GraphEdge(new RocksGraphNode(getNodeInternal(otherNode, true)),
                            this));
                }

                nodeEdges = node.outgoing.iterator();
                while (nodeEdges.hasNext()) {
                    ObjectId otherNode = nodeEdges.next();
                    edges.add(new GraphEdge(this,
                            new RocksGraphNode(getNodeInternal(otherNode, true))));
                }
            }

            final GraphNode myNode = this;

            return Iterators.filter(edges.iterator(), new Predicate<GraphEdge>() {
                @Override
                public boolean apply(GraphEdge input) {
                    switch (direction) {
                    case OUT:
                        return input.getFromNode() == myNode;
                    case IN:
                        return input.getToNode() == myNode;
                    default:
                        break;
                    }
                    return true;
                }
            });
        }

        @Override
        public boolean isSparse() {
            return node.isSparse();
        }

    }

    private static class GraphNodeBinding {

        private static final ObjectIdBinding OID = new ObjectIdBinding();

        private static final OidListBinding OIDLIST = new OidListBinding();

        private static final PropertiesBinding PROPS = new PropertiesBinding();

        public NodeData entryToObject(byte[] input) {
            DataInput in = ByteStreams.newDataInput(input);

            try {
                ObjectId id = OID.entryToObject(in);
                ObjectId mappedTo = OID.entryToObject(in);
                List<ObjectId> outgoing = OIDLIST.entryToObject(in);
                List<ObjectId> incoming = OIDLIST.entryToObject(in);
                Map<String, String> properties = PROPS.entryToObject(in);

                NodeData nodeData = new NodeData(id, mappedTo, outgoing, incoming, properties);
                return nodeData;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public byte[] objectToEntry(NodeData node) {
            try {
                ByteArrayDataOutput output = ByteStreams.newDataOutput();
                OID.objectToEntry(node.id, output);
                OID.objectToEntry(node.mappedTo, output);
                OIDLIST.objectToEntry(node.outgoing, output);
                OIDLIST.objectToEntry(node.incoming, output);
                PROPS.objectToEntry(node.properties, output);
                return output.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static class ObjectIdBinding {

            @Nullable
            public ObjectId entryToObject(DataInput input) throws IOException {
                int size = input.readByte() & 0xFF;
                if (size == 0) {
                    return ObjectId.NULL;
                }
                Preconditions.checkState(ObjectId.NUM_BYTES == size);
                return ObjectId.readFrom(input);
            }

            public void objectToEntry(@Nullable ObjectId object, DataOutput output)
                    throws IOException {
                if (null == object || object.isNull()) {
                    output.writeByte(0);
                } else {
                    output.writeByte(ObjectId.NUM_BYTES);
                    object.writeTo(output);
                }
            }
        }

        private static class OidListBinding {
            private static final ObjectIdBinding OID = new ObjectIdBinding();

            public List<ObjectId> entryToObject(DataInput input) throws IOException {
                int len = Varint.readUnsignedVarInt(input);
                List<ObjectId> list = new ArrayList<ObjectId>((int) (1.5 * len));
                for (int i = 0; i < len; i++) {
                    list.add(OID.entryToObject(input));
                }
                return list;
            }

            public void objectToEntry(List<ObjectId> list, DataOutput output) throws IOException {
                int len = list.size();
                Varint.writeUnsignedVarInt(len, output);
                for (int i = 0; i < len; i++) {
                    OID.objectToEntry(list.get(i), output);
                }
            }

        }

        private static class PropertiesBinding {

            public Map<String, String> entryToObject(DataInput input) throws IOException {
                int len = Varint.readUnsignedVarInt(input);
                Map<String, String> props = new HashMap<String, String>();
                for (int i = 0; i < len; i++) {
                    String k = input.readUTF();
                    String v = input.readUTF();
                    props.put(k, v);
                }
                return props;
            }

            public void objectToEntry(Map<String, String> props, DataOutput output)
                    throws IOException {
                Varint.writeUnsignedVarInt(props.size(), output);
                for (Map.Entry<String, String> e : props.entrySet()) {
                    output.writeUTF(e.getKey());
                    output.writeUTF(e.getValue());
                }
            }
        }
    }

    private static class NodeData {
        public ObjectId id;

        public List<ObjectId> outgoing;

        public List<ObjectId> incoming;

        public Map<String, String> properties;

        @Nullable
        public ObjectId mappedTo;

        public NodeData(ObjectId id, List<ObjectId> parents) {
            this(id, ObjectId.NULL, new ArrayList<ObjectId>(parents), new ArrayList<ObjectId>(2),
                    new HashMap<String, String>());
        }

        NodeData(ObjectId id, ObjectId mappedTo, List<ObjectId> parents, List<ObjectId> children,
                Map<String, String> properties) {
            this.id = id;
            this.mappedTo = mappedTo;
            this.outgoing = parents;
            this.incoming = children;
            this.properties = properties;
        }

        public NodeData(ObjectId id) {
            this(id, ImmutableList.<ObjectId> of());
        }

        public boolean isSparse() {
            return properties.containsKey(SPARSE_FLAG)
                    ? Boolean.valueOf(properties.get(SPARSE_FLAG))
                    : false;
        }
    }
}
