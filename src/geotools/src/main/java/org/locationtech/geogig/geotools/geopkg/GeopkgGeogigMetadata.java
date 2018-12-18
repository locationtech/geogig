/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import static java.lang.String.format;

import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.ObjectId;

import lombok.NonNull;

/**
 * Manages audit metadata.
 * <p>
 * TABLE: <b>{@code geogig_metadata}</b>, root auditing metadata table, holds repository information
 * at the time of export.
 * <ul>
 * <li>{@code repository_uri VARCHAR}, URI of the origin repository
 * <li>{@code source_branch VARCHAR}, branch name from which the feature types where exported
 * <li>{@code source_commit CHAR(40)}, string representation of the commit id {@code source_branch}
 * pointed to at the time of export
 * </ul>
 * <p>
 * TABLE: <b>{@code geogig_audited_tables}</b>, mappings from exported geogig tree paths to audit
 * tables
 * <ul>
 * <li>{@code table_name VARCHAR}
 * <li>{@code mapped_path VARCHAR}
 * <li>{@code audit_table VARCHAR}
 * </ul>
 */
public class GeopkgGeogigMetadata implements AutoCloseable {

    private static final String REPOSITORY_METADATA_TABLE = "geogig_metadata";

    private static final String AUDIT_METADATA_TABLE = "geogig_audited_tables";

    public static final int AUDIT_OP_INSERT = 1;

    public static final int AUDIT_OP_UPDATE = 2;

    public static final int AUDIT_OP_DELETE = 3;

    private Map<String, RocksdbMap> fidMappings = new HashMap<>();

    private Connection cx;

    public GeopkgGeogigMetadata(@NonNull Connection connection) {
        this.cx = connection;
    }

    @Override
    public void close() {
        fidMappings.values().forEach(RocksdbMap::close);
        fidMappings.clear();
    }

    public void init(@NonNull URI repositoryURI) throws SQLException {
        cx.setAutoCommit(false);
        try {
            try (Statement st = cx.createStatement()) {
                String sql = format("CREATE TABLE IF NOT EXISTS %s (repository_uri VARCHAR)",
                        REPOSITORY_METADATA_TABLE);

                st.execute(sql);

                sql = format(
                        "CREATE TABLE IF NOT EXISTS %s (table_name VARCHAR, mapped_path VARCHAR, audit_table VARCHAR, commit_id VARCHAR)",
                        AUDIT_METADATA_TABLE);

                st.execute(sql);
            }

            String sql = format("INSERT OR REPLACE INTO %s VALUES(?)", REPOSITORY_METADATA_TABLE);

            try (PreparedStatement st = cx.prepareStatement(sql)) {
                final String uri = repositoryURI.toString();

                st.setString(1, uri);

                st.executeUpdate();
            }
            cx.commit();
        } catch (SQLException e) {
            cx.rollback();
            throw e;
        }
    }

