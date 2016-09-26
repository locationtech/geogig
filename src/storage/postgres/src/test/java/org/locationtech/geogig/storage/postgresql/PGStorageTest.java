/* Copyright (c) 2015-2016 Boundless.
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
import static org.junit.Assert.fail;

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
import org.locationtech.geogig.repository.RepositoryBusyException;
import org.locationtech.geogig.storage.postgresql.Environment.ConnectionConfig;

import com.google.common.base.Throwables;

public class PGStorageTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    private Environment config;

    @Before
    public void before() {
        this.config = testConfig.getEnvironment();
    }

    @Test
    public void testCreateNewRepo() throws SQLException {
        final TableNames tables = config.getTables();
        final List<String> tableNames = tables.all();
        final DataSource dataSource = PGStorage.newDataSource(config);
        assertFalse(config.isRepositorySet());
        try {
            for (String table : tableNames) {
                assertTableDoesntExist(dataSource, table);
            }
            PGStorage.createNewRepo(config);
            assertTrue(config.isRepositorySet());

            for (String table : tableNames) {
                assertTableExist(dataSource, table);
            }

            try (Connection cx = dataSource.getConnection()) {
                String repositories = tables.repositories();
                String sql = format("SELECT * from %s WHERE repository = ?", repositories);
                int repositoryId = config.getRepositoryId();
                try (PreparedStatement st = cx.prepareStatement(sql)) {
                    st.setInt(1, repositoryId);
                    try (ResultSet rs = st.executeQuery()) {
                        assertTrue(format("repository '%s' not found in table '%s'", repositoryId,
                                repositories), rs.next());
                    }
                }
            }
        } finally {
            PGStorage.closeDataSource(dataSource);
        }
    }

    @Test
    public void testDeleteRepo() {
        final TableNames tables = config.getTables();
        final List<String> tableNames = tables.all();

        PGStorage.createNewRepo(config);

        final DataSource ds = PGStorage.newDataSource(config);
        try {

            assertTrue(PGStorage.deleteRepository(config));
            assertFalse(PGStorage.deleteRepository(config));

            // the tables should still exist though
            for (String table : tableNames) {
                assertTableExist(ds, table);
            }
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
            Environment anotherConfig = new Environment(connConfig.getServer(),
                    connConfig.getPortNumber(), connConfig.getDatabaseName(),
                    connConfig.getSchema(), connConfig.getUser(), connConfig.getPassword(),
                    "nonExistentRepoId", null);
            assertFalse(PGStorage.repoExists(anotherConfig));
        } finally {
            PGStorage.closeDataSource(ds);
        }
    }

    @Test
    public void testConnectionPoolConfig() throws SQLException {
        // Try only allowing a single connection
        try (PGConfigDatabase globalOnlydb = new PGConfigDatabase(config)) {
            globalOnlydb.putGlobal(Environment.KEY_MAX_CONNECTIONS, "1");
        }
        DataSource source = PGStorage.newDataSource(config);
        try (Connection c1 = PGStorage.newConnection(source)) {
            try {
                PGStorage.newConnection(source);
                fail();
            } catch (RepositoryBusyException e) {
                // expected;
            }
        }
        PGStorage.closeDataSource(source);

        // Try allowing two connections
        try (PGConfigDatabase globalOnlydb = new PGConfigDatabase(config)) {
            globalOnlydb.putGlobal(Environment.KEY_MAX_CONNECTIONS, "2");
        }
        source = PGStorage.newDataSource(config);
        try (Connection c1 = PGStorage.newConnection(source)) {
            try (Connection c2 = PGStorage.newConnection(source)) {
                try {
                    PGStorage.newConnection(source);
                    fail();
                } catch (RepositoryBusyException e) {
                    // expected;
                }
            }
        }
        PGStorage.closeDataSource(source);
    }

    private void assertTableExist(DataSource ds, String table) {
        assertTrue(format("Table %s does not exist", table), tableExists(ds, table));
    }

    private void assertTableDoesntExist(DataSource ds, String table) {
        assertFalse(format("Table %s already exists", table), tableExists(ds, table));
    }

    private boolean tableExists(DataSource dataSource, final String tableName) {
        try (Connection cx = dataSource.getConnection()) {
            DatabaseMetaData md = cx.getMetaData();
            final String schema = PGStorage.schema(tableName);
            final String table = PGStorage.stripSchema(tableName);
            try (ResultSet rs = md.getTables(null, schema, table, null)) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }
}
