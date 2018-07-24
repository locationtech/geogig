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

import javax.sql.DataSource;

import org.junit.AssumptionViolatedException;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.locationtech.geogig.storage.postgresql.config.ConnectionConfig;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Utility to be used as a {@link Rule} or {@link ClassRule} that provides access to a
 * {@link DataSource} for the PostgreSQL database the tests should run against.
 *
 */
public class PGTestDataSourceProvider extends ExternalResource {

    private static final Logger LOG = LoggerFactory.getLogger(PGTestDataSourceProvider.class);

    private ConnectionConfig connectionConfig;

    private PGTestProperties props;

    private DataSource dataSource;

    public @Override void before() throws AssumptionViolatedException {
        loadProperties();
        org.junit.Assume.assumeTrue(isEnabled());

        {// won't even get there if the above statement is not true
            String repositoryId = null;
            Environment config = props.newConfig(repositoryId);
            connectionConfig = config.connectionConfig;
        }
        getDataSource();
    }

    public @Override void after() {
        connectionConfig = null;
        closeDataSource();
    }

    public void closeDataSource() {
        if (dataSource != null) {
            PGStorage.closeDataSource(dataSource);
            dataSource = null;
        }
    }

    public synchronized DataSource getDataSource() {
        Preconditions.checkNotNull(connectionConfig);
        if (dataSource == null) {
            dataSource = PGStorage.newDataSource(connectionConfig);
        }
        return dataSource;
    }

    private boolean notified = false;

    public boolean isEnabled() {
        final boolean enabled = props.get(PGTestProperties.TESTS_ENABLED_KEY, Boolean.class)
                .or(Boolean.FALSE).booleanValue();
        if (!enabled && !notified) {
            final String home = System.getProperty("user.home");
            String propsFile = new File(home, PGTestProperties.CONFIG_FILE).getAbsolutePath();
            LOG.info("PostgreSQL backend tests disabled. Configure " + propsFile);
            notified = true;
        }
        return enabled;
    }

    private void loadProperties() {
        this.props = new PGTestProperties();
    }

    public synchronized ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public Environment newEnvironment(String repositoryId, String tablePrefix) {
        Environment config = props.newConfig(repositoryId, tablePrefix);
        return config;
    }

    public PGTestProperties getTestProperties() {
        return props;
    }

}
