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

import java.io.File;

import javax.sql.DataSource;

import org.junit.AssumptionViolatedException;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to be used as a {@link Rule} or {@link ClassRule} that provides access to a
 * {@link DataSource} for the PostgreSQL database the tests should run against.
 *
 */
public class PGTestDataSourceProvider extends ExternalResource implements PGDataSourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(PGTestDataSourceProvider.class);

    private ConnectionConfig connectionConfig;

    private PGTestProperties props;

    private DataSource dataSource;

    private DataSourceManager actualProvider = new DataSourceManager();

    public @Override DataSource get(ConnectionConfig config) {
        return dataSource;
    }

    public @Override void close(DataSource dataSource) {
        // no-op
    }

    public @Override void before() throws AssumptionViolatedException {
        this.props = new PGTestProperties();
        org.junit.Assume.assumeTrue(isEnabled());

        // won't even get there if the above statement is not true
        this.connectionConfig = props.getConnectionConfig();
        this.dataSource = actualProvider.get(connectionConfig);
    }

    public @Override void after() {
        actualProvider.releaseAll();
        connectionConfig = null;
        dataSource = null;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    private boolean notified = false;

    public boolean isEnabled() {
        final boolean enabled = props.get(PGTestProperties.TESTS_ENABLED_KEY, Boolean.class)
                .orElse(Boolean.FALSE).booleanValue();
        if (!enabled && !notified) {
            final String home = System.getProperty("user.home");
            String propsFile = new File(home, PGTestProperties.CONFIG_FILE).getAbsolutePath();
            LOG.info("PostgreSQL backend tests disabled. Configure " + propsFile);
            notified = true;
        }
        return enabled;
    }

    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public Environment newEnvironment(String repositoryName, String tablePrefix) {
        ConnectionConfig newConfig = getConnectionConfig().withTablePrefix(tablePrefix);
        return new Environment(newConfig, this, repositoryName);
    }

    public PGTestProperties getTestProperties() {
        return props;
    }
}
