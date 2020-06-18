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
import static org.junit.Assert.assertNotNull;
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

public class PGStorageTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    private Environment env;

    @Before
    public void before() {
        this.env = testConfig.getEnvironment();
    }

    @Test
    public void testCreateNewRepo() throws SQLException {
        final TableNames tables = env.getTables();
        final List<String> tableNames = tables.all();
        final DataSource dataSource = env.getDataSource();
        assertFalse(env.isRepositoryIdSet());
        for (String table : tableNames) {
            assertTableExist(dataSource, table);
        }
        PGStorage.createNewRepo(env);
        assertTrue(env.isRepositoryIdSet());

        for (String table : tableNames) {
            assertTableExist(dataSource, table);
        }

        try (Connection cx = dataSource.getConnection()) {
            String repositories = tables.repositories();
            String sql = format("SELECT * from %s WHERE repository = ?", repositories);
            int repositoryId = env.getRepositoryId();
            try (PreparedStatement st = cx.prepareStatement(sql)) {
                st.setInt(1, repositoryId);
                try (ResultSet rs = st.executeQuery()) {
                    assertTrue(format("repository '%s' not found in table '%s'", repositoryId,
                            repositories), rs.next());
                }
            }
        }
    }

    @Test
    public void testDeleteRepo() {
        final TableNames tables = env.getTables();
        final List<String> tableNames = tables.all();

        PGStorage.createNewRepo(env);

        final DataSource ds = env.getDataSource();
        assertTrue(PGStorage.deleteRepository(env));
        assertFalse(PGStorage.deleteRepository(env));

        // the tables should still exist though
        for (String table : tableNames) {
            assertTableExist(ds, table);
        }
    }

    @Test
    public void testRepoExists() {
        assertFalse(PGStorage.repoExists(env));

        PGStorage.createNewRepo(env);
        assertTrue(PGStorage.repoExists(env));

        Environment anotherConfig = env.withRepository("nonExistentRepoId");
        assertFalse(PGStorage.repoExists(anotherConfig));
    }

    // @Test
    // public void testConnectionPoolConfig() throws SQLException {
    // // Try only allowing a single connection
    // try (PGConfigDatabase globalOnlydb = new PGConfigDatabase(env)) {
    // globalOnlydb.putGlobal(Environment.KEY_MAX_CONNECTIONS, "1");
    // }
    // assertTrue(testConfig.closeDataSource());
    // final DataSource source_1 = env.getDataSource();
    // env.setDataSource(source_1);
    // try (PGConfigDatabase globalOnlydb = new PGConfigDatabase(env)) {
    // assertEquals("1", globalOnlydb.getGlobal(Environment.KEY_MAX_CONNECTIONS).get());
    // }
    // try (Connection c1 = PGStorage.newConnection(source_1)) {
    // try {
    // PGStorage.newConnection(source_1);
    // fail();
    // } catch (RepositoryBusyException e) {
    // // expected;
    // }
    // }
    // // Try allowing two connections
    // try (PGConfigDatabase globalOnlydb = new PGConfigDatabase(env)) {
    // globalOnlydb.putGlobal(Environment.KEY_MAX_CONNECTIONS, "2");
    // }
    //
    // assertTrue(testConfig.closeDataSource());
    // final DataSource source_2 = env.getDataSource();
    // assertNotSame(source_1, source_2);
    // env.setDataSource(source_2);
    //
    // try (PGConfigDatabase globalOnlydb = new PGConfigDatabase(env)) {
    // assertEquals("2", globalOnlydb.getGlobal(Environment.KEY_MAX_CONNECTIONS).get());
    // }
    // try (Connection c1 = PGStorage.newConnection(source_2)) {
    // try (Connection c2 = PGStorage.newConnection(source_2)) {
    // try {
    // PGStorage.newConnection(source_2);
    // fail();
    // } catch (RepositoryBusyException e) {
    // // expected;
    // }
    // }
    // }
    // assertTrue(PGStorage.closeDataSource(source_2));
    // }

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
