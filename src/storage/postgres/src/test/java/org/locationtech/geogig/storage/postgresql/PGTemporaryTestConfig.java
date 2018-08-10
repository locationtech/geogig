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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.AssumptionViolatedException;
import org.junit.rules.ExternalResource;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.TableNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class PGTemporaryTestConfig extends ExternalResource {

    private static final Logger LOG = LoggerFactory.getLogger(PGTemporaryTestConfig.class);

    private Environment environment;

    private final String repositoryName;

    private PGTestDataSourceProvider dataSourceProvider;

    private final boolean externalDataSource;

    public PGTemporaryTestConfig(String repositoryId) {
        Preconditions.checkNotNull(repositoryId);
        this.repositoryName = repositoryId;
        this.dataSourceProvider = new PGTestDataSourceProvider();
        this.externalDataSource = false;
    }

    public PGTemporaryTestConfig(String repositoryId, PGTestDataSourceProvider dataSourceProvider) {
        Preconditions.checkNotNull(repositoryId);
        Preconditions.checkNotNull(dataSourceProvider);
        this.repositoryName = repositoryId;
        this.dataSourceProvider = dataSourceProvider;
        this.externalDataSource = true;
    }

    @Override
    public void before() throws AssumptionViolatedException {
        if (!externalDataSource) {
            dataSourceProvider.before();
        }
        org.junit.Assume.assumeTrue(isEnabled());
        environment = getEnvironment();
    }

    private boolean isEnabled() {
        return dataSourceProvider.isEnabled();
    }

    @Override
    public void after() {
        if (environment == null) {
            return;
        }
        if (dataSourceProvider == null) {
            return;
        }
        try {
            delete(environment);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (!externalDataSource) {
                dataSourceProvider.after();
            }
            dataSourceProvider = null;
        }
    }

    public void closeDataSource() {
        dataSourceProvider.closeDataSource();
    }

    public DataSource getDataSource() {
        return dataSourceProvider.getDataSource();
    }

    public void delete(Environment environment) throws SQLException {
        if (environment == null) {
            return;
        }

        TableNames tables = environment.getTables();
        try (Connection cx = getDataSource().getConnection()) {
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

    public synchronized Environment getEnvironment() {
        if (environment == null) {
            environment = newEnvironment(repositoryName);
        }
        return environment;
    }

    public Environment newEnvironment(String repositoryName) {
        try (Connection cx = getDataSource().getConnection()) {
        } catch (SQLException e) {
            throw new RuntimeException();
        }
        String tablePrefix = "geogig_" + newTablePrefix() + "_";
        Environment env = dataSourceProvider.newEnvironment(repositoryName, tablePrefix);
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
        PGTestProperties props = dataSourceProvider.getTestProperties();
        Environment env = getEnvironment();
        String repositoryName = null;
        String prefix = env.getTables().getPrefix();
        String url = props.buildRepoURL(repositoryName, prefix);
        return url;
    }

    public String getRepoURL() {
        Environment env = getEnvironment();
        return getRepoURI(env);
    }

    public String newRepoURI(String repositoryName) {
        Environment env = getEnvironment();
        PGTestProperties props = dataSourceProvider.getTestProperties();
        String url = props.buildRepoURL(repositoryName, env.getTables().getPrefix());
        return url;
    }

    public String getRepoURI(Environment env) {
        PGTestProperties props = dataSourceProvider.getTestProperties();
        String url = props.buildRepoURL(env.getRepositoryName(), env.getTables().getPrefix());
        return url;
    }

}
