/* Copyright (c) 2015-2017 Boundless and others.
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
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.sql.DataSource;

import org.locationtech.geogig.storage.impl.ConnectionManager;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DataSourceManager extends ConnectionManager<ConnectionConfig.Key, DataSource> {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceManager.class);

    /**
     * Flag to perform the driver version check only once at {@link #connect} ->
     * {@link #verifyDriverVersion()}
     */
    @VisibleForTesting
    static boolean driverVersionVerified = false;

    private static String driverVersion;

    private static int driverMajorVersion;

    @VisibleForTesting
    int getDriverMajorVersion() {
        return new org.postgresql.Driver().getMajorVersion();
    }

    @VisibleForTesting
    int getDriverMinorVersion() {
        return new org.postgresql.Driver().getMinorVersion();
    }

    /**
     * GeoGig required a PostgreSQL JDBC driver version to be 42.1.1+, which is the first one that
     * supports true binary transfers
     * 
     * @return {@code true}
     */
    @VisibleForTesting
    boolean verifyDriverVersion() {
        if (!driverVersionVerified) {
            try {
                int majorVersion = getDriverMajorVersion();
                int minorVersion = getDriverMinorVersion();
                driverVersion = majorVersion + "." + minorVersion;
                driverMajorVersion = majorVersion;
            } catch (Throwable e) {
                driverMajorVersion = 0;
                driverVersion = "Unknown";
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            } finally {
                driverVersionVerified = true;
            }
            if (driverMajorVersion < 42) {
                List<String> postgresJars = getPostgresJars();
                String msg = "**********************\n"
                        + "GeoGig PostgreSQL support requires PostgreSQL JDBC Driver version 42.1.1 and above."
                        + "\norg.postgresql.Driver '" + driverVersion + "'"
                        + " was loaded by the classloader."
                        + "\nPlease make sure there are no multiple postgres drivers in the classpath.";
                if (!postgresJars.isEmpty()) {
                    msg += "\nThe following jar files contain the class org.postgresql.Driver: "
                            + postgresJars.toString();
                }
                logError(msg);
            }
        }
        return driverMajorVersion >= 42;
    }

    void logError(String msg) {
        LOG.error(msg);
    }

    @VisibleForTesting
    List<String> getPostgresJars() {
        final String classpath = System.getProperty("java.class.path", "");
        final String separator = System.getProperty("path.separator");
        final List<String> classpathItems = Splitter.on(separator).omitEmptyStrings().trimResults()
                .splitToList(classpath);

        List<String> postgresJars = classpathItems.parallelStream()//
                .filter((s) -> s.endsWith(".jar"))//
                .filter((s) -> {
                    boolean hasDriver = false;
                    try (ZipFile zf = new ZipFile(s)) {
                        ZipEntry entry = zf.getEntry("org/postgresql/Driver.class");
                        hasDriver = entry != null;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return hasDriver;
                })//
                .collect(Collectors.toList());

        return postgresJars;
    }

    @Override
    protected DataSource connect(ConnectionConfig.Key connInfo) {
        if (!verifyDriverVersion()) {
            throw new IllegalStateException(
                    "PostgreSQL JDBC Driver version not supported by GeoGig: " + driverVersion);
        }
        PGSimpleDataSource pgSimpleDataSource = new PGSimpleDataSource();
        pgSimpleDataSource.setBinaryTransfer(true);
        pgSimpleDataSource.setApplicationName("geogig");
        pgSimpleDataSource.setServerName(connInfo.server);
        pgSimpleDataSource.setDatabaseName(connInfo.databaseName);
        pgSimpleDataSource.setPortNumber(connInfo.portNumber);
        pgSimpleDataSource.setUser(connInfo.user);
        pgSimpleDataSource.setPassword(connInfo.password);
        pgSimpleDataSource.setAssumeMinServerVersion("9.4");
        // A value of {@code -1} stands for forceBinary
        pgSimpleDataSource.setPrepareThreshold(-1);
        pgSimpleDataSource.setTcpKeepAlive(true);

        HikariConfig hc = new HikariConfig();
        // hc.setConnectionInitSql("SELECT NOW()");
        // no need to set a validation query, connections auto validate
        // hc.setConnectionTestQuery("SELECT NOW()");

        // hc.setDriverClassName is replaced by the more explicit hc.setDataSource
        // hc.setDriverClassName("org.postgresql.Driver");
        hc.setDataSource(pgSimpleDataSource);

        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(0);
        // hc.setIdleTimeout(30/* seconds */);
        hc.setUsername(connInfo.user);
        hc.setPassword(connInfo.password);
        hc.setConnectionTimeout(5000);

        String jdbcUrl = connInfo.server + ":" + connInfo.portNumber + "/" + connInfo.databaseName;
        LOG.debug("Connecting to " + jdbcUrl + " as user " + connInfo.user);
        HikariDataSource ds = new HikariDataSource(hc);

        final String configTable = (connInfo.tablePrefix == null ? TableNames.DEFAULT_TABLE_PREFIX
                : connInfo.tablePrefix) + "config";
        try (Connection c = ds.getConnection()) {
            final String sql = format(
                    "SELECT value FROM %s WHERE repository = ? AND section = ? AND key = ?",
                    configTable);
            String[] maxConnections = Environment.KEY_MAX_CONNECTIONS.split("\\.");
            String section = maxConnections[0];
            String key = maxConnections[1];
            try (PreparedStatement ps = c
                    .prepareStatement(log(sql, LOG, Environment.GLOBAL_KEY, section, key))) {
                ps.setInt(1, Environment.GLOBAL_KEY);
                ps.setString(2, section);
                ps.setString(3, key);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String globalMaxConnections = rs.getString(1);
                        ds.setMaximumPoolSize(Integer.parseInt(globalMaxConnections));
                    }
                }
            } catch (SQLException e) {
                // tables weren't set up yet
            }

            LOG.debug("Connected to " + jdbcUrl + " as " + connInfo.user);
        } catch (SQLException e) {
            LOG.error("Unable to connect to " + jdbcUrl + " as " + connInfo.user, e);
            throw new IllegalArgumentException(e);
        }
        return ds;
    }

    @Override
    protected void disconnect(DataSource ds) {
        ((HikariDataSource) ds).close();
    }
}
