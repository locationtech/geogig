/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.config;

import static java.lang.String.format;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
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
import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.v9.PGConfigDatabase;

public class PGStorageTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    private Environment config;

    @Before
    public void before() {
        this.config = testConfig.getEnvironment();
    }

    /**
     * {@link PGStorage#newDataSource(Environment)} should return the same {@link DataSource} when
     * the connection parameters match the same database
     */
    public @Test void testAcquireDataSource() throws Exception {

        String server = config.getServer();
        int portNumber = config.getPortNumber();
        String databaseName = config.getDatabaseName();
        String schema = config.getSchema();
        String user = config.getUser();
        String password = config.getPassword();
        String repositoryName = config.getRepositoryName();
        String tablePrefix = config.getTables().getPrefix();

        final Environment sameEnv = new Environment(server, portNumber, databaseName, schema, user,
                password, repositoryName, tablePrefix);

        final Environment sameDbDifferentRepo = new Environment(server, portNumber, databaseName,
                schema, user, password, "another_repository", tablePrefix);
        assertNotEquals(repositoryName, sameDbDifferentRepo.getRepositoryName());

        final Environment noReposiotryName = new Environment(server, portNumber, databaseName,
                schema, user, password, "another_repository", tablePrefix);
        assertNotEquals(repositoryName, noReposiotryName.getRepositoryName());

        final DataSource expected = testConfig.getDataSource();

        DataSource actual = PGStorage.newDataSource(sameEnv);
        try {
            assertSame(expected, actual);
        } finally {
            PGStorage.closeDataSource(actual);
        }
        actual = PGStorage.newDataSource(sameDbDifferentRepo);
        try {
            assertSame(expected, actual);
        } finally {
            PGStorage.closeDataSource(actual);
        }
        actual = PGStorage.newDataSource(noReposiotryName);
        try {
            assertSame(expected, actual);
        } finally {
            PGStorage.closeDataSource(actual);
        }

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
        testConfig.closeDataSource();
        DataSource source = PGStorage.newDataSource(config);
        try (Connection c1 = PGStorage.newConnection(source)) {
            try {
                PGStorage.newConnection(source);
                fail();
            } catch (RepositoryBusyException e) {
                // expected;
            }
        } finally {
            PGStorage.closeDataSource(source);
        }
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
        } finally {
            PGStorage.closeDataSource(source);
        }
    }

    @Test
    public void testGetVersionFromQueryResult() {
        // ensure PG reported versions don't break
        // versions can be {major}.{minor} or {major}.{minor}.{patch}
        try {
            assertNotNull(PGStorage.getVersionFromQueryResult("9.4.0"));
            assertNotNull(PGStorage.getVersionFromQueryResult("10.1"));
            assertNotNull(PGStorage.getVersionFromQueryResult("10.3 (Debian 10.3-1.pgdg90+1)"));
        } catch (Exception ex) {
            // test failed
            ex.printStackTrace();
            fail("Version string not handled");
        }
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
            final String schema = PGStorageTableManager.schema(tableName);
            final String table = PGStorageTableManager.stripSchema(tableName);
            try (ResultSet rs = md.getTables(null, schema, table, null)) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
