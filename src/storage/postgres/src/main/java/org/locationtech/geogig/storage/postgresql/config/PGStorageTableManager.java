package org.locationtech.geogig.storage.postgresql.config;

import static java.lang.String.format;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.porcelain.VersionInfo;
import org.locationtech.geogig.porcelain.VersionOp;
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
    static final String CHILD_TABLE_STMT = "CREATE TABLE %s ( ) INHERITS(%s);";

    /**
     * @see PGStorage#partitionedObjectTableDDL
     */
    static final String PARTITIONED_CHILD_TABLE_STMT = "CREATE TABLE %s"
            + " (id OBJECTID, object BYTEA, CHECK ( ((id).h1) >= %d AND ((id).h1) < %d) ) INHERITS (%s);";

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

        // protected @Override void createObjectsTables(List<String> ddl, TableNames
        // tables) {
        // // tables.setFeaturesPartitions(0);
        // createObjectsTables(ddl, tables.objects(), tables.commits(),
        // tables.featureTypes(),
        // tables.tags(), tables.trees(), tables.features());
        // }

        protected @Override void createObjectTableIndex(List<String> ddl, String tableName) {
            String index = format("CREATE INDEX %s_objectid_h1_hash ON %s USING HASH (((id).h1));",
                    stripSchema(tableName), tableName);
            ddl.add(index);
        }

        // protected @Override void createPartitionedObjectsTable(List<String> ddl,
        // String
        // parentTable,
        // TableNames tables) {
        // if (true) {
        // throw new UnsupportedOperationException();
        // }
        // // final String tableDDL = "CREATE TABLE %s ( ) INHERITS(%s) PARTITION BY
        // RANGE (
        // // ((id).h1) )";
        // final String tableDDL = "CREATE TABLE %s ( ) INHERITS(%s) ";
        // String sql = format(tableDDL, parentTable, tables.objects());
        // ddl.add(sql);
        // createObjectTableIndex(ddl, parentTable);
        //
