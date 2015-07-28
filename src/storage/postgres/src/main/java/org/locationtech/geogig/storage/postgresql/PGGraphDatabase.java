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

    private final String NODES;

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
        this.NODES = tables.graphNodes();
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
        return has(commitId.toString(), dataSource);
    }

    @Override
    public ImmutableList<ObjectId> getParents(ObjectId commitId) throws IllegalArgumentException {
        return ImmutableList.copyOf(Iterables.transform(outgoing(commitId.toString(), dataSource),
                StringToObjectId.INSTANCE));
    }

    @Override
    public ImmutableList<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException {
        return ImmutableList.copyOf(Iterables.transform(incoming(commitId.toString(), dataSource),
                StringToObjectId.INSTANCE));
    }

    @Override
    public boolean put(ObjectId commitId, ImmutableList<ObjectId> parentIds) {
        String node = commitId.toString();
        boolean added = put(node, dataSource);

        // TODO: if node was node added should we severe existing parent relationships?
        for (ObjectId p : parentIds) {
            relate(node, p.toString(), dataSource);
        }
        return added;
    }

    @Override
    public void map(ObjectId mapped, ObjectId original) {
        map(mapped.toString(), original.toString(), dataSource);
    }

    @Override
    public ObjectId getMapping(ObjectId commitId) {
        String mapped = mapping(commitId.toString(), dataSource);
        return mapped != null ? ObjectId.valueOf(mapped) : null;
    }

    @Override
    public int getDepth(ObjectId commitId) {
        int depth = 0;

        Queue<String> q = Lists.newLinkedList();
        Iterables.addAll(q, outgoing(commitId.toString(), dataSource));

        List<String> next = Lists.newArrayList();
        while (!q.isEmpty()) {
            depth++;
            while (!q.isEmpty()) {
                String n = q.poll();
                List<String> parents = Lists.newArrayList(outgoing(n, dataSource));
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
        property(commitId.toString(), name, value, dataSource);
    }

    @Override
    public void truncate() {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                cx.setAutoCommit(false);
                try {
                    cx.createStatement().execute(format("DELETE FROM %s", MAPPINGS));
                    cx.createStatement().execute(format("DELETE FROM %s", PROPS));
                    cx.createStatement().execute(format("DELETE FROM %s", EDGES));
                    cx.createStatement().execute(format("DELETE FROM %s", NODES));
                    cx.commit();
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }
                return null;
            }
        }.run(dataSource);
    }

    /**
     * Adds a new node to the graph.
     * <p>
     * This method must determine if the node already exists in the graph.
     * </p>
     * 
     * @return True if the node did not previously exist in the graph, false if otherwise.
     */
    private boolean put(final String node, DataSource ds) {
        if (has(node, ds)) {
            return false;
        }

        return new DbOp<Boolean>() {
            @Override
            protected Boolean doRun(Connection cx) throws IOException, SQLException {
                String sql = format("INSERT INTO %s (id) VALUES (?)", NODES);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                    ps.setString(1, node);
                    try {
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        return false; // PK violation
                    }
                    return true;
                }
            }
        }.run(ds);
    }

    /**
     * Determines if a node exists in the graph.
     */
    public boolean has(final String node, DataSource ds) {
        return new DbOp<Boolean>() {
            @Override
            protected Boolean doRun(Connection cx) throws IOException, SQLException {
                String sql = format("SELECT count(*) FROM %s WHERE id = ?", NODES);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                    ps.setString(1, node);

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getInt(1) > 0;
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
    public void relate(final String src, final String dst, DataSource ds) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                final String source = src;
                final String dest = dst;
                final String insert = format("INSERT INTO %s (src, dst) VALUES (?, ?)", EDGES);

                try (PreparedStatement ps = cx.prepareStatement(log(insert, LOG, src, dst))) {
                    ps.setString(1, source);
                    ps.setString(2, dest);
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
    public void map(final String from, final String to, DataSource ds) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                // lacking upsert...
                String delete = format("DELETE FROM %s where alias = ?", MAPPINGS);
                String insert = format("INSERT INTO %s (alias, nid) VALUES (?,?)", MAPPINGS);
                cx.setAutoCommit(false);
                try {
                    final String alias = from;
                    final String nid = to;
                    try (PreparedStatement ds = cx.prepareStatement(log(delete, LOG, from))) {
                        ds.setString(1, from);
                        ds.executeUpdate();
                    }

                    try (PreparedStatement ps = cx.prepareStatement(log(insert, LOG, from))) {
                        ps.setString(1, alias);
                        ps.setString(2, nid);
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
    public String mapping(final String node, DataSource ds) {
        return new DbOp<String>() {
            @Override
            protected String doRun(Connection cx) throws IOException, SQLException {
                String sql = format("SELECT nid FROM %s WHERE alias = ?", MAPPINGS);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                    ps.setString(1, node);

                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
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
    public void property(final String node, final String key, final String val, DataSource ds) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {

                final String delete = format("DELETE FROM %s WHERE nid = ? AND  key = ?", PROPS);
                final String insert = format("INSERT INTO %s (nid,key,val) VALUES (?,?,?)", PROPS);
                cx.setAutoCommit(false);
                try {
                    try (PreparedStatement ds = cx.prepareStatement(log(delete, LOG, node, key))) {
                        ds.setString(1, node);
                        ds.setString(2, key);
                        ds.executeUpdate();
                    }
                    try (PreparedStatement is = cx
                            .prepareStatement(log(insert, LOG, node, key, val))) {
                        is.setString(1, node);
                        is.setString(2, key);
                        is.setString(3, val);
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
    public String property(final String node, final String key, DataSource ds) {
        return new DbOp<String>() {
            @Override
            protected String doRun(Connection cx) throws IOException, SQLException {
                String sql = format("SELECT val FROM %s WHERE nid = ? AND key = ?", PROPS);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node, key))) {
                    ps.setString(1, node);
                    ps.setString(2, key);

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
    public Iterable<String> outgoing(final String node, DataSource ds) {
        List<String> rs = new DbOp<List<String>>() {
            @Override
            protected List<String> doRun(Connection cx) throws IOException, SQLException {
                String sql = format("SELECT dst FROM %s WHERE src = ?", EDGES);

                List<String> outgoing = new ArrayList<>(2);

                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                    ps.setString(1, node);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            outgoing.add(rs.getString(1));
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
    public Iterable<String> incoming(final String node, DataSource ds) {
        List<String> rs = new DbOp<List<String>>() {
            @Override
            protected List<String> doRun(Connection cx) throws IOException, SQLException {
                String sql = format("SELECT src FROM %s WHERE dst = ?", EDGES);

                List<String> incoming = new ArrayList<>(2);
                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
                    ps.setString(1, node);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            incoming.add(rs.getString(1));
                        }
                    }
                }
                return incoming;
            }
        }.run(ds);

        return rs;
    }

    /**
     * Clears the contents of the graph.
     */
    public void clear(DataSource ds) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                try (Statement st = cx.createStatement()) {

                    String sql = format("DELETE FROM %s", PROPS);
                    st.execute(log(sql, LOG));

                    sql = format("DELETE FROM %s", EDGES);
                    st.execute(log(sql, LOG));

                    sql = format("DELETE FROM %s", NODES);
                    st.execute(log(sql, LOG));

                    sql = format("DELETE FROM %s", MAPPINGS);
                    st.execute(log(sql, LOG));
                }
                return null;
            }
        }.run(ds);
    }

    private class SQLiteGraphNode extends GraphNode {

        private ObjectId id;

        public SQLiteGraphNode(ObjectId id) {
            this.id = id;
        }

        @Override
        public ObjectId getIdentifier() {
            return id;
        }

        @Override
        public Iterator<GraphEdge> getEdges(final Direction direction) {
            List<GraphEdge> edges = new LinkedList<GraphEdge>();
            if (direction == Direction.IN || direction == Direction.BOTH) {
                Iterator<String> nodeEdges = incoming(id.toString(), dataSource).iterator();
                while (nodeEdges.hasNext()) {
                    String otherNode = nodeEdges.next();
                    edges.add(new GraphEdge(new SQLiteGraphNode(ObjectId.valueOf(otherNode)), this));
                }
            }
            if (direction == Direction.OUT || direction == Direction.BOTH) {
                Iterator<String> nodeEdges = outgoing(id.toString(), dataSource).iterator();
                while (nodeEdges.hasNext()) {
                    String otherNode = nodeEdges.next();
                    edges.add(new GraphEdge(this, new SQLiteGraphNode(ObjectId.valueOf(otherNode))));
                }
            }
            return edges.iterator();
        }

        @Override
        public boolean isSparse() {
            String sparse = property(id.toString(), SPARSE_FLAG, dataSource);
            return sparse != null && Boolean.valueOf(sparse);
        }
    }

    @Override
    public GraphNode getNode(ObjectId id) {
        return new SQLiteGraphNode(id);
    }
}
