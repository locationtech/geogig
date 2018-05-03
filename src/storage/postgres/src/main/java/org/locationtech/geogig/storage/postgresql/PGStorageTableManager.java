package org.locationtech.geogig.storage.postgresql;

import static java.lang.String.format;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public abstract class PGStorageTableManager {

    static final Logger LOG = LoggerFactory.getLogger(PGStorageTableManager.class);

    /**
     * @see PGStorage#createObjectsTables
     */
    static final String OBJECT_TABLE_STMT = "CREATE TABLE %s (id OBJECTID, object BYTEA) WITHOUT OIDS;";

    /**
     * @see PGStorageTableManager#createObjectChildTable
     */
    static final String CHILD_TABLE_STMT = "CREATE TABLE %s ( ) INHERITS(%s)";

    /**
     * @see PGStorage#partitionedObjectTableDDL
     */
    static final String PARTITIONED_CHILD_TABLE_STMT = "CREATE TABLE %s"
            + " (id OBJECTID, object BYTEA, CHECK ( ((id).h1) >= %d AND ((id).h1) < %d) ) INHERITS (%s)";

    public static PGStorageTableManager forVersion(Version serverVersion) {
        if (serverVersion.major < 10) {
            return new PrePg10TableManager();
        }
        return new Pg10TableManager();
    }

    private static class PrePg10TableManager extends PGStorageTableManager {

    }

    /**
     * <ul>
     * <li>Table partitioning: can't do with inheritance
     * <li>Hash indexes
     * <li>Parallel workers
     * </ul>
     */
    private static class Pg10TableManager extends PGStorageTableManager {

        protected @Override void createObjectsTables(Connection cx, TableNames tables)
                throws SQLException {
            tables.setFeaturesPartitions(0);
            createObjectsTables(cx, tables.objects(), tables.commits(), tables.featureTypes(),
                    tables.tags(), tables.trees(), tables.features());
        }

        protected @Override void createObjectTableIndex(Connection cx, String tableName)
                throws SQLException {
            String index = String.format(
                    "CREATE INDEX %s_objectid_h1_hash ON %s USING HASH (((id).h1))",
                    stripSchema(tableName), tableName);
            run(cx, index);
        }

        protected @Override void createPartitionedObjectsTable(final Connection cx,
                String parentTable, TableNames tables) throws SQLException {
            if (true) {
                throw new UnsupportedOperationException();
            }
            // final String tableDDL = "CREATE TABLE %s ( ) INHERITS(%s) PARTITION BY RANGE (
            // ((id).h1) )";
            final String tableDDL = "CREATE TABLE %s ( ) INHERITS(%s) ";
            String sql = format(tableDDL, parentTable, tables.objects());
            run(cx, sql);
            createObjectTableIndex(cx, parentTable);

            //@formatter:off
            /*
            final int min = Integer.MIN_VALUE;
            final long max = (long) Integer.MAX_VALUE + 1;
            final int numTables = 16;
            final int step = (int) (((long) max - (long) min) / numTables);

            parentTable = stripSchema(parentTable);
            long curr = min;
            for (long i = 0; i < numTables; i++) {
                long next = curr + step;
                String tableName = String.format("%s_%d", parentTable, i);
                final String tableDDL = String.format("CREATE TABLE \"%s\" PARTITION OF \"%s\" " + //
                        "FOR VALUES FROM (%d) TO (%d)", tableName, parentTable, curr, next);

                run(cx, tableDDL);
                createIgnoreDuplicatesRule(cx, tableName);
                createObjectTableIndex(cx, tableName);
                curr = next;
            }
            */
            //@formatter:on
        }
    }

    public static String schema(String tableName) {
        int idx = tableName.indexOf('.');
        String schema = "public";
        if (idx > -1) {
            schema = tableName.substring(0, idx);
        }
        return schema;
    }

    public static String stripSchema(String tableName) {
        int idx = tableName.indexOf('.');
        String prefixStripped = tableName;
        if (idx > -1) {
            prefixStripped = tableName.substring(idx + 1);
        }
        return prefixStripped;
    }

    public void createTables(Connection cx, Environment config) throws SQLException {
        final TableNames tables = config.getTables();
        final String reposTable = tables.repositories();
        final String schema = PGStorageTableManager.schema(reposTable);
        final String table = PGStorageTableManager.stripSchema(reposTable);

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
            PGStorageTableManager.run(cx, "SELECT pg_advisory_lock(-1)");
            String sql = String.format("ALTER DATABASE \"%s\" SET bytea_output = 'escape'",
                    config.getDatabaseName());
            try {
                PGStorageTableManager.run(cx, sql);
            } catch (SQLException e) {
                LOG.warn(String.format(
                        "Unable to run '%s'. User may need more priviledges. This is not fatal, but recommended.",
                        sql), e);
            }
            PGStorageTableManager.run(cx, "SELECT pg_advisory_unlock(-1)");

            cx.setAutoCommit(false);

            createObjectIdCompositeType(cx);
            PGStorageTableManager.run(cx, "SET constraint_exclusion=ON");

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
            Throwables.throwIfInstanceOf(e, SQLException.class);
            throw e;
        } finally {
            cx.setAutoCommit(true);
        }
    }

    protected void createGraphTables(Connection cx, TableNames tables) throws SQLException {

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

    protected void createPartitionedObjectsTable(final Connection cx, final String parentTable,
            TableNames tables) throws SQLException {
        final int min = Integer.MIN_VALUE;
        final long max = (long) Integer.MAX_VALUE + 1;
        final int numTables = 16;
        final int step = (int) (((long) max - (long) min) / numTables);

        createObjectChildTable(cx, parentTable, tables.objects());

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

    protected void createObjectTableIndex(Connection cx, String tableName) throws SQLException {
        String index = String.format("CREATE INDEX %s_objectid_h1_hash ON %s (((id).h1))",
                stripSchema(tableName), tableName);
        run(cx, index);
    }

    protected void createIgnoreDuplicatesRule(Connection cx, String tableName) throws SQLException {
        String rulePrefix = stripSchema(tableName);

        String rule = "CREATE OR REPLACE RULE " + rulePrefix
                + "_ignore_duplicate_inserts AS ON INSERT TO " + tableName
                + " WHERE (EXISTS ( SELECT 1 FROM " + tableName
                + " WHERE ((id).h1) = (NEW.id).h1 AND id = NEW.id))" + " DO INSTEAD NOTHING;";
        run(cx, rule);
    }

    protected String partitionedObjectTableDDL(String tableName, final String parentTable,
            long checkMinValue, long checkMaxValue) {
        return String.format(PARTITIONED_CHILD_TABLE_STMT, tableName, checkMinValue, checkMaxValue,
                parentTable);
    }

    protected void createObjectChildTable(Connection cx, String tableName, String parentTable)
            throws SQLException {

        String sql = format(CHILD_TABLE_STMT, tableName, parentTable);
        run(cx, sql);
    }

    /**
     * TODO: compare performance in case we also created indexes for the "abstract" tables (object
     * and object_feature), have read somewhere that otherwise you'll get sequential scans from the
     * query planner that can be avoided.
     */
    protected void createObjectsTables(Connection cx, TableNames tables) throws SQLException {
        createObjectsTables(cx, tables.objects(), tables.commits(), tables.featureTypes(),
                tables.tags(), tables.trees());
        createPartitionedObjectsTable(cx, tables.features(), tables);
    }

    protected void createObjectsTables(Connection cx, String objectsTable, String... childTables)
            throws SQLException {

        String sql = format(OBJECT_TABLE_STMT, objectsTable);
        run(cx, sql);

        for (String tableName : childTables) {
            createObjectChildTable(cx, tableName, objectsTable);
            createIgnoreDuplicatesRule(cx, tableName);
            createObjectTableIndex(cx, tableName);
        }
    }

    protected void createIndexTables(Connection cx, TableNames tables) throws SQLException {
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
        sql = format(PGStorageTableManager.OBJECT_TABLE_STMT, indexObjects);
        run(cx, sql);
        createIgnoreDuplicatesRule(cx, indexObjects);
        createObjectTableIndex(cx, indexObjects);
    }

    protected void createBlobsTable(Connection cx, TableNames tables) throws SQLException {
        String blobsTable = tables.blobs();
        String sql = format(
                "CREATE TABLE %s (repository INTEGER, namespace TEXT, path TEXT, blob BYTEA"
                        + ", PRIMARY KEY(repository,namespace,path)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                blobsTable, tables.repositories());
        run(cx, sql);
    }

    protected void createConflictsTable(Connection cx, TableNames tables) throws SQLException {
        String conflictsTable = tables.conflicts();
        String sql = format(
                "CREATE TABLE %s (repository INTEGER, namespace TEXT, path TEXT, ancestor bytea, ours bytea NOT NULL, theirs bytea NOT NULL"
                        + ", PRIMARY KEY(repository, namespace, path)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                conflictsTable, tables.repositories());
        run(cx, sql);
    }

    protected void createRefsTable(Connection cx, TableNames tables) throws SQLException {
        final String TABLE_STMT = format(
                "CREATE TABLE %s (" + "repository INTEGER, path TEXT, name TEXT, value TEXT, "
                        + "PRIMARY KEY(repository, path, name), "
                        + "FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE)",
                tables.refs(), tables.repositories());
        run(cx, TABLE_STMT);
    }

    protected void createConfigTable(Connection cx, TableNames tables) throws SQLException {
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
            sql = format("CREATE VIEW %s " + "AS SELECT r.*, c.value AS name FROM "
                    + "%s r INNER JOIN %s c ON r.repository = c.repository WHERE c.section = 'repo' AND c.key = 'name'",
                    viewName, repositories, configTable);
            run(cx, sql);
        } catch (SQLException alreadyExists) {
            // ignore
        }
    }

    protected void createRepositoriesTable(Connection cx, TableNames tables) throws SQLException {
        final String repositories = tables.repositories();

        String sql = format("CREATE TABLE %s (repository serial PRIMARY KEY, created TIMESTAMP)",
                repositories);
        run(cx, sql);
        // create an entry for global config to have a matching entry in repositories
        sql = format("INSERT INTO %s (repository, created) VALUES (%d, NOW())", repositories,
                PGConfigDatabase.GLOBAL_KEY);
        run(cx, sql);

    }

    protected void createObjectIdCompositeType(Connection cx) throws SQLException {
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
            run(cx, func);
            // Call the function you just created
            run(cx, "SELECT create_objectid_type()");

            // Remove the function you just created
            run(cx, "DROP function create_objectid_type()");
            cx.commit();
            cx.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            cx.rollback();
            throw e;
        }
    }

    static void run(final Connection cx, final String sql) throws SQLException {
        String s = sql;
        if (!s.endsWith(";")) {
            s += ";";
        }
        System.out.println(s);
        try (Statement st = cx.createStatement()) {
            st.execute(PGStorage.log(sql, PGStorage.LOG));
        }
    }

}
