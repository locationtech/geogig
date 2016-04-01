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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.storage.postgresql.Environment.ConnectionConfig;

public class PGStorageTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    private Environment config;

    @Before
    public void before() {
        this.config = testConfig.getEnvironment();
    }

    @Test
    public void testCreateNewRepo() {
        final DataSource ds = PGStorage.newDataSource(config);
        final TableNames tables = config.getTables();
        final List<String> tableNames = tables.all();
        try {
            for (String table : tableNames) {
                assertTableDoesntExist(ds, table);
            }
            PGStorage.createNewRepo(config);
            for (String table : tableNames) {
                assertTableExist(ds, table);
            }

            new DbOp<Void>() {
                @Override
                protected Void doRun(Connection cx) throws IOException, SQLException {
                    String repositories = tables.repositories();
                    String sql = format("SELECT * from %s WHERE repository = ?", repositories);
                    String repositoryId = config.repositoryId;
                    try (PreparedStatement st = cx.prepareStatement(sql)) {
                        st.setString(1, repositoryId);
                        try (ResultSet rs = st.executeQuery()) {
                            assertTrue(
                                    format("repository '%s' not found in table '%s'", repositoryId,
                                            repositories), rs.next());
                        }
                    }
                    return null;
                }
            }.run(ds);
        } finally {
            PGStorage.closeDataSource(ds);
        }
    }

    @Test
    public void testRepoExists() {
        assertFalse(PGStorage.repoExists(config));

        final DataSource ds = PGStorage.newDataSource(config);
        try {
            PGStorage.createNewRepo(config);
            assertTrue(PGStorage.repoExists(config));

            ConnectionConfig connConfig = config.connectionConfig;
            Environment anotherConfig = new Environment(connConfig.getServer(), connConfig.getPortNumber(),
                    connConfig.getDatabaseName(), connConfig.getSchema(), connConfig.getUser(),
                    connConfig.getPassword(), "nonExistentRepoId", null);
            assertFalse(PGStorage.repoExists(anotherConfig));
        } finally {
            PGStorage.closeDataSource(ds);
        }
    }

    private void assertTableExist(DataSource ds, String table) {
        assertTrue(format("Table %s does not exist", table), tableExists(ds, table));
    }

    private void assertTableDoesntExist(DataSource ds, String table) {
        assertFalse(format("Table %s already exists", table), tableExists(ds, table));
    }

    private boolean tableExists(DataSource ds, final String tableName) {
        return new DbOp<Boolean>() {
            @Override
            protected Boolean doRun(Connection cx) throws IOException, SQLException {
                DatabaseMetaData md = cx.getMetaData();
                final String schema = PGStorage.schema(tableName);
                final String table = PGStorage.stripSchema(tableName);
                try (ResultSet rs = md.getTables(null, schema, table, null)) {
                    return rs.next();
                }
            }
        }.run(ds);
    }
}
