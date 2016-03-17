/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.PGStorage.closeDataSource;
import static org.locationtech.geogig.storage.postgresql.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.PGStorage.newDataSource;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.FORMAT_NAME;

import java.io.IOException;
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
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
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
        // Preconditions.checkArgument(PGStorage.repoExists(config), "Repository %s does not exist",
        // config.repositoryId);
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
        RepositoryConnectionException.StorageType.GRAPH.configure(configdb, FORMAT_NAME,
                formatVersion);
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.GRAPH
                .verify(configdb, FORMAT_NAME, formatVersion);
    }

    @Override
    public boolean exists(ObjectId commitId) {
        return has(PGId.valueOf(commitId), dataSource);
    }

    @Override
    public ImmutableList<ObjectId> getParents(ObjectId commitId) throws IllegalArgumentException {
        return ImmutableList.copyOf(Iterables.transform(
                outgoing(PGId.valueOf(commitId), dataSource), (p) -> p.toObjectId()));
    }

    @Override
    public ImmutableList<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException {
        return ImmutableList.copyOf(Iterables.transform(
                incoming(PGId.valueOf(commitId), dataSource), (p) -> p.toObjectId()));
    }

    @Override
    public boolean put(ObjectId commitId, ImmutableList<ObjectId> parentIds) {
        PGId node = PGId.valueOf(commitId);

        final boolean exists = has(node, dataSource);

        // TODO: if node was node added should we severe existing parent relationships?
        for (ObjectId p : parentIds) {
            relate(node, PGId.valueOf(p), dataSource);
        }
        return !exists;
    }

    @Override
    public void map(ObjectId mapped, ObjectId original) {
        map(PGId.valueOf(mapped), PGId.valueOf(original), dataSource);
    }

    @Override
    public ObjectId getMapping(ObjectId commitId) {
        @Nullable
        PGId mapped = mapping(PGId.valueOf(commitId), dataSource);
        return mapped == null ? null : mapped.toObjectId();
    }

    @Override
    public int getDepth(ObjectId commitId) {
        int depth = 0;

        Queue<PGId> q = Lists.newLinkedList();
        Iterables.addAll(q, outgoing(PGId.valueOf(commitId), dataSource));

        List<PGId> next = Lists.newArrayList();
        while (!q.isEmpty()) {
            depth++;
            while (!q.isEmpty()) {
                PGId n = q.poll();
                List<PGId> parents = Lists.newArrayList(outgoing(n, dataSource));
                if (parents.size() == 0) {
                    return depth;
                }

                Iterables.addAll(next, parents);
            }

            q.addAll(next);
            next.clear();
        }

        return depth;
    }

    @Override
    public void setProperty(ObjectId commitId, String name, String value) {
        property(PGId.valueOf(commitId), name, value, dataSource);
    }

    @Override
    public void truncate() {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
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
                }
                return null;
            }
        }.run(dataSource);
    }

    @Override
    public GraphNode getNode(ObjectId id) {
        return new PGGraphNode(id);
    }

    /**
     * Determines if a node exists in the graph.
     * <p>
     * A node exists if there's at least one relationship pointing to it in the edges table.
     */
    boolean has(final PGId node, DataSource ds) {
        return new DbOp<Boolean>() {
            @Override
            protected Boolean doRun(Connection cx) throws IOException, SQLException {
                String sql = format(
                        "SELECT TRUE WHERE EXISTS ( SELECT 1 FROM %s WHERE src = CAST(ROW(?,?,?) AS OBJECTID) OR dst = CAST(ROW(?,?,?) AS OBJECTID) )",
                        EDGES);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                    node.setArgs(ps, 1);
                    node.setArgs(ps, 4);
                    try (ResultSet rs = ps.executeQuery()) {
                        boolean exists = rs.next();
                        return exists;
                    }
                }
            }
        }.run(ds);
    }

    /**
     * Relates two nodes in the graph.
     * 
     * @param src The source (origin) node of the relationship.
     * @param dst The destination (origin) node of the relationship.
     */
    void relate(final PGId src, final PGId dst, DataSource ds) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                final String insert = format(
                        "INSERT INTO %s (src, dst) VALUES (ROW(?,?,?), ROW(?,?,?))", EDGES);

                try (PreparedStatement ps = cx.prepareStatement(log(insert, LOG, src, dst))) {
                    src.setArgs(ps, 1);
                    dst.setArgs(ps, 4);
                    try {
                        ps.executeUpdate();
                    } catch (SQLException duplicateTuple) {
                        // ignore
                    }
                }
                return null;
            }
        }.run(ds);
    }

    /**
     * Creates a node mapping.
     * 
     * @param from The node being mapped from.
     * @param to The node being mapped to.
     */
    void map(final PGId from, final PGId to, DataSource ds) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                // lacking upsert...
                String delete = format("DELETE FROM %s where alias = CAST(ROW(?,?,?) AS OBJECTID)",
                        MAPPINGS);
                String insert = format(
                        "INSERT INTO %s (alias, nid) VALUES ( ROW(?,?,?), ROW(?,?,?) )", MAPPINGS);
                cx.setAutoCommit(false);
                try {
                    try (PreparedStatement ds = cx.prepareStatement(log(delete, LOG, from))) {
                        from.setArgs(ds, 1);
                        ds.executeUpdate();
                    }

                    try (PreparedStatement ps = cx.prepareStatement(log(insert, LOG, from))) {
                        from.setArgs(ps, 1);
                        to.setArgs(ps, 4);

                        ps.executeUpdate();
                    }
                    cx.commit();
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }
                return null;
            }
        }.run(ds);
    }

    /**
     * Returns the mapping for a node.
     * <p>
     * This method should return <code>null</code> if no mapping exists.
     * </p>
     */
    @Nullable
    PGId mapping(final PGId node, DataSource ds) {
        return new DbOp<PGId>() {
            @Override
            protected PGId doRun(Connection cx) throws IOException, SQLException {
                String sql = format(
                        "SELECT ((nid).h1), ((nid).h2), ((nid).h3) FROM %s WHERE alias = CAST(ROW(?,?,?) AS OBJECTID)",
                        MAPPINGS);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                    node.setArgs(ps, 1);

                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? PGId.valueOf(rs, 1) : null;
                    }
                }
            }
        }.run(ds);
    }

    /**
     * Assigns a property key/value pair to a node.
     * 
     * @param node The node.
     * @param key The property key.
     * @param value The property value.
     */
    void property(final PGId node, final String key, final String val, DataSource ds) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {

                final String delete = format(
                        "DELETE FROM %s WHERE nid = CAST(ROW(?,?,?) AS OBJECTID) AND  key = ?",
                        PROPS);
                final String insert = format(
                        "INSERT INTO %s (nid,key,val) VALUES (ROW(?,?,?), ?, ?)", PROPS);
                cx.setAutoCommit(false);
                try {
                    try (PreparedStatement ds = cx.prepareStatement(log(delete, LOG, node, key))) {
                        node.setArgs(ds, 1);
                        ds.setString(4, key);
                        ds.executeUpdate();
                    }
                    try (PreparedStatement is = cx
                            .prepareStatement(log(insert, LOG, node, key, val))) {
                        node.setArgs(is, 1);
                        is.setString(4, key);
                        is.setString(5, val);
                        is.executeUpdate();
                    }
                    cx.commit();
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }
                return null;
            }
        }.run(ds);
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
        return new DbOp<String>() {
            @Override
            protected String doRun(Connection cx) throws IOException, SQLException {
                String sql = format(
                        "SELECT val FROM %s WHERE nid = CAST(ROW(?,?,?) AS OBJECTID) AND key = ?",
                        PROPS);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node, key))) {
                    node.setArgs(ps, 1);
                    ps.setString(4, key);

                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
                    }
                }
            }
        }.run(ds);
    }

    /**
     * Returns all nodes connected to the specified node through a relationship in which the
     * specified node is the "source" of the relationship.
     */
    Iterable<PGId> outgoing(final PGId node, DataSource ds) {
        List<PGId> rs = new DbOp<List<PGId>>() {
            @Override
            protected List<PGId> doRun(Connection cx) throws IOException, SQLException {
                String sql = format(
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
        }.run(ds);

        return rs;
    }

    /**
     * Returns all nodes connected to the specified node through a relationship in which the
     * specified node is the "destination" of the relationship.
     */
    Iterable<PGId> incoming(final PGId node, DataSource ds) {
        List<PGId> rs = new DbOp<List<PGId>>() {
            @Override
            protected List<PGId> doRun(Connection cx) throws IOException, SQLException {
                String sql = format(
                        "SELECT ((src).h1), ((src).h2),((src).h3) FROM %s WHERE dst = CAST(ROW(?,?,?) AS OBJECTID)",
                        EDGES);

                List<PGId> incoming = new ArrayList<>(2);
                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                    node.setArgs(ps, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            incoming.add(PGId.valueOf(rs, 1));
                        }
                    }
                }
                return incoming;
            }
        }.run(ds);

        return rs;
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
                Iterator<PGId> nodeEdges = incoming(pgId, dataSource).iterator();
                while (nodeEdges.hasNext()) {
                    PGId otherNode = nodeEdges.next();
                    edges.add(new GraphEdge(new PGGraphNode(otherNode.toObjectId()), this));
                }
            }
            if (direction == Direction.OUT || direction == Direction.BOTH) {
                Iterator<PGId> nodeEdges = outgoing(pgId, dataSource).iterator();
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
