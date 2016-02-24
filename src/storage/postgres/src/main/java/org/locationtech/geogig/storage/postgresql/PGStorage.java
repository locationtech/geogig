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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * Utility class for PostgreSQL storage.
 */
public class PGStorage {

    private static final Logger LOG = LoggerFactory.getLogger(PGStorage.class);

    static Map<Connection, Map<String, PreparedStatement>> OPEN_STATEMENTS = new IdentityHashMap<>();

    private static final DataSourceManager DATASOURCE_POOL = new DataSourceManager();

    /**
     * Logs a (prepared) sql statement.
     * 
     * @param sql Base sql to log.
     * @param log The logger object.
     * @param args Optional arguments to the statement.
     * 
     * @return The original statement.
     */
    static String log(String sql, Logger log, Object... args) {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder(sql);
            if (args.length > 0) {
                sb.append(";");
                for (int i = 0; i < args.length; i++) {
                    sb.append(i).append("=").append(args[i]).append(", ");
                }
                sb.setLength(sb.length() - 2);
            }
            log.debug(sb.toString());
        }
        return sql;
    }

    synchronized static DataSource newDataSource(Environment config) {
        DataSource dataSource = DATASOURCE_POOL.acquire(config.connectionConfig);
        return dataSource;
    }

    static void closeDataSource(DataSource ds) {
        DATASOURCE_POOL.release(ds);
    }

    static Connection newConnection(DataSource ds) {
        try {
            Connection connection = ds.getConnection();
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("Unable to obatain connection: " + e.getMessage(), e);
        }
    }

    static SQLException rollbackAndRethrow(Connection c, Exception e) throws SQLException {
        c.rollback();
        if (e instanceof SQLException) {
            throw (SQLException) e;
        }
        throw Throwables.propagate(e);
    }

    public static boolean repoExists(final Environment config) throws IllegalArgumentException {
        Preconditions.checkNotNull(config);
        Preconditions.checkArgument(config.repositoryId != null, "no repository id provided");
        DataSource ds = PGStorage.newDataSource(config);
        try {
            Boolean exists = new DbOp<Boolean>() {
                @Override
                protected Boolean doRun(Connection cx) throws IOException, SQLException {
                    final String reposTable = config.getTables().repositories();
                    DatabaseMetaData md = cx.getMetaData();

                    final String schema = schema(reposTable);
                    final String table = stripSchema(reposTable);
                    try (ResultSet rs = md.getTables(null, schema, table, null)) {
                        if (!rs.next()) {
                            return false;
                        }
                    }
                    String sql = String.format(
                            "SELECT TRUE WHERE EXISTS(SELECT 1 FROM %s WHERE repository = ?)",
                            reposTable);
                    try (PreparedStatement st = cx.prepareStatement(sql)) {
                        String repositoryId = config.repositoryId;
                        st.setString(1, repositoryId);
                        try (ResultSet rs = st.executeQuery()) {
                            return rs.next();
                        }
                    }
                }
            }.run(ds);
            return exists;
        } finally {
            PGStorage.closeDataSource(ds);
        }
    }

    /**
     * Initializes all the tables as given by the names in {@link Environment#getTables()
     * config.getTables()} if need be, and creates an entry in the {@link TableNames#repositories()
     * repsitories} table with the repository id given by {@link Environment#repositoryId
     * config.repositoryId}, returning true if the entry was created and false if it already
     * existed.
     * 
     * @return {@code true} if the repository was created, {@code false} if it already exists
     */
    public static boolean createNewRepo(final Environment config) throws IllegalArgumentException {
        Preconditions.checkNotNull(config);
        Preconditions.checkArgument(config.repositoryId != null, "no repository id provided");
        checkArgument(!PGConfigDatabase.GLOBAL_KEY.equals(config.repositoryId),
                "%s is a reserved key. No repo can be named like that.",
                PGConfigDatabase.GLOBAL_KEY);
        createTables(config);

        DataSource ds = PGStorage.newDataSource(config);
        try {
            Boolean created = new DbOp<Boolean>() {
                @Override
                protected Boolean doRun(Connection cx) throws IOException, SQLException {

                    final String reposTable = config.getTables().repositories();
                    String sql = String.format(
                            "SELECT TRUE WHERE EXISTS(SELECT 1 FROM %s WHERE repository = ?)",
                            reposTable);

                    final String repositoryId = config.repositoryId;
                    final boolean exists;
                    cx.setAutoCommit(false);
                    try {
                        try (PreparedStatement st = cx.prepareStatement(sql)) {
                            st.setString(1, repositoryId);
                            try (ResultSet rs = st.executeQuery()) {
                                exists = rs.next();
                            }
                        }
                        if (!exists) {
                            sql = format("INSERT INTO %s (repository, created) VALUES (?, NOW())",
                                    reposTable);
                            try (PreparedStatement st = cx.prepareStatement(sql)) {
                                st.setString(1, repositoryId);
                                int updCnt = st.executeUpdate();
                                Preconditions.checkState(updCnt == 1);
                            }
                        }
                        cx.commit();
                    } catch (SQLException | RuntimeException e) {
                        cx.rollback();
                        Throwables.propagateIfInstanceOf(e, SQLException.class);
                        throw Throwables.propagate(e);
                    }
                    return !exists;
                }
            }.run(ds);
            return created;
        } finally {
            PGStorage.closeDataSource(ds);
        }
    }

    public static void createTables(final Environment config) {

        DataSource ds = PGStorage.newDataSource(config);
        try {
            new DbOp<Void>() {

                @Override
                protected Void doRun(final Connection cx) throws SQLException {
                    DatabaseMetaData md = cx.getMetaData();
                    final TableNames tables = config.getTables();
                    final String reposTable = tables.repositories();
                    final String schema = PGStorage.schema(reposTable);
                    final String table = PGStorage.stripSchema(reposTable);
                    try (ResultSet rs = md.getTables(null, schema, table, null)) {
                        if (rs.next()) {
                            return null;
                        }
                    }
                    cx.setAutoCommit(false);
                    try {
                        // tell postgres to send bytea fields in a more compact format than hex
                        // encoding
                        String sql = String.format(
                                "ALTER DATABASE \"%s\" SET bytea_output = 'escape'",
                                config.getDatabaseName());
                        PGStorage.run(cx, sql);

                        PGStorage.run(cx, "SET constraint_exclusion=ON");

                        createConfigTable(cx, tables);
                        cx.commit();
                        cx.setAutoCommit(false);
                        createRefsTable(cx, tables);
                        createConflictsTable(cx, tables);
                        createBlobsTable(cx, tables);
                        createObjectsTables(cx, tables);
                        createGraphTables(cx, tables);
                        cx.commit();
                    } catch (SQLException | RuntimeException e) {
                        cx.rollback();
                        Throwables.propagateIfInstanceOf(e, SQLException.class);
                        throw Throwables.propagate(e);
                    }
                    return null;
                }
            }.run(ds);
        } finally {
            PGStorage.closeDataSource(ds);
        }

    }

    private static void run(Connection cx, String sql) throws SQLException {
        // System.err.println(sql + ";");
        try (Statement st = cx.createStatement()) {
            st.execute(log(sql, LOG));
        }
    }

    private static void createRepositoriesTable(Connection cx, TableNames tables)
            throws SQLException {
        String sql = format(
                "CREATE TABLE %s (repository TEXT PRIMARY KEY, created TIMESTAMP, lock_id SERIAL);"
                + "INSERT INTO %s (repository, created) VALUES ( '" + PGConfigDatabase.GLOBAL_KEY
                + "', NOW())", tables.repositories(), tables.repositories());
        run(cx, sql);
    }

    private static void createConfigTable(Connection cx, TableNames tables) throws SQLException {

        createRepositoriesTable(cx, tables);

        String configTable = tables.config();
        String sql = format(
                "CREATE TABLE IF NOT EXISTS %s (repository TEXT, section TEXT, key TEXT, value TEXT,"
                        + " PRIMARY KEY (repository, section, key)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                configTable, tables.repositories());
        run(cx, sql);
        sql = format("CREATE INDEX %s_section_idx ON %s (repository, section)",
                stripSchema(configTable), configTable);
        try {
            run(cx, sql);
        } catch (SQLException alreadyExists) {
            // ignore
        }
    }

    private static void createRefsTable(Connection cx, TableNames tables) throws SQLException {
        final String TABLE_STMT = format("CREATE TABLE %s ("
                + "repository TEXT, path TEXT, name TEXT, value TEXT, "
                + "PRIMARY KEY(repository, path, name), "
                + "FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                tables.refs(), tables.repositories());
        run(cx, TABLE_STMT);
    }

    private static void createConflictsTable(Connection cx, TableNames tables) throws SQLException {
        String conflictsTable = tables.conflicts();
        String sql = format(
                "CREATE TABLE %s (repository TEXT, namespace TEXT, path TEXT, conflict TEXT"
                        + ", PRIMARY KEY(repository, namespace, path)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                conflictsTable, tables.repositories());
        run(cx, sql);
    }

    private static void createBlobsTable(Connection cx, TableNames tables) throws SQLException {
        String blobsTable = tables.blobs();
        String sql = format(
                "CREATE TABLE %s (repository TEXT, namespace TEXT, path TEXT, blob BYTEA"
                        + ", PRIMARY KEY(repository,namespace,path)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                blobsTable, tables.repositories());
        run(cx, sql);
    }

    // static final String OBJECT_TABLE_STMT =
    // "CREATE TABLE %s (hash1 INTEGER, hash2 TEXT, object BYTEA, PRIMARY KEY(hash1, hash2))";
    static final String OBJECT_TABLE_STMT = "CREATE TABLE %s (hash1 INTEGER, hash2 BIGINT, hash3 BIGINT, object BYTEA) WITHOUT OIDS";

    static final String CHILD_TABLE_STMT = "CREATE TABLE %s ( ) INHERITS(%s)";

    /**
     * TODO: compare performance in case we also created indexes for the "abstract" tables (object
     * and object_feature), have read somewhere that otherwise you'll get sequential scans from the
     * query planner that can be avoided.
     */
    private static void createObjectsTables(Connection cx, TableNames tables) throws SQLException {
        String objectsTable = tables.objects();
        String sql = format(OBJECT_TABLE_STMT, objectsTable);
        run(cx, sql);
        // createForbiddenInsertsToAbstractTableRule(cx, objectsTable);

        List<String> childTables = ImmutableList.of(tables.commits(), tables.featureTypes(),
                tables.tags(), tables.trees());
        for (String tableName : childTables) {
            createObjectChildTable(cx, tableName, objectsTable);
            createIgnoreDuplicatesRule(cx, tableName);
            createObjectTableIndex(cx, tableName);
        }

        createObjectChildTable(cx, tables.features(), objectsTable);
        // createForbiddenInsertsToAbstractTableRule(cx, tables.features());

        createPartitionedChildTables(cx, tables.features());
    }

    private static void createObjectChildTable(Connection cx, String tableName, String parentTable)
            throws SQLException {

        String sql = format(CHILD_TABLE_STMT, tableName, parentTable);
        run(cx, sql);
    }

    private static void createIgnoreDuplicatesRule(Connection cx, String tableName)
            throws SQLException {
        String rulePrefix = stripSchema(tableName);
        String rule = "CREATE OR REPLACE RULE " + rulePrefix
                + "_ignore_duplicate_inserts AS ON INSERT TO " + tableName
                + " WHERE (EXISTS ( SELECT 1 FROM " + tableName + " WHERE " + tableName
                + ".hash1 = NEW.hash1 AND " + tableName + ".hash2 = NEW.hash2 AND " + tableName
                + ".hash3 = NEW.hash3)) DO INSTEAD NOTHING";
        run(cx, rule);
    }

    private static void createObjectTableIndex(Connection cx, String tableName) throws SQLException {

        String index = String.format("CREATE INDEX %s_hash1 ON %s USING HASH(hash1)",
                stripSchema(tableName), tableName);
        run(cx, index);
        // index = String.format("CREATE INDEX %s_hash2 ON %s USING HASH(hash2)",
        // stripSchema(tableName), tableName);
        // run(cx, index);
    }

    private static void createPartitionedChildTables(final Connection cx, final String parentTable)
            throws SQLException {
        final int min = Integer.MIN_VALUE;
        final long max = (long) Integer.MAX_VALUE + 1;
        final int numTables = 16;
        final int step = (int) (((long) max - (long) min) / numTables);

        final String triggerFunction = stripSchema(String.format("%s_partitioning_insert_trigger",
                parentTable));
        StringBuilder f = new StringBuilder(String.format("CREATE OR REPLACE FUNCTION %s()\n",
                triggerFunction));
        f.append("RETURNS TRIGGER AS $$\n");
        f.append("BEGIN\n");
        long curr = min;
        for (long i = 0; i < numTables; i++) {
            long next = curr + step;
            String tableName = String.format("%s_%d", parentTable, i);
            String sql = String.format("CREATE TABLE %s"
                    + " ( CHECK (hash1 >= %d AND hash1 < %d) ) INHERITS (%s)", tableName, curr,
                    next, parentTable);

            run(cx, sql);
            createIgnoreDuplicatesRule(cx, tableName);
            createObjectTableIndex(cx, tableName);

            f.append(i == 0 ? "IF" : "ELSIF");
            f.append(" ( NEW.hash1 >= ").append(curr);
            if (i < numTables - 1) {
                f.append(" AND NEW.hash1 < ").append(next);
            }
            f.append(" ) THEN\n");
            f.append(String.format("  INSERT INTO %s_%d VALUES (NEW.*);\n", parentTable, i));
            curr = next;
        }
        f.append("END IF;\n");
        f.append("RETURN NULL;\n");
        f.append("END;\n");
        f.append("$$\n");
        f.append("LANGUAGE plpgsql;\n");

        String sql = f.toString();
        run(cx, sql);

        sql = String.format("CREATE TRIGGER %s BEFORE INSERT ON "
                + "%s FOR EACH ROW EXECUTE PROCEDURE %s()", triggerFunction, parentTable,
                triggerFunction);
        run(cx, sql);
    }

    public static String stripSchema(String tableName) {
        int idx = tableName.indexOf('.');
        String prefixStripped = tableName;
        if (idx > -1) {
            prefixStripped = tableName.substring(idx + 1);
        }
        return prefixStripped;
    }

    public static String schema(String tableName) {
        int idx = tableName.indexOf('.');
        String schema = "public";
        if (idx > -1) {
            schema = tableName.substring(0, idx);
        }
        return schema;
    }

    private static void createGraphTables(Connection cx, TableNames tables) throws SQLException {

        final String nodes = tables.graphNodes();
        final String edges = tables.graphEdges();
        final String properties = tables.graphProperties();
        final String mappings = tables.graphMappings();

        String sql = format("CREATE TABLE %s (id VARCHAR(40) PRIMARY KEY)", nodes);
        run(cx, sql);

        sql = format("CREATE TABLE %s (src VARCHAR(40), dst VARCHAR(40), PRIMARY KEY (src,dst))",
                edges);
        run(cx, sql);

        sql = format("CREATE INDEX %s_src_index ON %s(src)", stripSchema(edges), edges);
        run(cx, sql);

        sql = format("CREATE INDEX %s_dst_index ON %s(dst)", stripSchema(edges), edges);
        run(cx, sql);

        sql = format("CREATE TABLE %s (nid VARCHAR(40), key VARCHAR, val VARCHAR,"
                + " PRIMARY KEY(nid,key))", properties);
        run(cx, sql);

        sql = format("CREATE TABLE %s (alias VARCHAR PRIMARY KEY, nid VARCHAR(40))", mappings);
        run(cx, sql);

        sql = format("CREATE INDEX %s_nid_index ON %s(nid)", stripSchema(mappings), mappings);
        run(cx, sql);
    }
}
