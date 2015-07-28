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

import javax.sql.DataSource;

import org.locationtech.geogig.storage.ConnectionManager;
import org.locationtech.geogig.storage.postgresql.Environment.ConnectionConfig;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class DataSourceManager extends ConnectionManager<Environment.ConnectionConfig, DataSource> {

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
        //hc.setIdleTimeout(30/* seconds */);
        hc.setUsername(config.getUser());
        hc.setPassword(config.getPassword());

        HikariDataSource ds = new HikariDataSource(hc);
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
