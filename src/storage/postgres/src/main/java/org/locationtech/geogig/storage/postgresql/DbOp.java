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

import static org.locationtech.geogig.storage.postgresql.PGStorage.newConnection;
import static org.locationtech.geogig.storage.postgresql.PGStorageModule.LOG;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import com.google.common.base.Throwables;

/**
 * Helper class for executing statements and queries against a JDBC connection.
 * <p>
 * Usage:
 * 
 * <pre>
 * <code>
 * Connection cx = ...;
 * Long count = new DbOp<Long>() {
 *   public Long run(Connection cx) {
 *      ResultSet rs = open(open(cx.createStatement()).executeQuery("SELECT count(*) FROM foo"));
 *      rs.next();
 *      return rs.getLong(1);
 *   }
 * }.run(cx):
 * 
 * </code>
 * </pre>
 * 
 * </p>
 * 
 * 
 * @param <T> Operation return type.
 */
abstract class DbOp<T> {

    static ThreadLocal<Connection> CONN = new ThreadLocal<>();

    static ThreadLocal<AtomicInteger> REFCOUNT = new ThreadLocal<>();

    /**
     * Runs the op against a new connection provided by the data source.
     * <p>
     * The connection is closed after usage.
     * </p>
     * 
     * @param ds The data source to obtain connection from.
     */
    public final T run(DataSource ds) {
        try {
            Connection c = CONN.get();
            if (c == null) {
                c = newConnection(ds);
                CONN.set(c);
                REFCOUNT.set(new AtomicInteger(1));
            } else {
                REFCOUNT.get().incrementAndGet();
            }
            return run(c);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Runs the op.
     * 
     * @param cx The connection to run the op against.
     * 
     * @return The op result.
     */
    private final T run(Connection cx) {
        try {
            try {
                boolean auto = isAutoCommit();
                if (!auto) {
                    cx.setAutoCommit(false);
                }
                try {
                    return doRun(cx);
                } finally {
                    if (!auto) {
                        cx.setAutoCommit(true);
                    }
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        } finally {
            close();
        }
    }

    synchronized void close() {
        Connection c = CONN.get();
        AtomicInteger refCount = REFCOUNT.get();
        if (c != null) {
            int useCount = refCount.decrementAndGet();
            if (useCount == 0) {
                CONN.remove();
                REFCOUNT.remove();
                try {
                    c.close();
                } catch (SQLException e) {
                    LOG.debug("error closing object: " + c, e);
                }
            }
        }
    }

    /**
     * Hook for sublcasses to run.
     * <p>
     * When creating statements and result sets from the connection ensure that {@link #open} is
     * called in order to track the created object to be closed when the operation is complete.
     * </p>
     * 
     * @param cx The connection to run the op against.
     * 
     * @return The op result, or <code>null</code> for opts that don't return a value.
     * 
     */
    protected abstract T doRun(Connection cx) throws IOException, SQLException;

    /**
     * Subclass hook to determine if the operation runs within a transaction.
     * <p>
     * It is the responsibility of the subclass to either commit or rollback the transaction.
     * </p>
     */
    protected boolean isAutoCommit() {
        return true;
    }
}
