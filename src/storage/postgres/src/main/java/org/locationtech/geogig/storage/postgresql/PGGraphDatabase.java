/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static com.google.common.base.Throwables.propagate;
import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.PGStorage.closeDataSource;
import static org.locationtech.geogig.storage.postgresql.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.PGStorage.newDataSource;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.FORMAT_NAME;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

/**
 * Base class for PostgreSQL based graph database.
 * 
 */
public class PGGraphDatabase implements GraphDatabase {
    static Logger LOG = LoggerFactory.getLogger(PGGraphDatabase.class);

    private final String EDGES;

    private final String PROPS;

    private final String MAPPINGS;

    final ConfigDatabase configdb;

    private DataSource dataSource;

    private final String formatVersion;

    private Environment config;

    @Inject
    public PGGraphDatabase(ConfigDatabase configdb, Hints hints) throws URISyntaxException {
        this(configdb, Environment.get(hints));
    }

    public PGGraphDatabase(ConfigDatabase configdb, Environment config) {
        Preconditions.checkNotNull(configdb);
        Preconditions.checkNotNull(config);
        Preconditions.checkArgument(PGStorage.repoExists(config), "Repository %s does not exist",
                config.getRepositoryName());
        this.configdb = configdb;
        this.config = config;
        this.formatVersion = PGStorageProvider.VERSION;
        TableNames tables = config.getTables();
        this.EDGES = tables.graphEdges();
        this.PROPS = tables.graphProperties();
        this.MAPPINGS = tables.graphMappings();
    }

    @Override
    public synchronized void open() {
        if (dataSource == null) {
            dataSource = newDataSource(config);
        }
    }

    @Override
    public boolean isOpen() {
        return dataSource != null;
    }

    @Override
    public synchronized void close() {
        if (dataSource != null) {
            closeDataSource(dataSource);
            dataSource = null;
        }
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.GRAPH.configure(configdb, FORMAT_NAME, formatVersion);
    }

