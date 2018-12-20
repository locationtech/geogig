/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.closeDataSource;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.newDataSource;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGId;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.TableNames;
import org.locationtech.geogig.storage.postgresql.config.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
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

    private DataSource dataSource;

    private Environment config;

    private Version serverVersion;

    @Inject
    public PGGraphDatabase(Hints hints) throws URISyntaxException {
        this(Environment.get(hints));
    }

    public PGGraphDatabase(Environment config) {
        Preconditions.checkNotNull(config);
        Preconditions.checkArgument(PGStorage.repoExists(config), "Repository %s does not exist",
                config.getRepositoryName());
        this.config = config;
        TableNames tables = config.getTables();
        this.EDGES = tables.graphEdges();
        this.PROPS = tables.graphProperties();
        this.MAPPINGS = tables.graphMappings();
    }

    @Override
    public synchronized void open() {
        if (dataSource == null) {
            dataSource = newDataSource(config);
            serverVersion = PGStorage.getServerVersion(config);
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
            throw new RuntimeException(e);
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

        // (p) -> p.toObjectId()
        Function<PGId, ObjectId> fn =  new Function<PGId, ObjectId>() {
            @Override
            public ObjectId apply(PGId p) {
                return p.toObjectId();
            }};

        return ImmutableList.copyOf(Iterables.transform(outgoing(node),fn));
    }

    @Override
    public ImmutableList<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException {

        // (p) -> p.toObjectId()
        Function<PGId, ObjectId> fn =  new Function<PGId, ObjectId>() {
            @Override
            public ObjectId apply(PGId p) {
                return p.toObjectId();
            }};

        return ImmutableList.copyOf(
                Iterables.transform(incoming(PGId.valueOf(commitId)), fn));
    }

    @Override
    public boolean put(ObjectId commitId, ImmutableList<ObjectId> parentIds) {
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            return put(cx, commitId, parentIds);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    boolean put(Connection cx, ObjectId commitId, List<ObjectId> parentIds) {
        try {
            boolean updated;
            cx.setAutoCommit(false);
            final PGId src = PGId.valueOf(commitId);
            updated = !exists(src, cx);
            // NOTE: runs in autoCommit mode to ignore statement failures due to duplicates (?)
            // TODO: if node was node added should we severe existing parent relationships?
            int dstindex = 0;
            final String insert = format(
                    "INSERT INTO %s (src, dst, dstindex) VALUES (ROW(?,?,?), ROW(?,?,?), ?)",
                    EDGES);

            for (ObjectId p : parentIds) {
                final PGId dst = PGId.valueOf(p);
                boolean isDuplicate = false;
                try (PreparedStatement ps = cx.prepareStatement(log(insert, LOG, src, dst))) {
                    src.setArgs(ps, 1);
                    dst.setArgs(ps, 4);
                    ps.setInt(7, dstindex);
                    dstindex++;
                    try {
                        ps.executeUpdate();
                    } catch (SQLException duplicateTuple) {
                        isDuplicate = true;
                    }
                }
                updated = updated || !isDuplicate;
            }
            cx.commit();
            return updated;
        } catch (SQLException e) {
            try {
                cx.rollback();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
            throw new RuntimeException(e);
        }
    }

    void put(Connection cx, Stream<RevCommit> commits) throws SQLException {
        if (Version.V9_5_0.lowerOrEqualTo(this.serverVersion)) {
            putWithUpsert(cx, commits);
        } else {
            putWithoutUpsert(cx, commits);
        }
    }

    /**
     * Bulk put compatible with PG < 9.5, can't use upsert
     * 
     * @throws SQLException
     */
    private void putWithoutUpsert(Connection cx, Stream<RevCommit> commits) throws SQLException {

        Preconditions.checkArgument(!cx.getAutoCommit());

        final Set<RevCommit> uniqueCommits = commits.collect(Collectors.toSet());
        if (uniqueCommits.isEmpty()) {
            return;
        }

        final String tmpTable = "graph_edge_tmp";
        final String tmpTableSql = format(
                "CREATE TEMPORARY TABLE %s (src OBJECTID, dst OBJECTID, dstindex int NOT NULL, PRIMARY KEY (src,dst)) ON COMMIT DROP",
                tmpTable, EDGES);

        // create temporary table. The temporary table is local to the transaction
        try (Statement st = cx.createStatement()) {
            st.execute(log(tmpTableSql, LOG));
        }

        // insert all rows to the tmp table
        final String insert = "INSERT INTO " + tmpTable
                + " (src, dst, dstindex) VALUES (ROW(?,?,?), ROW(?,?,?), ?)";

        PGId src, dst;
        try (PreparedStatement ps = cx.prepareStatement(insert)) {
            for (RevCommit c : uniqueCommits) {
                src = PGId.valueOf(c.getId());
                ImmutableList<ObjectId> parentIds = c.getParentIds();
                for (int parentIndex = 0; parentIndex < parentIds.size(); parentIndex++) {
                    ObjectId parent = parentIds.get(parentIndex);
                    dst = PGId.valueOf(parent);
                    src.setArgs(ps, 1);
                    dst.setArgs(ps, 4);
                    ps.setInt(7, parentIndex);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }

        // copy all new rows to the edges table
        final String insertUniqueSql = "INSERT INTO " + EDGES + "(src,dst,dstindex)\n"//
                + " SELECT DISTINCT src,dst,dstindex FROM " + tmpTable + "\n"//
                + " WHERE NOT EXISTS (\n"//
                + "  SELECT 1 FROM " + EDGES + "\n"//
                + "  WHERE \n" //
                + "   " + tmpTable + ".src = " + EDGES + ".src \n"//
                + "   AND " + tmpTable + ".dst = " + EDGES + ".dst\n"//
                + ")";

        try (Statement st = cx.createStatement()) {
            st.execute(log(insertUniqueSql, LOG));
        }
    }

    /**
     * Bulk put compatible with PG 9.5+, using upsert
     */
    private void putWithUpsert(Connection cx, Stream<RevCommit> commits) throws SQLException {
        // from PG 9.5+
        final String upsert = format(
                "INSERT INTO %s (src, dst, dstindex) VALUES (ROW(?,?,?), ROW(?,?,?), ?) ON CONFLICT DO NOTHING",
                EDGES);

        final Stopwatch sw = LOG.isTraceEnabled() ? Stopwatch.createStarted() : null;

        int count = 0;
        PGId src, dst;
        try (PreparedStatement ps = cx.prepareStatement(upsert)) {
            Iterator<RevCommit> iterator = commits.iterator();
            while (iterator.hasNext()) {
                RevCommit c = iterator.next();
                count++;
                src = PGId.valueOf(c.getId());
                ImmutableList<ObjectId> parentIds = c.getParentIds();
                for (int parentIndex = 0; parentIndex < parentIds.size(); parentIndex++) {
                    ObjectId parent = parentIds.get(parentIndex);
                    dst = PGId.valueOf(parent);
                    src.setArgs(ps, 1);
                    dst.setArgs(ps, 4);
                    ps.setInt(7, parentIndex);
                    ps.addBatch();
                }
            }
            if (count > 0) {
                int[] batchResults = ps.executeBatch();
                if (LOG.isTraceEnabled()) {
                    sw.stop();
                    LOG.trace(String.format("%,d updates to graph in %s: %s", count, sw,
                            Arrays.toString(batchResults)));
                }
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
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
        }
    }

    Iterable<PGId> outgoing(final PGId node) {
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            return outgoing(node, cx);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns all nodes connected to the specified node through a relationship in which the
     * specified node is the "source" of the relationship.
     */
    Iterable<PGId> outgoing(final PGId node, final Connection cx) throws SQLException {
        final String sql = format(
                "SELECT ((dst).h1), ((dst).h2),((dst).h3), dstindex FROM %s WHERE src = CAST(ROW(?,?,?) AS OBJECTID) ORDER BY dstindex",
                EDGES);

        List<PGId> outgoing = new ArrayList<>(2);

        try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, node))) {
            node.setArgs(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PGId dst = PGId.valueOf(rs, 1);
                    int index = rs.getInt(4);
                    outgoing.add(index, dst);
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
            throw new RuntimeException(e);
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