    public List<AuditTable> getAuditTables() throws SQLException {
        final String sql = format("SELECT table_name, mapped_path, audit_table, commit_id FROM %s",
                AUDIT_METADATA_TABLE);

        List<AuditTable> tables = new ArrayList<>();
        try (Statement st = cx.createStatement()) {
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String tableName = rs.getString(1);
                    String featureTreePath = rs.getString(2);
                    String auditTable = rs.getString(3);
                    String commitId = rs.getString(4);
                    ObjectId commitObjectId = ObjectId.valueOf(commitId);

                    AuditTable tableInfo = new AuditTable(tableName, featureTreePath, auditTable,
                            commitObjectId);
                    tables.add(tableInfo);
                }
            }
        }
        return tables;
    }

    public Map<String, String> getFidMappings(@NonNull String tableName) throws SQLException {
        if (fidMappings.containsKey(tableName)) {
            return fidMappings.get(tableName);
        }
        String fidTable = tableName + "_fids";
        RocksdbMap mappings = new RocksdbMap();
        DatabaseMetaData dbm = cx.getMetaData();
        ResultSet tables = dbm.getTables(null, null, fidTable, null);
        while (tables.next()) {
            if (tables.getString("TABLE_NAME").equals(fidTable)) {
                // Fid table exists
                final String sql = format("SELECT gpkg_fid, geogig_fid FROM %s", fidTable);
                try (Statement st = cx.createStatement()) {
                    try (ResultSet rs = st.executeQuery(sql)) {
                        while (rs.next()) {
                            String gpkgfid = rs.getString(1);
                            String geogigfid = rs.getString(2);
                            mappings.put(gpkgfid, geogigfid);
                        }
                    }
                }
            }
        }
        fidMappings.put(tableName, mappings);

        return mappings;
    }

    public void createAudit(final @NonNull String tableName, final @NonNull String mappedPath,
            final @NonNull ObjectId commitObjectId) throws SQLException {
        cx.setAutoCommit(false);
        try {
            String sql = format("INSERT OR REPLACE INTO %s VALUES(?, ?, ?, ?)",
                    AUDIT_METADATA_TABLE);
            final String audit_table = createAuditTable(tableName);

            try (PreparedStatement st = cx.prepareStatement(sql)) {

                st.setString(1, tableName);
                st.setString(2, mappedPath);
                st.setString(3, audit_table);
                st.setString(4, commitObjectId.toString());

                st.executeUpdate();
            }
            cx.commit();
        } catch (SQLException e) {
            cx.rollback();
            throw e;
        }
    }

    public void createChangeLog(final @NonNull String targetTableName) throws SQLException {

        final String changeTable = targetTableName + "_changes";
        final String sql = format(
                "CREATE TABLE IF NOT EXISTS \"%s\" (geogig_fid VARCHAR, audit_op INTEGER)",
                changeTable);

        try (Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }

    public void populateChangeLog(final @NonNull String targetTableName,
            @NonNull Map<String, ChangeType> changedNodes) throws SQLException {

        final String sql = format("INSERT INTO %s_changes  VALUES (?, ?)", targetTableName);

        try (PreparedStatement st = cx.prepareStatement(sql)) {
            for (Entry<String, ChangeType> changedNode : changedNodes.entrySet()) {
                st.setString(1, changedNode.getKey());
                st.setInt(2, auditOpForChangeType(changedNode.getValue()));
                st.addBatch();
            }

            st.executeBatch();
        }
    }

    private int auditOpForChangeType(ChangeType changeType) {
        int op = -1;
        switch (changeType) {
        case ADDED:
            op = AUDIT_OP_INSERT;
            break;
        case MODIFIED:
            op = AUDIT_OP_UPDATE;
            break;
        case REMOVED:
            op = AUDIT_OP_DELETE;
            break;
        }
        return op;
    }

    public String createFidMappingTable(final @NonNull String tableName,
            @NonNull Map<String, String> fidMappings) throws SQLException {
        final String fidMappingTable = tableName + "_fids";

        final String createSql = format(
                "CREATE TABLE IF NOT EXISTS \"%s\" (gpkg_fid VARCHAR, geogig_fid VARCHAR, PRIMARY KEY(gpkg_fid))",
                fidMappingTable);

        final String insertSql = format("INSERT OR REPLACE INTO \"%s\" VALUES(?,?);",
                fidMappingTable);

        cx.setAutoCommit(false);
        try {
            try (Statement st = cx.createStatement()) {
                st.execute(createSql);
            }

            try (PreparedStatement prepared = cx.prepareStatement(insertSql)) {
                for (Entry<String, String> entry : fidMappings.entrySet()) {
                    prepared.setString(1, entry.getKey());
                    prepared.setString(2, entry.getValue());
                    prepared.addBatch();
                }
                prepared.executeBatch();
            }

            cx.commit();
        } catch (SQLException e) {
            cx.rollback();
            throw e;
        }

        return fidMappingTable;
    }

    private String createAuditTable(final String tableName) throws SQLException {
        final String auditTable = tableName + "_audit";

        final LinkedHashMap<String, String> columnNames = getColumnNamesAndTypes(tableName);

        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS \"").append(auditTable)
                .append("\" (");

        for (Map.Entry<String, String> c : columnNames.entrySet()) {
            String colName = c.getKey();
            String colType = c.getValue();
            String colDef = format("\"%s\" %s, ", colName, colType);
            sql.append(colDef);
        }
        sql.append("audit_timestamp INTEGER DEFAULT CURRENT_TIMESTAMP, audit_op INTEGER)");
        final String statement = sql.toString();
        try (Statement st = cx.createStatement()) {
            st.execute(statement);
        }

        createInsertTrigger(tableName, auditTable, columnNames);
        createUpdateTrigger(tableName, auditTable, columnNames);
        createDeleteTrigger(tableName, auditTable);

        return auditTable;
    }

    /**
     * Gathers table column names and type names according to
     * <a href="http://www.sqlite.org/pragma.html#pragma_table_info">PRAGMA table_info</a>
     */
    private LinkedHashMap<String, String> getColumnNamesAndTypes(final String tableName)
            throws SQLException {

        LinkedHashMap<String, String> cols = new LinkedHashMap<>();

        final String sql = format("PRAGMA table_info('%s')", tableName);
        try (Statement st = cx.createStatement()) {
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    cols.put(rs.getString(2), rs.getString(3));
                }
            }
        }
        return cols;
    }

    private void createInsertTrigger(final String tableName, final String auditTable,
            final LinkedHashMap<String, String> columnNames) throws SQLException {

        StringBuilder trigger = new StringBuilder(
                format("CREATE TRIGGER '%s_insert' AFTER INSERT ON '%s'%n", auditTable, tableName));
        trigger.append("BEGIN\n");
        trigger.append(format("  INSERT INTO '%s' (", auditTable));
        for (String colName : columnNames.keySet()) {
            trigger.append('\'').append(colName).append("', ");
        }
        trigger.append("audit_op) VALUES (");
        for (String colName : columnNames.keySet()) {
            trigger.append("NEW.'").append(colName).append("', ");
        }
        trigger.append(AUDIT_OP_INSERT).append(");\n");

        trigger.append("END\n");

        try (Statement st = cx.createStatement()) {
            String statement = trigger.toString();
            st.execute(statement);
        }
    }

    private void createUpdateTrigger(final String tableName, final String auditTable,
            final LinkedHashMap<String, String> columnNames) throws SQLException {

        StringBuilder trigger = new StringBuilder(
                format("CREATE TRIGGER '%s_update' AFTER UPDATE ON '%s'%n", auditTable, tableName));
        trigger.append("BEGIN\n");
        trigger.append(format("  INSERT INTO '%s' (", auditTable));
        for (String colName : columnNames.keySet()) {
            trigger.append('\'').append(colName).append("', ");
        }
        trigger.append("audit_op) VALUES (");
        for (String colName : columnNames.keySet()) {
            trigger.append("NEW.'").append(colName).append("', ");
        }
        trigger.append(AUDIT_OP_UPDATE).append(");\n");

        trigger.append("END\n");

        try (Statement st = cx.createStatement()) {
            String statement = trigger.toString();
            st.execute(statement);
        }
    }

    private void createDeleteTrigger(final String tableName, final String auditTable)
            throws SQLException {

        StringBuilder trigger = new StringBuilder(
                format("CREATE TRIGGER '%s_delete' AFTER DELETE ON '%s'%n", auditTable, tableName));
        trigger.append("BEGIN\n");

        final String insert = format("  INSERT INTO '%s' ('fid', audit_op) VALUES (OLD.fid, %s);%n",
                auditTable, AUDIT_OP_DELETE);
        trigger.append(insert);

        trigger.append("END\n");

        try (Statement st = cx.createStatement()) {
            String statement = trigger.toString();
            st.execute(statement);
        }
    }
}