//            //@formatter:off
//            /*
//            final int min = Integer.MIN_VALUE;
//            final long max = (long) Integer.MAX_VALUE + 1;
//            final int numTables = 16;
//            final int step = (int) (((long) max - (long) min) / numTables);
//
//            parentTable = stripSchema(parentTable);
//            long curr = min;
//            for (long i = 0; i < numTables; i++) {
//                long next = curr + step;
//                String tableName = format("%s_%d", parentTable, i);
//                final String tableDDL = format("CREATE TABLE \"%s\" PARTITION OF \"%s\" " + //
//                        "FOR VALUES FROM (%d) TO (%d)", tableName, parentTable, curr, next);
//
//                run(cx, tableDDL);
//                createIgnoreDuplicatesRule(cx, tableName);
//                createObjectTableIndex(cx, tableName);
//                curr = next;
//            }
//            */
//            //@formatter:on
        // }
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

    public List<String> createDDL(Environment config) {
        final TableNames tables = config.getTables();
        final String databaseName = config.getDatabaseName();
        return createDDL(databaseName, tables);
    }

    public List<String> createDDL(final String databaseName, final TableNames tables) {
        List<String> ddl = new ArrayList<>();

        ddl.add("-- tell postgres to send bytea fields in a more compact format than hex encoding");
        ddl.add("SELECT pg_advisory_lock(-1);");
        String sql = format("do language plpgsql\n"//
                + "$$\n"//
                + "begin\n"//
                + " alter database %s SET bytea_output = 'escape'; \n"//
                + " exception when others then raise notice 'Unable to set bytea_output to escape';\n"//
                + "end;\n"//
                + "$$;", //

                databaseName);
        ddl.add(sql);
        ddl.add("SELECT pg_advisory_unlock(-1);");
        ddl.add("SET constraint_exclusion=ON;");

        createMetadata(ddl, tables);
        createObjectIdCompositeType(ddl);
        createRepositoriesTable(ddl, tables);
        createConfigTable(ddl, tables);

        createRefsTable(ddl, tables);
        createConflictsTable(ddl, tables);
        createBlobsTable(ddl, tables);
        createIndexTables(ddl, tables);
        createObjectsTables(ddl, tables);
        createGraphTables(ddl, tables);
        return ddl;
    }

    public void createMetadata(List<String> ddl, TableNames tables) {
        VersionInfo productVersion = VersionOp.get();

        final String table = tables.metadata();
        int latestVersion = getLatestSchemaVersion();
        ddl.add(format("CREATE TABLE %s (key TEXT PRIMARY KEY, value TEXT, description TEXT);",
                table));
        ddl.add(format("INSERT INTO %s (key, value) VALUES ('geogig.version', '%s');", table,
                productVersion.getProjectVersion()));
        ddl.add(format("INSERT INTO %s (key, value) VALUES ('geogig.commit-id', '%s');", table,
                productVersion.getCommitId()));
        ddl.add(format("INSERT INTO %s (key, value) VALUES ('schema.version', '%s');", table,
                latestVersion));
        ddl.add(format("INSERT INTO %s (key, value) VALUES ('schema.features.partitions', '16');",
                table));
        // ddl.add(format(
        // "insert into %s (key, value) values ('schema.databse-version', '10');",
        // table));
    }

    public int getLatestSchemaVersion() {
        return 1;
    }

    public int getSchemaVersion(Connection cx, TableNames tables) throws SQLException {
        String tableName = tables.metadata();
        if (!PGStorage.tableExists(cx, tableName)) {
            return 0;
        } else {
            try (Statement st = cx.createStatement()) {
                String sql = format("select value from %s where key = 'schema.version'", tableName);
                try (ResultSet rs = st.executeQuery(sql)) {
                    if (rs.next()) {
                        String sv = rs.getString(1);
                        return Integer.parseInt(sv);
                    }
                }
            }
        }
        throw new IllegalStateException(
                "Table " + tableName + " exists but has no schema.version entry");
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
        final List<String> ddl = createDDL(config);
        try {
            cx.setAutoCommit(false);
            runScript(cx, ddl);
            cx.commit();
        } catch (SQLException | RuntimeException e) {
            cx.rollback();
            Throwables.propagateIfInstanceOf(e, SQLException.class);
            throw e;
        } finally {
            cx.setAutoCommit(true);
        }
    }

    public void runScript(Connection cx, final List<String> ddl) throws SQLException {
        for (String ddlStatement : ddl) {
            if (ddlStatement.trim().startsWith("--")) {
                continue;
            }
            run(cx, ddlStatement);
        }
    }

    protected void createGraphTables(List<String> ddl, TableNames tables) {

        final String edges = tables.graphEdges();
        final String properties = tables.graphProperties();
        final String mappings = tables.graphMappings();

        String sql;

        sql = format(
                "CREATE TABLE %s (src OBJECTID, dst OBJECTID, dstindex int NOT NULL, PRIMARY KEY (src,dst));",
                edges);
        ddl.add(sql);

        sql = format("CREATE INDEX %s_src_index ON %s(src);", stripSchema(edges), edges);
        ddl.add(sql);

        sql = format("CREATE INDEX %s_dst_index ON %s(dst);", stripSchema(edges), edges);
        ddl.add(sql);

        sql = format("CREATE TABLE %s (nid OBJECTID, key VARCHAR, val VARCHAR,"
                + " PRIMARY KEY(nid,key));", properties);
        ddl.add(sql);

        sql = format("CREATE TABLE %s (alias OBJECTID PRIMARY KEY, nid OBJECTID);", mappings);
        ddl.add(sql);

        sql = format("CREATE INDEX %s_nid_index ON %s(nid);", stripSchema(mappings), mappings);
        ddl.add(sql);
    }

    protected void createPartitionedObjectsTable(List<String> ddl, final String parentTable,
            TableNames tables) {
        final int min = Integer.MIN_VALUE;
        final long max = (long) Integer.MAX_VALUE + 1;
        final int numTables = 16;
        final int step = (int) (((long) max - (long) min) / numTables);

        createObjectChildTable(ddl, parentTable, tables.objects());

        final String triggerFunction = stripSchema(
                format("%s_partitioning_insert_trigger", parentTable));
        StringBuilder funcSql = new StringBuilder(
                format("CREATE OR REPLACE FUNCTION %s()\n", triggerFunction));
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
            String tableName = format("%s_%d", parentTable, i);
            String sql = partitionedObjectTableDDL(tableName, parentTable, curr, next);

            ddl.add(sql);

            createIgnoreDuplicatesRule(ddl, tableName);
            createObjectTableIndex(ddl, tableName);

            funcSql.append(i == 0 ? "IF" : "ELSIF");
            funcSql.append(" ( h1 >= ").append(curr);
            if (i < numTables - 1) {
                funcSql.append(" AND h1 < ").append(next);
            }
            funcSql.append(" ) THEN\n");
            funcSql.append(format("  INSERT INTO %s_%d VALUES (NEW.*);\n", parentTable, i));
            curr = next;
        }
        funcSql.append("END IF;\n");
        funcSql.append("RETURN NULL;\n");
        funcSql.append("END;\n");
        funcSql.append("$$\n");
        funcSql.append("LANGUAGE plpgsql;\n");

        String sql = funcSql.toString();
        ddl.add(sql);

        sql = format(
                "CREATE TRIGGER %s BEFORE INSERT ON " + "%s FOR EACH ROW EXECUTE PROCEDURE %s();",
                triggerFunction, parentTable, triggerFunction);
        ddl.add(sql);
    }

    protected void createObjectTableIndex(List<String> ddl, String tableName) {
        String index = format("CREATE INDEX %s_objectid_h1_hash ON %s (((id).h1));",
                stripSchema(tableName), tableName);
        ddl.add(index);
    }

    protected void createIgnoreDuplicatesRule(List<String> ddl, String tableName) {
        String rulePrefix = stripSchema(tableName);

        String rule = "CREATE OR REPLACE RULE " + rulePrefix
                + "_ignore_duplicate_inserts AS ON INSERT TO " + tableName
                + " WHERE (EXISTS ( SELECT 1 FROM " + tableName
                + " WHERE ((id).h1) = (NEW.id).h1 AND id = NEW.id))" + " DO INSTEAD NOTHING;";
        ddl.add(rule);
    }

    protected String partitionedObjectTableDDL(String tableName, final String parentTable,
            long checkMinValue, long checkMaxValue) {
        return format(PARTITIONED_CHILD_TABLE_STMT, tableName, checkMinValue, checkMaxValue,
                parentTable);
    }

    protected void createObjectChildTable(List<String> ddl, String tableName, String parentTable) {

        String sql = format(CHILD_TABLE_STMT, tableName, parentTable);
        ddl.add(sql);
    }

    /**
     * TODO: compare performance in case we also created indexes for the "abstract" tables (object
     * and object_feature), have read somewhere that otherwise you'll get sequential scans from the
     * query planner that can be avoided.
     */
    protected void createObjectsTables(List<String> ddl, TableNames tables) {
        createObjectsTables(ddl, tables.objects(), tables.commits(), tables.featureTypes(),
                tables.tags(), tables.trees());
        createPartitionedObjectsTable(ddl, tables.features(), tables);
    }

    protected void createObjectsTables(List<String> ddl, String objectsTable,
            String... childTables) {

        String sql = format(OBJECT_TABLE_STMT, objectsTable);
        ddl.add(sql);

        for (String tableName : childTables) {
            createObjectChildTable(ddl, tableName, objectsTable);
            createIgnoreDuplicatesRule(ddl, tableName);
            createObjectTableIndex(ddl, tableName);
        }
    }

    protected void createIndexTables(List<String> ddl, TableNames tables) {
        String indexTable = tables.index();
        String sql = format(
                "CREATE TABLE %s (repository INTEGER, treeName TEXT, attributeName TEXT, strategy TEXT, metadata BYTEA"
                        + ", PRIMARY KEY(repository, treeName, attributeName)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE);",
                indexTable, tables.repositories());
        ddl.add(sql);
        String indexMappings = tables.indexMappings();
        sql = format(
                "CREATE TABLE %s (repository INTEGER, indexId OBJECTID, treeId OBJECTID, indexTreeId OBJECTID"
                        + ", PRIMARY KEY(repository, indexId, treeId)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE);",
                indexMappings, tables.repositories());
        ddl.add(sql);
        String indexObjects = tables.indexObjects();
        sql = format(PGStorageTableManager.OBJECT_TABLE_STMT, indexObjects);
        ddl.add(sql);
        createIgnoreDuplicatesRule(ddl, indexObjects);
        createObjectTableIndex(ddl, indexObjects);
    }

    protected void createBlobsTable(List<String> ddl, TableNames tables) {
        String blobsTable = tables.blobs();
        String sql = format(
                "CREATE TABLE %s (repository INTEGER, namespace TEXT, path TEXT, blob BYTEA"
                        + ", PRIMARY KEY(repository,namespace,path)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE);",
                blobsTable, tables.repositories());
        ddl.add(sql);
    }

    protected void createConflictsTable(List<String> ddl, TableNames tables) {
        String conflictsTable = tables.conflicts();
        String sql = format(
                "CREATE TABLE %s (repository INTEGER, namespace TEXT, path TEXT, ancestor bytea, ours bytea NOT NULL, theirs bytea NOT NULL"
                        + ", PRIMARY KEY(repository, namespace, path)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE);",
                conflictsTable, tables.repositories());
        ddl.add(sql);
    }

    protected void createRefsTable(List<String> ddl, TableNames tables) {
        final String TABLE_STMT = format(
                "CREATE TABLE %s (" + "repository INTEGER, path TEXT, name TEXT, value TEXT, "
                        + "PRIMARY KEY(repository, path, name), "
                        + "FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE);",
                tables.refs(), tables.repositories());
        ddl.add(TABLE_STMT);
    }

    protected void createConfigTable(List<String> ddl, TableNames tables) {
        final String viewName = tables.repositoryNamesView();
        final String repositories = tables.repositories();
        String configTable = tables.config();
        String sql = format(
                "CREATE TABLE IF NOT EXISTS %s (repository INTEGER, section TEXT, key TEXT, value TEXT,"
                        + " PRIMARY KEY (repository, section, key)"
                        + ", FOREIGN KEY (repository) REFERENCES %s(repository) ON DELETE CASCADE);",
                configTable, tables.repositories());
        ddl.add(sql);
        sql = format("CREATE INDEX %s_section_idx ON %s (repository, section);",
                stripSchema(configTable), configTable);
        ddl.add(sql);
        sql = format("CREATE VIEW %s " + "AS SELECT r.*, c.value AS name FROM "
                + "%s r INNER JOIN %s c ON r.repository = c.repository WHERE c.section = 'repo' AND c.key = 'name';",
                viewName, repositories, configTable);
        ddl.add(sql);
    }

    protected void createRepositoriesTable(List<String> ddl, TableNames tables) {
        final String repositories = tables.repositories();

        String sql = format("CREATE TABLE %s (repository serial PRIMARY KEY, created TIMESTAMP);",
                repositories);
        ddl.add(sql);
        ddl.add("-- create an entry for global config to have a matching entry in repositories");
        sql = format("INSERT INTO %s (repository, created) VALUES (%d, NOW());", repositories,
                Environment.GLOBAL_KEY);
        ddl.add(sql);
    }

    protected void createObjectIdCompositeType(List<String> ddl) {

        ddl.add("-- There's no CREATE TYPE IF NOT EXIST, so use this trick");
        ddl.add("-- This function is to create a type if it does not exist since there's no support for IF NOT EXISTS for CREATE TYPE\n");

        final String func = "CREATE OR REPLACE FUNCTION create_objectid_type() RETURNS integer AS $$\n"//
                + "DECLARE v_exists INTEGER;\n"//
                + "BEGIN\n"//
                + "    SELECT into v_exists (SELECT 1 FROM pg_type WHERE typname = 'objectid');\n"//
                + "    IF v_exists IS NULL THEN\n"//
                + "        CREATE TYPE OBJECTID AS(h1 INTEGER, h2 BIGINT, h3 BIGINT);\n"//
                + "    END IF;\n"//
                + "    RETURN v_exists;\n"//
                + "END;\n"//
                + "$$ LANGUAGE plpgsql;";

        ddl.add(func);
        ddl.add("SELECT create_objectid_type();");
        ddl.add("DROP FUNCTION IF EXISTS create_objectid_type();");
    }

    static void run(final Connection cx, final String sql) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.execute(PGStorage.log(sql, PGStorage.LOG));
        } catch (SQLException e) {
            LOG.error("Error running SQL: {}", sql, e);
            throw e;
        }
    }

    public void checkCompatibility(Connection cx, Environment env)
            throws IllegalArgumentException, SQLException {
        final int latestSchemaVersion = getLatestSchemaVersion();
        final int currentSchemaVersion = getSchemaVersion(cx, env.getTables());
        if (currentSchemaVersion < latestSchemaVersion) {
            String msg = String.format(
                    "ERROR: Database %s is running an outdated geogig schema. You need to run `geogig postgres-upgrade` from the command line before continuing.",
                    env.getDatabaseName());
            throw new IllegalArgumentException(msg);
        }
    }

}
