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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.propagate;
import static java.lang.String.format;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

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
            String statement = sb.toString();
            if (!statement.trim().endsWith(";")) {
                statement += ";";
            }
            log.debug(statement);
        }
        return sql;
    }

    synchronized static DataSource newDataSource(Environment config) {
        DataSource dataSource = DATASOURCE_POOL.acquire(config);
        return dataSource;
    }

    static void closeDataSource(DataSource ds) {
        DATASOURCE_POOL.release(ds);
    }

    static Connection newConnection(DataSource ds) {
        try {
            Connection connection = ds.getConnection();
            return connection;
        } catch (SQLTransientConnectionException e) {
            throw new RepositoryBusyException("No available connections to the repository.", e);
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
        checkNotNull(config);
        checkArgument(config.getRepositoryName() != null, "no repository name provided");

        Optional<Integer> repoPK;
        try (PGConfigDatabase configdb = new PGConfigDatabase(config)) {
            if (config.isRepositorySet()) {
                String configuredName = configdb.get("repo.name").orNull();
                Preconditions.checkState(config.getRepositoryName().equals(configuredName));
                return true;
            } else {
                repoPK = configdb.resolveRepositoryPK(config.getRepositoryName());
                if (repoPK.isPresent()) {
                    if (config.isRepositorySet()) {
                        if (repoPK.get() != config.getRepositoryId()) {
                            String msg = format("The provided primary key for the repository '%s' "
                                    + "does not match the one in the database. Provided: %d, returned: %d. "
                                    + "Check the consistency of the repo.name config property for those repository ids");
                            throw new IllegalStateException(msg);
                        }
                    } else {
                        config.setRepositoryId(repoPK.get());
                    }
                }
                return repoPK.isPresent();
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * List the names of all repositories in the given {@link Environment}.
     * 
     * @param config the environment
     * @return the list of repository names
     */
    public static List<String> listRepos(final Environment config) {
        checkNotNull(config);

        List<String> repoNames = Lists.newLinkedList();
        final DataSource dataSource = PGStorage.newDataSource(config);

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            final String repoNamesView = config.getTables().repositoryNamesView();
            String sql = format("SELECT name FROM %s", repoNamesView);
            try (Statement st = cx.createStatement()) {
                try (ResultSet repos = st.executeQuery(sql)) {
                    while (repos.next()) {
                        repoNames.add(repos.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            throw propagate(e);
        } finally {
            PGStorage.closeDataSource(dataSource);
        }

        return repoNames;
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
        checkNotNull(config);
        checkArgument(config.getRepositoryName() != null, "no repository name provided");
        createTables(config);

        final String argRepoName = config.getRepositoryName();
        if (config.isRepositorySet()) {
            return false;
        }

        final DataSource dataSource = PGStorage.newDataSource(config);

        try (PGConfigDatabase configdb = new PGConfigDatabase(config)) {
            final Optional<Integer> repositoryPK = configdb.resolveRepositoryPK(argRepoName);
            if (repositoryPK.isPresent()) {
                return false;
            }
            try (Connection cx = PGStorage.newConnection(dataSource)) {
                final String reposTable = config.getTables().repositories();
                cx.setAutoCommit(false);
                final int pk;
                try {
                    String sql = format("INSERT INTO %s (created) VALUES (NOW())", reposTable);
                    try (Statement st = cx.createStatement()) {
                        int updCnt = st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
                        checkState(updCnt == 1);
                        try (ResultSet generatedKeys = st.getGeneratedKeys()) {
                            checkState(generatedKeys.next());
                            pk = generatedKeys.getInt(1);
                        }
                        config.setRepositoryId(pk);
                    }
                    cx.commit();
                } catch (SQLException | RuntimeException e) {
                    throw rollbackAndRethrow(cx, e);
                } finally {
                    cx.setAutoCommit(true);
                }

                configdb.put("repo.name", argRepoName);
                checkState(pk == configdb.resolveRepositoryPK(argRepoName)
                        .or(Environment.REPOSITORY_ID_UNSET));
            }
        } catch (SQLException e) {
            throw propagate(e);
        } finally {
            PGStorage.closeDataSource(dataSource);
        }

        checkState(config.isRepositorySet());
        return true;
    }

    public static boolean tableExists(final DataSource dataSource, final String tableName) {
        boolean tableExists = false;
        if (dataSource != null) {
            try (Connection cx = PGStorage.newConnection(dataSource)) {
                DatabaseMetaData md = cx.getMetaData();
                final String schema = PGStorage.schema(tableName);
                final String table = PGStorage.stripSchema(tableName);
                try (ResultSet tables = md.getTables(null, schema, table, null)) {
                    tableExists = tables.next();
                }
            } catch (SQLException e) {
                throw propagate(e);
            }
        }
        return tableExists;
    }

    public static void createTables(final Environment config) {

        final TableNames tables = config.getTables();
        final String reposTable = tables.repositories();
        final String schema = PGStorage.schema(reposTable);
        final String table = PGStorage.stripSchema(reposTable);

        final DataSource dataSource = PGStorage.newDataSource(config);
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            DatabaseMetaData md = cx.getMetaData();
            try (ResultSet rs = md.getTables(null, schema, table, null)) {
                if (rs.next()) {
                    return;
                }
            }
            try {
                // tell postgres to send bytea fields in a more compact format than hex
                // encoding
                cx.setAutoCommit(true);
                PGStorage.run(cx, "SELECT pg_advisory_lock(-1)");
                String sql = String.format("ALTER DATABASE \"%s\" SET bytea_output = 'escape'",
                        config.getDatabaseName());
                try {
                    PGStorage.run(cx, sql);
                } catch (SQLException e) {
                    LOG.warn(String.format(
                            "Unable to run '%s'. User may need more priviledges. This is not fatal, but recommended.",
                            sql), e);
                }
                PGStorage.run(cx, "SELECT pg_advisory_unlock(-1)");

                cx.setAutoCommit(false);

                PGStorage.createObjectIdCompositeType(cx);
                PGStorage.run(cx, "SET constraint_exclusion=ON");

                createRepositoriesTable(cx, tables);
                createConfigTable(cx, tables);
                cx.commit();
                cx.setAutoCommit(false);
                createRefsTable(cx, tables);
                createConflictsTable(cx, tables);
                createBlobsTable(cx, tables);
                createIndexTables(cx, tables);
                createObjectsTables(cx, tables);
                createGraphTables(cx, tables);
                cx.commit();
            } catch (SQLException | RuntimeException e) {
                e.printStackTrace();
                cx.rollback();
                Throwables.propagateIfInstanceOf(e, SQLException.class);
                throw Throwables.propagate(e);
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw propagate(e);
        } finally {
            PGStorage.closeDataSource(dataSource);
        }

    }

    private static void run(final Connection cx, final String sql) throws SQLException {
        // String s = sql;
        // if (!s.endsWith(";")) {
        // s += ";";
        // }
        // System.out.println(s);
        try (Statement st = cx.createStatement()) {
            st.execute(log(sql, LOG));
        }
    }

    private static void createObjectIdCompositeType(Connection cx) throws SQLException {
        final boolean autoCommit = cx.getAutoCommit();
        cx.setAutoCommit(false);

        // There's no CREATE TYPE IF NOT EXIST, so use this trick
        final String func = "-- This function is to create a type if it does not exist since there's no support for IF NOT EXISTS for CREATE TYPE\n"//
                + "CREATE OR REPLACE FUNCTION create_objectid_type() RETURNS integer AS $$\n"//
                + "DECLARE v_exists INTEGER;\n"//

                + "BEGIN\n"//
                + "    SELECT into v_exists (SELECT 1 FROM pg_type WHERE typname = 'objectid');\n"//
                + "    IF v_exists IS NULL THEN\n"//
                + "        CREATE TYPE OBJECTID AS(h1 INTEGER, h2 BIGINT, h3 BIGINT);\n"//
                + "    END IF;\n"//
                + "    RETURN v_exists;\n"//
                + "END;\n"//
                + "$$ LANGUAGE plpgsql;";

        try (Statement st = cx.createStatement()) {
            PGStorage.run(cx, func);
            // Call the function you just created
            PGStorage.run(cx, "SELECT create_objectid_type()");

            // Remove the function you just created
            PGStorage.run(cx, "DROP function create_objectid_type()");
            cx.commit();
            cx.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            cx.rollback();
            throw e;
        }
    }

    private static void createRepositoriesTable(Connection cx, TableNames tables)
            throws SQLException {
        final String repositories = tables.repositories();

        String sql = format("CREATE TABLE %s (repository serial PRIMARY KEY, created TIMESTAMP)",
                repositories);
        run(cx, sql);
        // create an entry for global config to have a matching entry in repositories
        sql = format("INSERT INTO %s (repository, created) VALUES (%d, NOW())", repositories,
                PGConfigDatabase.GLOBAL_KEY);
        run(cx, sql);

    }

    private static void createConfigTable(Connection cx, TableNames tables) throws SQLException {
        final String viewName = tables.repositoryNamesView();
        final String repositories = tables.repositories();
        String configTable = tables.config();
        String sql = format(
                "CREATE TABLE IF NOT EXISTS %s (repository INTEGER, section TEXT, key TEXT, value TEXT,"
                        + " PRIMARY KEY (repository, section, key)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                configTable, tables.repositories());
        run(cx, sql);
        sql = format("CREATE INDEX %s_section_idx ON %s (repository, section)",
                stripSchema(configTable), configTable);
        run(cx, sql);
        try {
            sql = format(
                    "CREATE VIEW %s " + "AS SELECT r.*, c.value AS name FROM "
                            + "%s r INNER JOIN %s c ON r.repository = c.repository WHERE c.section = 'repo' AND c.key = 'name'",
                    viewName, repositories, configTable);
            run(cx, sql);
        } catch (SQLException alreadyExists) {
            // ignore
        }
    }

    private static void createRefsTable(Connection cx, TableNames tables) throws SQLException {
        final String TABLE_STMT = format(
                "CREATE TABLE %s (" + "repository INTEGER, path TEXT, name TEXT, value TEXT, "
                        + "PRIMARY KEY(repository, path, name), "
                        + "FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                tables.refs(), tables.repositories());
        run(cx, TABLE_STMT);
    }

    private static void createConflictsTable(Connection cx, TableNames tables) throws SQLException {
        String conflictsTable = tables.conflicts();
        String sql = format(
                "CREATE TABLE %s (repository INTEGER, namespace TEXT, path TEXT, ancestor bytea, ours bytea NOT NULL, theirs bytea NOT NULL"
                        + ", PRIMARY KEY(repository, namespace, path)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                conflictsTable, tables.repositories());
        run(cx, sql);
    }

    private static void createBlobsTable(Connection cx, TableNames tables) throws SQLException {
        String blobsTable = tables.blobs();
        String sql = format(
                "CREATE TABLE %s (repository INTEGER, namespace TEXT, path TEXT, blob BYTEA"
                        + ", PRIMARY KEY(repository,namespace,path)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                blobsTable, tables.repositories());
        run(cx, sql);
    }

    public static void createIndexTables(Connection cx, TableNames tables) throws SQLException {
        String indexTable = tables.index();
        String sql = format(
                "CREATE TABLE %s (repository INTEGER, treeName TEXT, attributeName TEXT, strategy TEXT, metadata BYTEA"
                        + ", PRIMARY KEY(repository, treeName, attributeName)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                indexTable, tables.repositories());
        run(cx, sql);
        String indexMappings = tables.indexMappings();
        sql = format(
                "CREATE TABLE %s (repository INTEGER, indexId OBJECTID, treeId OBJECTID, indexTreeId OBJECTID"
                        + ", PRIMARY KEY(repository, indexId, treeId)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                indexMappings, tables.repositories());
        run(cx, sql);
        String indexObjects = tables.indexObjects();
        sql = format(OBJECT_TABLE_STMT, indexObjects);
        run(cx, sql);
        createIgnoreDuplicatesRule(cx, indexObjects);
        createObjectTableIndex(cx, indexObjects);
    }

    /**
     * @see #createObjectsTables
     */
    static final String OBJECT_TABLE_STMT = "CREATE TABLE %s (id OBJECTID, object BYTEA) WITHOUT OIDS;";

    /**
     * @see #createObjectChildTable
     */
    static final String CHILD_TABLE_STMT = "CREATE TABLE %s ( ) INHERITS(%s)";

    /**
     * @see #partitionedObjectTableDDL
     */
    static final String PARTITIONED_CHILD_TABLE_STMT = "CREATE TABLE %s"
            + " (id OBJECTID, object BYTEA, CHECK ( ((id).h1) >= %d AND ((id).h1) < %d) ) INHERITS (%s)";

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

    private static String partitionedObjectTableDDL(String tableName, final String parentTable,
            long checkMinValue, long checkMaxValue) {
        return String.format(PARTITIONED_CHILD_TABLE_STMT, tableName, checkMinValue, checkMaxValue,
                parentTable);
    }

    private static void createIgnoreDuplicatesRule(Connection cx, String tableName)
            throws SQLException {
        String rulePrefix = stripSchema(tableName);

        String rule = "CREATE OR REPLACE RULE " + rulePrefix
                + "_ignore_duplicate_inserts AS ON INSERT TO " + tableName
                + " WHERE (EXISTS ( SELECT 1 FROM " + tableName
                + " WHERE ((id).h1) = (NEW.id).h1 AND id = NEW.id))" + " DO INSTEAD NOTHING;";
        run(cx, rule);
    }

    private static void createObjectTableIndex(Connection cx, String tableName)
            throws SQLException {

        String index = String.format("CREATE INDEX %s_objectid_h1_hash ON %s (((id).h1))",
                stripSchema(tableName), tableName);
        run(cx, index);
    }

    private static void createPartitionedChildTables(final Connection cx, final String parentTable)
            throws SQLException {
        final int min = Integer.MIN_VALUE;
        final long max = (long) Integer.MAX_VALUE + 1;
        final int numTables = 16;
        final int step = (int) (((long) max - (long) min) / numTables);

        final String triggerFunction = stripSchema(
                String.format("%s_partitioning_insert_trigger", parentTable));
        StringBuilder funcSql = new StringBuilder(
                String.format("CREATE OR REPLACE FUNCTION %s()\n", triggerFunction));
        funcSql.append("RETURNS TRIGGER AS $$\n");
        funcSql.append("DECLARE\n\n");
        funcSql.append("id objectid;\n");
        funcSql.append("h1 integer;\n");
        funcSql.append("\nBEGIN\n\n");

        funcSql.append("id = NEW.id;\n");
        funcSql.append("-- raise notice 'id : %', id;\n");
        funcSql.append("h1 = id.h1;\n");
        funcSql.append("-- raise notice 'h1 : %', h1;\n");

        long curr = min;
        for (long i = 0; i < numTables; i++) {
            long next = curr + step;
            String tableName = String.format("%s_%d", parentTable, i);
            String sql = partitionedObjectTableDDL(tableName, parentTable, curr, next);

            run(cx, sql);
            createIgnoreDuplicatesRule(cx, tableName);
            createObjectTableIndex(cx, tableName);

            funcSql.append(i == 0 ? "IF" : "ELSIF");
            funcSql.append(" ( h1 >= ").append(curr);
            if (i < numTables - 1) {
                funcSql.append(" AND h1 < ").append(next);
            }
            funcSql.append(" ) THEN\n");
            funcSql.append(String.format("  INSERT INTO %s_%d VALUES (NEW.*);\n", parentTable, i));
            curr = next;
        }
        funcSql.append("END IF;\n");
        funcSql.append("RETURN NULL;\n");
        funcSql.append("END;\n");
        funcSql.append("$$\n");
        funcSql.append("LANGUAGE plpgsql;\n");

        String sql = funcSql.toString();
        run(cx, sql);

        sql = String.format(
                "CREATE TRIGGER %s BEFORE INSERT ON " + "%s FOR EACH ROW EXECUTE PROCEDURE %s()",
                triggerFunction, parentTable, triggerFunction);
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

        final String edges = tables.graphEdges();
        final String properties = tables.graphProperties();
        final String mappings = tables.graphMappings();

        String sql;

        sql = format("CREATE TABLE %s (src OBJECTID, dst OBJECTID, PRIMARY KEY (src,dst))", edges);
        run(cx, sql);

        sql = format("CREATE INDEX %s_src_index ON %s(src)", stripSchema(edges), edges);
        run(cx, sql);

        sql = format("CREATE INDEX %s_dst_index ON %s(dst)", stripSchema(edges), edges);
        run(cx, sql);

        sql = format("CREATE TABLE %s (nid OBJECTID, key VARCHAR, val VARCHAR,"
                + " PRIMARY KEY(nid,key))", properties);
        run(cx, sql);

        sql = format("CREATE TABLE %s (alias OBJECTID PRIMARY KEY, nid OBJECTID)", mappings);
        run(cx, sql);

        sql = format("CREATE INDEX %s_nid_index ON %s(nid)", stripSchema(mappings), mappings);
        run(cx, sql);
    }

    public static boolean deleteRepository(final Environment env) {
        checkArgument(env.getRepositoryName() != null, "No repository name provided");

        final String repositoryName = env.getRepositoryName();
        final int repositoryPK;

        if (env.isRepositorySet()) {
            repositoryPK = env.getRepositoryId();
        } else {
            try (PGConfigDatabase configdb = new PGConfigDatabase(env)) {
                Optional<Integer> pk = configdb.resolveRepositoryPK(repositoryName);
                if (pk.isPresent()) {
                    repositoryPK = pk.get();
                } else {
                    return false;
                }
            } catch (SQLException e) {
                throw Throwables.propagate(e);
            }
        }
        final TableNames tables = env.getTables();
        final String reposTable = tables.repositories();
        boolean deleted = false;

        final String sql = String.format("DELETE FROM %s WHERE repository = ?", reposTable);
        final DataSource ds = PGStorage.newDataSource(env);

        try (Connection cx = PGStorage.newConnection(ds)) {
            cx.setAutoCommit(false);
            try (PreparedStatement st = cx.prepareStatement(log(sql, LOG, repositoryName))) {
                st.setInt(1, repositoryPK);
                int rowCount = st.executeUpdate();
                deleted = rowCount > 0;
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            PGStorage.closeDataSource(ds);
        }

        return Boolean.valueOf(deleted);
    }
}