    @Override
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.GRAPH.verify(configdb, FORMAT_NAME, formatVersion);
    }

    /**
     * Determines if a node exists in the graph.
     * <p>
     * A node exists if there's at least one relationship pointing to it in the edges table.
     */
    @Override
    public boolean exists(ObjectId commitId) {
        final PGId node = PGId.valueOf(commitId);
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            return exists(node, cx);
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    private boolean exists(final PGId node, final Connection cx) throws SQLException {
        final String sql = format(
                "SELECT TRUE WHERE EXISTS ( SELECT 1 FROM %s WHERE src = CAST(ROW(?,?,?) AS OBJECTID) OR dst = CAST(ROW(?,?,?) AS OBJECTID) )",
                EDGES);

        boolean exists;
        try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
            node.setArgs(ps, 1);
            node.setArgs(ps, 4);
            try (ResultSet rs = ps.executeQuery()) {
                exists = rs.next();
            }
        }
        return exists;
    }

    @Override
    public ImmutableList<ObjectId> getParents(ObjectId commitId) throws IllegalArgumentException {
        final PGId node = PGId.valueOf(commitId);
        return ImmutableList.copyOf(Iterables.transform(outgoing(node), (p) -> p.toObjectId()));
    }

    @Override
    public ImmutableList<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException {
        return ImmutableList.copyOf(
                Iterables.transform(incoming(PGId.valueOf(commitId)), (p) -> p.toObjectId()));
    }

    @Override
    public boolean put(ObjectId commitId, ImmutableList<ObjectId> parentIds) {
        final PGId node = PGId.valueOf(commitId);
        final boolean exists;
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            exists = exists(node, cx);
            // NOTE: runs in autoCommit mode to ignore statement failures due to duplicates (?)
            // TODO: if node was node added should we severe existing parent relationships?
            for (ObjectId p : parentIds) {
                relate(node, PGId.valueOf(p), cx);
            }
        } catch (SQLException e) {
            throw propagate(e);
        }

        return !exists;
    }

    /**
     * Relates two nodes in the graph.
     * 
     * @param src The source (origin) node of the relationship.
     * @param dst The destination (origin) node of the relationship.
     */
    void relate(final PGId src, final PGId dst, final Connection cx) throws SQLException {
        final String insert = format("INSERT INTO %s (src, dst) VALUES (ROW(?,?,?), ROW(?,?,?))",
                EDGES);

        try (PreparedStatement ps = cx.prepareStatement(log(insert, LOG, src, dst))) {
            src.setArgs(ps, 1);
            dst.setArgs(ps, 4);
            try {
                ps.executeUpdate();
            } catch (SQLException duplicateTuple) {
                // ignore
            }
        }
    }

    /**
     * Creates a node mapping.
     * 
     * @param from The node being mapped from.
     * @param to The node being mapped to.
     */
    @Override
    public void map(ObjectId mapped, ObjectId original) {
        final PGId from = PGId.valueOf(mapped);
        final PGId to = PGId.valueOf(original);
        // lacking upsert...
        String delete = format("DELETE FROM %s where alias = CAST(ROW(?,?,?) AS OBJECTID)",
                MAPPINGS);
        String insert = format("INSERT INTO %s (alias, nid) VALUES ( ROW(?,?,?), ROW(?,?,?) )",
                MAPPINGS);
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try {
                try (PreparedStatement deleteSt = cx.prepareStatement(log(delete, LOG, from))) {
                    from.setArgs(deleteSt, 1);
                    deleteSt.executeUpdate();
                }
                try (PreparedStatement insertSt = cx.prepareStatement(log(insert, LOG, from))) {
                    from.setArgs(insertSt, 1);
                    to.setArgs(insertSt, 4);

                    insertSt.executeUpdate();
                }
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    /**
     * Returns the mapping for a node.
     * <p>
     * This method should return <code>null</code> if no mapping exists.
     * </p>
     */
    @Nullable
    @Override
    public ObjectId getMapping(ObjectId commitId) {
        final PGId node = PGId.valueOf(commitId);

        final String sql = format(
                "SELECT ((nid).h1), ((nid).h2), ((nid).h3) FROM %s WHERE alias = CAST(ROW(?,?,?) AS OBJECTID)",
                MAPPINGS);

        final ObjectId mapped;
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                node.setArgs(ps, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    mapped = rs.next() ? PGId.valueOf(rs, 1).toObjectId() : null;
                }
            }
        } catch (SQLException e) {
            throw propagate(e);
        }

        return mapped;
    }

    @Override
    public int getDepth(ObjectId commitId) {
        int depth = 0;

        Queue<PGId> q = Lists.newLinkedList();
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            Iterables.addAll(q, outgoing(PGId.valueOf(commitId), cx));

            List<PGId> next = Lists.newArrayList();
            while (!q.isEmpty()) {
                depth++;
                while (!q.isEmpty()) {
                    PGId n = q.poll();
                    List<PGId> parents = Lists.newArrayList(outgoing(n, cx));
                    if (parents.size() == 0) {
                        return depth;
                    }

                    Iterables.addAll(next, parents);
                }

                q.addAll(next);
                next.clear();
            }
        } catch (SQLException e) {
            throw propagate(e);
        }

        return depth;
    }

    /**
     * Assigns a property key/value pair to a node.
     */
    @Override
    public void setProperty(ObjectId commitId, String name, String value) {
        final PGId node = PGId.valueOf(commitId);
        final String delete = format(
                "DELETE FROM %s WHERE nid = CAST(ROW(?,?,?) AS OBJECTID) AND  key = ?", PROPS);
        final String insert = format("INSERT INTO %s (nid,key,val) VALUES (ROW(?,?,?), ?, ?)",
                PROPS);
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try {
                try (PreparedStatement ds = cx.prepareStatement(log(delete, LOG, node, name))) {
                    node.setArgs(ds, 1);
                    ds.setString(4, name);
                    ds.executeUpdate();
                }
                try (PreparedStatement is = cx
                        .prepareStatement(log(insert, LOG, node, name, value))) {
                    node.setArgs(is, 1);
                    is.setString(4, name);
                    is.setString(5, value);
                    is.executeUpdate();
                }
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    @Override
    public void truncate() {
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try {
                try (Statement st = cx.createStatement()) {
                    st.execute(log(format("TRUNCATE %s", MAPPINGS), LOG));
                    st.execute(log(format("TRUNCATE %s", PROPS), LOG));
                    st.execute(log(format("TRUNCATE %s", EDGES), LOG));
                    // this is a view now. st.execute(format("DELETE FROM %s", NODES));
                    cx.commit();
                }
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    @Override
    public GraphNode getNode(ObjectId id) {
        return new PGGraphNode(id);
    }

    /**
     * Retrieves a property by key from a node.
     * 
     * @param node The node.
     * @param key The property key.
     * 
     * @return The property value, or <code>null</code> if the property is not set for the node.
     */
    @Nullable
    String property(final PGId node, final String key, DataSource ds) {
        final String sql = format(
                "SELECT val FROM %s WHERE nid = CAST(ROW(?,?,?) AS OBJECTID) AND key = ?", PROPS);

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node, key))) {
                node.setArgs(ps, 1);
                ps.setString(4, key);

                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    Iterable<PGId> outgoing(final PGId node) {
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            return outgoing(node, cx);
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    /**
     * Returns all nodes connected to the specified node through a relationship in which the
     * specified node is the "source" of the relationship.
     */
    Iterable<PGId> outgoing(final PGId node, final Connection cx) throws SQLException {
        final String sql = format(
                "SELECT ((dst).h1), ((dst).h2),((dst).h3) FROM %s WHERE src = CAST(ROW(?,?,?) AS OBJECTID)",
                EDGES);

        List<PGId> outgoing = new ArrayList<>(2);

        try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
            node.setArgs(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    outgoing.add(PGId.valueOf(rs, 1));
                }
            }
        }

        return outgoing;
    }

    /**
     * Returns all nodes connected to the specified node through a relationship in which the
     * specified node is the "destination" of the relationship.
     */
    Iterable<PGId> incoming(final PGId node) {
        final String sql = format(
                "SELECT ((src).h1), ((src).h2),((src).h3) FROM %s WHERE dst = CAST(ROW(?,?,?) AS OBJECTID)",
                EDGES);

        List<PGId> incoming = new ArrayList<>(2);
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                node.setArgs(ps, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        incoming.add(PGId.valueOf(rs, 1));
                    }
                }
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
        return incoming;
    }

    private class PGGraphNode extends GraphNode {

        private ObjectId id;

        public PGGraphNode(ObjectId id) {
            this.id = id;
        }

        @Override
        public ObjectId getIdentifier() {
            return id;
        }

        @Override
        public Iterator<GraphEdge> getEdges(final Direction direction) {
            List<GraphEdge> edges = new LinkedList<GraphEdge>();

            final PGId pgId = PGId.valueOf(id);

            if (direction == Direction.IN || direction == Direction.BOTH) {
                Iterator<PGId> nodeEdges = incoming(pgId).iterator();
                while (nodeEdges.hasNext()) {
                    PGId otherNode = nodeEdges.next();
                    edges.add(new GraphEdge(new PGGraphNode(otherNode.toObjectId()), this));
                }
            }
            if (direction == Direction.OUT || direction == Direction.BOTH) {
                Iterator<PGId> nodeEdges = outgoing(pgId).iterator();
                while (nodeEdges.hasNext()) {
                    PGId otherNode = nodeEdges.next();
                    edges.add(new GraphEdge(this, new PGGraphNode(otherNode.toObjectId())));
                }
            }
            return edges.iterator();
        }

        @Override
        public boolean isSparse() {
            @Nullable
            String sparse = property(PGId.valueOf(id), SPARSE_FLAG, dataSource);
            return Boolean.parseBoolean(sparse);
        }
    }
}
