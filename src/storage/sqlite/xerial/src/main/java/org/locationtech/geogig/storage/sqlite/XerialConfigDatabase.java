/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.sqlite;

import static org.locationtech.geogig.storage.sqlite.Xerial.log;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.api.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

/**
 * Config database based on xerial SQLite jdbc driver.
 * 
 * @author Justin Deoliveira, Boundless
 */
public class XerialConfigDatabase extends SQLiteConfigDatabase {

    static final Logger LOG = LoggerFactory.getLogger(XerialConfigDatabase.class);

    @Inject
    public XerialConfigDatabase(Platform platform) {
        super(platform);
    }

    @Override
    protected String get(final Entry entry, Config config) {
        return new DbOp<String>() {
            @Override
            protected String doRun(Connection cx) throws IOException, SQLException {
                String sql = "SELECT value FROM config WHERE section = ? AND key = ?";

                String s = entry.section;
                String k = entry.key;

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, s, k)));
                ps.setString(1, s);
                ps.setString(2, k);

                ResultSet rs = open(ps.executeQuery());
                return rs.next() ? rs.getString(1) : null;
            }
        }.run(connect(config));
    }

    @Override
    protected Map<String, String> all(Config config) {
        return new DbOp<Map<String, String>>() {
            @Override
            protected Map<String, String> doRun(Connection cx) throws IOException, SQLException {
                String sql = "SELECT section,key,value FROM config";
                ResultSet rs = open(open(cx.createStatement()).executeQuery(log(sql, LOG)));

                Map<String, String> all = Maps.newLinkedHashMap();
                while (rs.next()) {
                    String entry = String.format("%s.%s", rs.getString(1), rs.getString(2));
                    all.put(entry, rs.getString(3));
                }

                return all;
            }
        }.run(connect(config));
    }

    @Override
    protected Map<String, String> all(final String section, Config config) {
        return new DbOp<Map<String, String>>() {
            @Override
            protected Map<String, String> doRun(Connection cx) throws IOException, SQLException {
                String sql = "SELECT section,key,value FROM config WHERE section = ?";

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, section)));
                ps.setString(1, section);

                ResultSet rs = open(ps.executeQuery());

                Map<String, String> all = Maps.newLinkedHashMap();
                while (rs.next()) {
                    String entry = String.format("%.%", rs.getString(1), rs.getString(2));
                    all.put(entry, rs.getString(3));
                }

                return all;
            }
        }.run(connect(config));
    }

    @Override
    protected List<String> list(final String section, Config config) {
        return new DbOp<List<String>>() {
            @Override
            protected List<String> doRun(Connection cx) throws IOException, SQLException {
                String sql = "SELECT key FROM config WHERE section = ?";

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, section)));
                ps.setString(1, section);

                ResultSet rs = open(ps.executeQuery());

                List<String> all = Lists.newArrayList();
                while (rs.next()) {
                    all.add(rs.getString(1));
                }

                return all;
            }

        }.run(connect(config));

    }

    @Override
    protected void put(final Entry entry, final String value, Config config) {
        new DbOp<Void>() {
            @Override
            protected boolean isAutoCommit() {
                return false;
            }

            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                doRemove(entry, cx);

                String sql = "INSERT OR REPLACE INTO config (section,key,value) VALUES (?,?,?)";

                String s = entry.section;
                String k = entry.key;

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, s, k, value)));
                ps.setString(1, s);
                ps.setString(2, k);
                ps.setString(3, value);

                ps.executeUpdate();
                cx.commit();

                return null;
            }
        }.run(connect(config));
    }

    @Override
    protected void remove(final Entry entry, Config config) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                doRemove(entry, cx);
                return null;
            }
        }.run(connect(config));
    }

    void doRemove(final Entry entry, Connection cx) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                String sql = "DELETE FROM config WHERE section = ? AND key = ?";

                String s = entry.section;
                String k = entry.key;

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, s, k)));
                ps.setString(1, s);
                ps.setString(2, k);

                ps.executeUpdate();
                return null;
            }
        }.run(cx);
    }

    @Override
    protected void removeAll(final String section, Config config) {
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                String sql = "DELETE FROM config WHERE section = ?";

                PreparedStatement ps = open(cx.prepareStatement(log(sql, LOG, section)));
                ps.setString(1, section);

                ps.executeUpdate();
                return null;
            }
        }.run(connect(config));
    }

    SQLiteDataSource connect(Config config) {
        SQLiteDataSource dataSource = Xerial.newDataSource(config.file);
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                String sql = "CREATE TABLE IF NOT EXISTS config (section VARCHAR, key VARCHAR, value VARCHAR,"
                        + " PRIMARY KEY (section,key))";

                Statement st = open(cx.createStatement());
                st.execute(log(sql, LOG));

                sql = "CREATE INDEX IF NOT EXISTS config_section_idx ON config (section)";
                st.execute(log(sql, LOG));

                return null;
            }
        }.run(dataSource);

        return dataSource;
    }
}
