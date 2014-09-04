/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.sqlite;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.di.GeogigModule;
import org.slf4j.Logger;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteDataSource;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Guice;
import com.google.inject.util.Modules;

/**
 * Utility class.
 * 
 * @author Justin Deoliveira, Boundless
 * 
 */
public class Xerial {

    /**
     * Default synchronization setting, see {@link #turnSynchronizationOff()}
     */
    private static SynchronousMode DEFAULT_SYNC_MODE = SynchronousMode.NORMAL;

    /**
     * Turns SQLite synchronization off.
     * <p>
     * Hack put in place only for testing, never set this for production use.
     * </p>
     */
    @VisibleForTesting
    public static void turnSynchronizationOff() {
        DEFAULT_SYNC_MODE = SynchronousMode.OFF;
    }

    /**
     * Logs a (prepared) sql statement.
     * 
     * @param sql Base sql to log.
     * @param log The logger object.
     * @param args Optional arguments to the statement.
     * 
     * @return The original statement.
     */
    public static String log(String sql, Logger log, Object... args) {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder(sql);
            if (args.length > 0) {
                sb.append(";");
                for (int i = 0; i < args.length; i++) {
                    sb.append(i).append("=").append(args[i]).append(", ");
                }
                sb.setLength(sb.length() - 2);
            }
            log.debug(sb.toString());
        }
        return sql;
    }

    /**
     * Creates the injector to enable xerial sqlite storage.
     */
    public static Context injector() {
        return Guice.createInjector(Modules.override(new GeogigModule()).with(
                new XerialSQLiteModule())).getInstance(Context.class);
    }

    public static SQLiteDataSource newDataSource(File db) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + db.getAbsolutePath());
        dataSource.setSynchronous(DEFAULT_SYNC_MODE.getValue());
        return dataSource;
    }

    public static Connection newConnection(DataSource ds) {
        try {
            return ds.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to obatain connection", e);
        }
    }
}
