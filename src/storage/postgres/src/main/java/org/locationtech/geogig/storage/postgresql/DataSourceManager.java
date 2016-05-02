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

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.locationtech.geogig.storage.ConnectionManager;
import org.locationtech.geogig.storage.postgresql.Environment.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class DataSourceManager extends ConnectionManager<Environment.ConnectionConfig, DataSource> {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceManager.class);

    @Override
    protected DataSource connect(Environment.ConnectionConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setConnectionInitSql("SELECT NOW()");
        hc.setConnectionTestQuery("SELECT NOW()");
        hc.setDriverClassName("org.postgresql.Driver");
        
        final String jdbcUrl = getUrl(config);
        hc.setJdbcUrl(jdbcUrl);

        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(0);
        // hc.setIdleTimeout(30/* seconds */);
        hc.setUsername(config.getUser());
        hc.setPassword(config.getPassword());

        LOG.debug("Connecting to " + jdbcUrl + " as user " + config.getUser());
        HikariDataSource ds = new HikariDataSource(hc);
        try (Connection c = ds.getConnection()) {
            LOG.debug("Connected to " + jdbcUrl + " as " + config.getUser());
        } catch (SQLException e) {
            LOG.error("Unable to connect to " + jdbcUrl + " as " + config.getUser(), e);
            throw Throwables.propagate(e);
        }
        return ds;
    }

    @Override
    protected void disconnect(DataSource ds) {
        ((HikariDataSource) ds).close();
    }

    String getUrl(ConnectionConfig config) {
        StringBuilder sb = new StringBuilder("jdbc:postgresql://").append(config.getServer());
        if (config.getPortNumber() != 0) {
            sb.append(':').append(config.getPortNumber());
        }
        sb.append('/').append(config.getDatabaseName());
        sb.append("?binaryTransfer=true");
        sb.append("&prepareThreshold=3");

        return sb.toString();
    }

}
