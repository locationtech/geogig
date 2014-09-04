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

import static java.lang.String.format;
import static org.locationtech.geogig.storage.sqlite.Xerial.log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

/**
 * Object database based on Xerial SQLite jdbc driver.
 * 
 * @author Justin Deoliveira, Boundless
 */
public class XerialObjectDatabase extends SQLiteObjectDatabase<DataSource> {

    static Logger LOG = LoggerFactory.getLogger(XerialObjectDatabase.class);

    static final String OBJECTS = "objects";

    final int partitionSize = 10 * 1000; // TODO make configurable

    final String dbName;

    @Inject
    public XerialObjectDatabase(ConfigDatabase configdb, Platform platform) {
        this(configdb, platform, "objects");
    }

    public XerialObjectDatabase(ConfigDatabase configdb, Platform platform, String dbName) {
        super(configdb, platform);
        this.dbName = dbName;
        // File db = new File(new File(platform.pwd(), ".geogig"), name + ".db");
        // dataSource = Xerial.newDataSource(db);
    }

    @Override
    protected DataSource connect(File geogigDir) {
        return Xerial.newDataSource(new File(geogigDir, dbName + ".db"));
    }

    @Override
    protected void close(DataSource ds) {
    }

    @Override
    public void init(DataSource ds) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws SQLException {
                String sql = format(
                        "CREATE TABLE IF NOT EXISTS %s (id varchar PRIMARY KEY, object blob)",
                        OBJECTS);
                open(cx.createStatement()).execute(log(sql, LOG));
                return null;
            }
        }.run(ds);
    }

    @Override
    public boolean has(final String id, DataSource ds) {
        return new DbOp<Boolean>() {
            @Override
            protected Boolean doRun(Connection cx) throws SQLException {
                String sql = format("SELECT count(*) FROM %s WHERE id = ?", OBJECTS);

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, id)));
                ps.setString(1, id);

                ResultSet rs = open(ps.executeQuery());
                rs.next();

                return rs.getInt(1) > 0;
            }
        }.run(ds);
    }

    @Override
    public Iterable<String> search(final String partialId, DataSource ds) {
        Connection cx = Xerial.newConnection(ds);
        final ResultSet rs = new DbOp<ResultSet>() {
            @Override
            protected ResultSet doRun(Connection cx) throws SQLException {
                String sql = format("SELECT id FROM %s WHERE id LIKE '%%%s%%'", OBJECTS, partialId);
                return cx.createStatement().executeQuery(log(sql, LOG));
            }
        }.run(cx);

        return new StringResultSetIterable(rs, cx);
    }

    @Override
    public InputStream get(final String id, DataSource ds) {
        return new DbOp<InputStream>() {
            @Override
            protected InputStream doRun(Connection cx) throws SQLException {
                String sql = format("SELECT object FROM %s WHERE id = ?", OBJECTS);

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, id)));
                ps.setString(1, id);

                ResultSet rs = open(ps.executeQuery());
                if (!rs.next()) {
                    return null;
                }

                byte[] bytes = rs.getBytes(1);
                return new ByteArrayInputStream(bytes);
            }
        }.run(ds);
    }

    @Override
    public void put(final String id, final InputStream obj, DataSource ds) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws SQLException, IOException {
                String sql = format("INSERT OR IGNORE INTO %s (id,object) VALUES (?,?)", OBJECTS);

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, id, obj)));
                ps.setString(1, id);
                ps.setBytes(2, ByteStreams.toByteArray(obj));
                ps.executeUpdate();

                return null;
            }
        }.run(ds);
    }

    @Override
    public boolean delete(final String id, DataSource ds) {
        return new DbOp<Boolean>() {
            @Override
            protected Boolean doRun(Connection cx) throws SQLException {
                String sql = format("DELETE FROM %s WHERE id = ?", OBJECTS);

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, id)));
                ps.setString(1, id);

                return ps.executeUpdate() > 0;
            }
        }.run(ds);
    }

    /**
     * Override to optimize batch insert.
     */
    @Override
    public void putAll(final Iterator<? extends RevObject> objects, final BulkOpListener listener) {
        Preconditions.checkState(isOpen(), "No open database connection");
        new DbOp<Void>() {
            @Override
            protected boolean isAutoCommit() {
                return false;
            }

            @Override
            protected Void doRun(Connection cx) throws SQLException, IOException {
                // use INSERT OR IGNORE to deal with duplicates cleanly
                String sql = format("INSERT OR IGNORE INTO %s (object,id) VALUES (?,?)", OBJECTS);
                PreparedStatement stmt = open(cx.prepareStatement(log(sql, LOG)));

                // partition the objects into chunks for batch processing
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Iterator<List<? extends RevObject>> it = (Iterator) Iterators.partition(objects,
                        partitionSize);

                while (it.hasNext()) {
                    List<? extends RevObject> objs = it.next();
                    for (RevObject obj : objs) {
                        stmt.setBytes(1, ByteStreams.toByteArray(writeObject(obj)));
                        stmt.setString(2, obj.getId().toString());
                        stmt.addBatch();
                    }

                    notifyInserted(stmt.executeBatch(), objs, listener);
                    stmt.clearParameters();
                }
                cx.commit();

                return null;
            }
        }.run(cx);
    }

    void notifyInserted(int[] inserted, List<? extends RevObject> objects, BulkOpListener listener) {
        for (int i = 0; i < inserted.length; i++) {
            if (inserted[i] > 0) {
                listener.inserted(objects.get(i).getId(), null);
            }
        }
    }

    /**
     * Override to optimize batch delete.
     */
    @Override
    public long deleteAll(final Iterator<ObjectId> ids, final BulkOpListener listener) {
        Preconditions.checkState(isOpen(), "No open database connection");
        return new DbOp<Long>() {
            @Override
            protected boolean isAutoCommit() {
                return false;
            }

            @Override
            protected Long doRun(Connection cx) throws SQLException, IOException {
                String sql = format("DELETE FROM %s WHERE id = ?", OBJECTS);
                PreparedStatement stmt = open(cx.prepareStatement(log(sql, LOG)));

                long count = 0;

                // partition the objects into chunks for batch processing
                Iterator<List<ObjectId>> it = Iterators.partition(ids, partitionSize);

                while (it.hasNext()) {
                    List<ObjectId> l = it.next();
                    for (ObjectId id : l) {
                        stmt.setString(1, id.toString());
                        stmt.addBatch();
                    }

                    count += notifyDeleted(stmt.executeBatch(), l, listener);
                    stmt.clearParameters();
                }
                cx.commit();

                return count;
            }
        }.run(cx);
    }

    long notifyDeleted(int[] deleted, List<ObjectId> ids, BulkOpListener listener) {
        long count = 0;
        for (int i = 0; i < deleted.length; i++) {
            if (deleted[i] > 0) {
                count++;
                listener.deleted(ids.get(i));
            }
        }
        return count;
    }
}
