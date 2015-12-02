/* Copyright (c) 2015 Boundless and others.
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.api.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
class GeogigMetadata {

    private static final Logger LOG = LoggerFactory.getLogger(GeogigMetadata.class);

    private static final String REPOSITORY_METADATA_TABLE = "geogig_metadata";

    private static final String AUDIT_METADATA_TABLE = "geogig_audited_tables";

    static final int AUDIT_OP_INSERT = 1;

    static final int AUDIT_OP_UPDATE = 2;

    static final int AUDIT_OP_DELETE = 3;

    private Connection cx;

    public GeogigMetadata(Connection connection) {
        this.cx = connection;
    }

    private String log(String sql) {
        LOG.debug(sql);
        return sql;
    }

    public void init(URI repositoryURI) throws SQLException {
        cx.setAutoCommit(false);
        try {
            try (Statement st = cx.createStatement()) {
                String sql = format("CREATE TABLE IF NOT EXISTS %s (repository_uri VARCHAR)",
                        REPOSITORY_METADATA_TABLE);

                st.execute(log(sql));

                sql = format(
                        "CREATE TABLE IF NOT EXISTS %s (table_name VARCHAR, mapped_path VARCHAR, audit_table VARCHAR, root_tree_id VARCHAR)",
                        AUDIT_METADATA_TABLE);

                st.execute(log(sql));
            }

            String sql = format("INSERT OR REPLACE INTO %s VALUES(?)", REPOSITORY_METADATA_TABLE);

            try (PreparedStatement st = cx.prepareStatement(log(sql))) {
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
        final String sql = format(
                "SELECT table_name, mapped_path, audit_table, root_tree_id FROM %s",
                AUDIT_METADATA_TABLE);

        List<AuditTable> tables = new ArrayList<>();
        try (Statement st = cx.createStatement()) {
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String tableName = rs.getString(1);
                    String featureTreePath = rs.getString(2);
                    String auditTable = rs.getString(3);
                    String rootId = rs.getString(4);
                    ObjectId rootTreeId = ObjectId.valueOf(rootId);

                    AuditTable tableInfo = new AuditTable(tableName, featureTreePath, auditTable,
                            rootTreeId);
                    tables.add(tableInfo);
                }
            }
        }
        return tables;
    }

    public void createAudit(final String tableName, final String mappedPath,
            final ObjectId rootTreeId) throws SQLException {
        cx.setAutoCommit(false);
        try {
            String sql = format("INSERT OR REPLACE INTO %s VALUES(?, ?, ?, ?)",
                    AUDIT_METADATA_TABLE);
            final String audit_table = createAuditTable(tableName);

            try (PreparedStatement st = cx.prepareStatement(log(sql))) {

                st.setString(1, tableName);
                st.setString(2, mappedPath);
                st.setString(3, audit_table);
                st.setString(4, rootTreeId.toString());

                st.executeUpdate();
            }
            cx.commit();
        } catch (SQLException e) {
            cx.rollback();
            throw e;
        }
    }

    private String createAuditTable(final String tableName) throws SQLException {
        final String auditTable = tableName + "_audit";

        final LinkedHashMap<String, String> columnNames = getColumnNamesAndTypes(tableName);

        StringBuilder sql = new StringBuilder("CREATE TABLE \"").append(auditTable).append("\" (");

        for (Map.Entry<String, String> c : columnNames.entrySet()) {
            String colName = c.getKey();
            String colType = c.getValue();
            String colDef = format("\"%s\" %s, ", colName, colType);
            sql.append(colDef);
        }
        sql.append("audit_timestamp INTEGER DEFAULT CURRENT_TIMESTAMP, audit_op INTEGER)");

        try (Statement st = cx.createStatement()) {
            st.execute(log(sql.toString()));
        }

        createInsertTrigger(tableName, auditTable, columnNames);
        createUpdateTrigger(tableName, auditTable, columnNames);
        createDeleteTrigger(tableName, auditTable, columnNames);

        return auditTable;
    }

    /**
     * Gathers table column names and type names according to <a
     * href="http://www.sqlite.org/pragma.html#pragma_table_info">PRAGMA table_info</a>
     */
    private LinkedHashMap<String, String> getColumnNamesAndTypes(final String tableName)
            throws SQLException {

        LinkedHashMap<String, String> cols = new LinkedHashMap<>();

        final String sql = format("PRAGMA table_info('%s')", tableName);
        try (Statement st = cx.createStatement()) {
            try (ResultSet rs = st.executeQuery(log(sql))) {
                while (rs.next()) {
                    cols.put(rs.getString(2), rs.getString(3));
                }
            }
        }
        return cols;
    }

    private void createInsertTrigger(final String tableName, final String auditTable,
            final LinkedHashMap<String, String> columnNames) throws SQLException {

        StringBuilder trigger = new StringBuilder(format(
                "CREATE TRIGGER '%s_insert' AFTER INSERT ON '%s'\n", auditTable, tableName));
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
            st.execute(log(trigger.toString()));
        }
    }

    private void createUpdateTrigger(final String tableName, final String auditTable,
            final LinkedHashMap<String, String> columnNames) throws SQLException {

        StringBuilder trigger = new StringBuilder(format(
                "CREATE TRIGGER '%s_update' AFTER UPDATE ON '%s'\n", auditTable, tableName));
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
            st.execute(log(trigger.toString()));
        }
    }

    private void createDeleteTrigger(final String tableName, final String auditTable,
            final LinkedHashMap<String, String> columnNames) throws SQLException {

        StringBuilder trigger = new StringBuilder(format(
                "CREATE TRIGGER '%s_delete' AFTER DELETE ON '%s'\n", auditTable, tableName));
        trigger.append("BEGIN\n");

        final String insert = format(
                "  INSERT INTO '%s' ('fid', audit_op) VALUES (OLD.fid, %s);\n", auditTable,
                AUDIT_OP_DELETE);
        trigger.append(insert);

        trigger.append("END\n");

        try (Statement st = cx.createStatement()) {
            st.execute(log(trigger.toString()));
        }
    }
}
