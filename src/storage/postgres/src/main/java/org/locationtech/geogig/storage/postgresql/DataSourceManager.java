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
import static org.locationtech.geogig.storage.postgresql.PGStorage.log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.locationtech.geogig.storage.impl.ConnectionManager;
import org.locationtech.geogig.storage.postgresql.Environment.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class DataSourceManager extends ConnectionManager<Environment, DataSource> {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceManager.class);

    @Override
    protected DataSource connect(Environment config) {
        HikariConfig hc = new HikariConfig();
        hc.setConnectionInitSql("SELECT NOW()");
        hc.setConnectionTestQuery("SELECT NOW()");
        hc.setDriverClassName("org.postgresql.Driver");

        final String jdbcUrl = getUrl(config.connectionConfig);
        hc.setJdbcUrl(jdbcUrl);

        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(0);
        // hc.setIdleTimeout(30/* seconds */);
        hc.setUsername(config.getUser());
        hc.setPassword(config.getPassword());
        hc.setConnectionTimeout(5000);

        LOG.debug("Connecting to " + jdbcUrl + " as user " + config.getUser());
        HikariDataSource ds = new HikariDataSource(hc);
        try (Connection c = ds.getConnection()) {
            final String sql = format(
                    "SELECT value FROM %s WHERE repository = ? AND section = ? AND key = ?",
                    config.getTables().config());
            String[] maxConnections = Environment.KEY_MAX_CONNECTIONS.split("\\.");
            String section = maxConnections[0];
            String key = maxConnections[1];
            try (PreparedStatement ps = c
                    .prepareStatement(log(sql, LOG, PGConfigDatabase.GLOBAL_KEY, section, key))) {
                ps.setInt(1, PGConfigDatabase.GLOBAL_KEY);
                ps.setString(2, section);
                ps.setString(3, key);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ds.setMaximumPoolSize(Integer.parseInt(rs.getString(1)));
                    }
                }
            } catch (SQLException e) {
                // tables weren't set up yet
            }

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
