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

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public class PGTemporaryTestConfig extends ExternalResource {

    private static final Logger LOG = LoggerFactory.getLogger(PGTemporaryTestConfig.class);

    private static SecureRandom RND = new SecureRandom();

    private Environment environment;

    private PGTestProperties props;

    private String repositoryId;

    public PGTemporaryTestConfig(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    @Override
    public void before() throws Throwable {
        create();
        org.junit.Assume.assumeTrue(isEnabled());
    }

    private boolean isEnabled() {
        final boolean enabled = props.get(PGTestProperties.TESTS_ENABLED_KEY, Boolean.class)
                .or(Boolean.FALSE).booleanValue();
        if (!enabled) {
            final String home = System.getProperty("user.home");
            String propsFile = new File(home, PGTestProperties.CONFIG_FILE).getAbsolutePath();
            LOG.info("PostgreSQL backend tests disabled. Configure " + propsFile);
        }
        return enabled;
    }

    @Override
    public void after() {
        try {
            delete();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void create() {
        this.props = new PGTestProperties();
    }

    private void delete() throws SQLException {
        if (environment == null) {
            return;
        }
        DataSource dataSource = PGStorage.newDataSource(environment);
        try {
            TableNames tables = environment.getTables();
            Connection cx = dataSource.getConnection();
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
        } finally {
            PGStorage.closeDataSource(dataSource);
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
            String tablePrefix;
            synchronized (RND) {
                tablePrefix = "geogig_" + Math.abs(RND.nextInt(100_000)) + "_";
            }
            environment = props.getConfig(repositoryId, tablePrefix);
        }
        return environment;
    }

    public String getRepoURL() {
        Environment env = getEnvironment();
        String url = props.buildRepoURL(env.getRepositoryName(), env.getTables().getPrefix());
        return url;
    }

}
