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

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;

import javax.sql.DataSource;

import org.junit.AssumptionViolatedException;
import org.junit.rules.ExternalResource;
import org.locationtech.geogig.storage.postgresql.commands.PGInitDB;
import org.locationtech.geogig.test.TestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.NonNull;

public class PGTemporaryTestConfig extends ExternalResource implements Function<String, URI> {

    private static final Logger LOG = LoggerFactory.getLogger(PGTemporaryTestConfig.class);

    private Environment environment;

    private final String repositoryName;

    private PGTestDataSourceProvider dataSourceProvider;

    private final boolean externalDataSource;

    public PGTemporaryTestConfig(@NonNull String repositoryId) {
        this.repositoryName = repositoryId;
        this.dataSourceProvider = new PGTestDataSourceProvider();
        this.externalDataSource = false;
    }

    public PGTemporaryTestConfig(@NonNull String repositoryId,
            @NonNull PGTestDataSourceProvider dataSourceProvider) {
        this.repositoryName = repositoryId;
        this.dataSourceProvider = dataSourceProvider;
        this.externalDataSource = true;
    }

    public @Override void before() throws AssumptionViolatedException {
        if (!externalDataSource) {
            dataSourceProvider.before();
        }
        org.junit.Assume.assumeTrue(isEnabled());
        environment = newEnvironment(repositoryName);
    }

    public void init(TestRepository testRepository) {
        testRepository.setURIBuilder(this);
        // PGTemporaryTestConfig removes all tables for the environment's table prefix so no need
        // for TestRepository to try to remove the repositories
        testRepository.setRemoveRepositoriesAtTearDown(false);
    }

    public @Override URI apply(String repositoryName) {
        return newRepoURI(repositoryName);
    }

    private boolean isEnabled() {
        return dataSourceProvider.isEnabled();
    }

    public @Override void after() {
        if (environment == null) {
            return;
        }
        if (dataSourceProvider == null) {
            return;
        }
        try {
            deleteTables(environment);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (!externalDataSource) {
                dataSourceProvider.after();
            }
            dataSourceProvider = null;
            environment.close();
        }
    }

    public DataSource getDataSource() {
        return dataSourceProvider.getDataSource();
    }

    public void deleteTables(Environment environment) throws SQLException {
        if (environment == null) {
            return;
        }

        TableNames tables = environment.getTables();
        try (Connection cx = getDataSource().getConnection()) {
            cx.setAutoCommit(false);
            execute(cx, String.format("DROP VIEW IF EXISTS %s", tables.repositoryNamesView()));
            delete(cx, tables.objects(), true);
            delete(cx, tables.conflicts());
            delete(cx, tables.blobs());

            delete(cx, tables.index());
            delete(cx, tables.indexMappings());
            delete(cx, tables.indexObjects());

            delete(cx, tables.graphMappings());
            delete(cx, tables.graphEdges());
            delete(cx, tables.graphMappings());
            delete(cx, tables.graphProperties());

            delete(cx, tables.refs());
            delete(cx, tables.config());
            delete(cx, tables.repositories());
            delete(cx, tables.metadata());
            cx.commit();
        }
    }

    private void delete(Connection cx, String table) throws SQLException {
        delete(cx, table, false);
    }

    private void delete(Connection cx, String table, boolean cascade) throws SQLException {
        String sql = String.format("DROP TABLE IF EXISTS %s %s", table, cascade ? "CASCADE" : "");
        execute(cx, sql);
    }

    private void execute(Connection cx, String sql) throws SQLException {
        LOG.debug(sql);
        try (Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }

    public Environment getEnvironment() {
        return environment;
    }

    public Environment getEnvironment(boolean readOnly) {
        environment.setReadOnly(readOnly);
        return environment;
    }

    public Environment newEnvironment(String repositoryName) {
        String tablePrefix = "geogig_" + newTablePrefix() + "_";
        Environment env = dataSourceProvider.newEnvironment(repositoryName, tablePrefix);
        new PGInitDB().setEnvironment(env).call();
        return env;
    }

    private int newTablePrefix() {
        // this method of creating a sequence if it doesn't exist is compatible with PG prior to 9.5
        final String createSequence = "DO $$ BEGIN CREATE SEQUENCE geogig_test_prefix_sequence; EXCEPTION WHEN duplicate_table THEN END $$ LANGUAGE plpgsql;";
        try (Connection cx = getDataSource().getConnection()) {
            cx.setAutoCommit(false);
            try (Statement st = cx.createStatement()) {
                st.execute(createSequence);
                cx.commit();
                cx.setAutoCommit(true);
            }
            try (Statement st = cx.createStatement()) {
                try (ResultSet rs = st
                        .executeQuery("select nextval('geogig_test_prefix_sequence')")) {
                    rs.next();
                    int val = rs.getInt(1);
                    return val;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public String getRootURI() {
        Environment env = getEnvironment();
        URI uri = env.getConnectionConfig().toURI();
        String url = uri.toString();
        return url;
    }

    public URI newRepoURI(String repositoryName) {
        return getEnvironment().withRepository(repositoryName).toURI();
    }
}
