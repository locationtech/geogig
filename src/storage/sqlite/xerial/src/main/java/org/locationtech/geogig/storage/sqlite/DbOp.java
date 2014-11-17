/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.sqlite;

import static org.locationtech.geogig.storage.sqlite.XerialSQLiteModule.LOG;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;

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
 * @author Justin Deoliveira, Boundless
 * 
 * @param <T> Operation return type.
 */
public abstract class DbOp<T> {

    Deque<Object> open = new ArrayDeque<Object>();

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
            return run(open(ds.getConnection()));
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
    public final T run(Connection cx) {
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

    /**
     * Tracks a resource to be closed in LIFO order.
     * 
     * @param obj The object to be closed.
     * 
     * @return The original object.
     */
    protected <X> X open(X obj) {
        open.push(obj);
        return obj;
    }

    void close() {
        while (!open.isEmpty()) {
            Object obj = open.pop();
            try {
                if (obj instanceof ResultSet) {
                    ((ResultSet) obj).close();
                }
                if (obj instanceof Statement) {
                    ((Statement) obj).close();
                }
                if (obj instanceof Connection) {
                    ((Connection) obj).close();
                }
            } catch (Exception e) {
                LOG.debug("error closing object: " + obj, e);
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
