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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.PGStorage.newConnection;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.VERSION;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.inject.Inject;

public class PGRefDatabase implements RefDatabase {

    private static final Logger LOG = LoggerFactory.getLogger(PGRefDatabase.class);

    private Environment config;

    private ConfigDatabase configDB;

    private DataSource dataSource;

    private final String refsTableName;

    private static ThreadLocal<Connection> LockConnection = new ThreadLocal<>();

    @Inject
    public PGRefDatabase(ConfigDatabase configDB, Hints hints) throws URISyntaxException {
        this(configDB, Environment.get(hints));
    }

    public PGRefDatabase(ConfigDatabase configDB, Environment config) {
        Preconditions.checkNotNull(configDB);
        Preconditions.checkNotNull(config);
        // Preconditions.checkArgument(PGStorage.repoExists(config), "Repository %s does not exist",
        // config.repositoryId);
        this.configDB = configDB;
        this.config = config;
        this.refsTableName = config.getTables().refs();
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.configure(configDB, FORMAT_NAME, VERSION);
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.verify(configDB, FORMAT_NAME, VERSION);
    }

    @Override
    public synchronized void create() {
        if (dataSource != null) {
            return;
        }
        dataSource = PGStorage.newDataSource(config);
    }

    @Override
    public synchronized void close() {
        if (dataSource != null) {
            try {
                PGStorage.closeDataSource(dataSource);
            } finally {
                dataSource = null;
            }
        }
    }

    private static final String TABLE_STMT = "CREATE TABLE %s ("
            + "repository TEXT, path TEXT, name TEXT, value TEXT, "
            + "PRIMARY KEY(repository, path, name))";

    @Override
    public void lock() throws TimeoutException {
        lockWithTimeout(30);
    }

    public void lockWithTimeout(int timeout) throws TimeoutException {
        final String repo = PGRefDatabase.this.config.repositoryId;
        Connection c = LockConnection.get();
        if (c == null) {
            c = newConnection(dataSource);
            LockConnection.set(c);
        }
        final String repoTable = PGRefDatabase.this.config.getTables().repositories();
        final String sql = format(
                "SELECT pg_advisory_lock((SELECT lock_id FROM %s WHERE repository=?));", repoTable);
        try (PreparedStatement st = c.prepareStatement(log(sql, LOG, repo))) {
            st.setString(1, repo);
            st.setQueryTimeout(timeout);
            st.executeQuery();
        } catch (SQLException e) {
            LockConnection.remove();
            try {
                c.close();
            } catch (SQLException ex) {
                LOG.debug("error closing object: " + c, ex);
            }
            // executeQuery should throw an SQLTimeoutException when the query times out, but it is
            // actually throwing an SQLException with a message that the query was cancelled.
            if (e.getMessage().contains("canceling")) {
                throw new TimeoutException("The attempt to lock the database timed out.");
            }
            Throwables.propagate(e);
        }
    }

    @Override
    public void unlock() {
        final String repo = PGRefDatabase.this.config.repositoryId;
        Connection c = LockConnection.get();
        if (c != null) {
            final String repoTable = PGRefDatabase.this.config.getTables().repositories();
            final String sql = format(
                    "SELECT pg_advisory_unlock((SELECT lock_id FROM %s WHERE repository=?));",
                    repoTable);
            try (PreparedStatement st = c.prepareStatement(log(sql, LOG, repo))) {
                st.setString(1, repo);
                st.executeQuery();
            } catch (SQLException e) {
                Throwables.propagate(e);
            } finally {
                LockConnection.remove();
                try {
                    c.close();
                } catch (SQLException e) {
                    LOG.debug("error closing object: " + c, e);
                }
            }
        }
    }

    @Override
    public String getRef(String name) {
        checkNotNull(name);

        String value = getInternal(name);
        if (value == null) {
            return null;
        }
        try {
            ObjectId.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw e;
        }
        return value;
    }

    @Override
    public String getSymRef(String name) {
        checkNotNull(name);
        String value = getInternal(name);
        if (value == null) {
            return null;
        }
        if (!value.startsWith("ref: ")) {
            throw new IllegalArgumentException(name + " is not a symbolic ref: '" + value + "'");
        }
        value = value.substring("ref: ".length());
        return value;
    }

    private String getInternal(final String refPath) {
        return new DbOp<String>() {

            @Override
            protected String doRun(Connection cx) throws IOException, SQLException {
                return doGet(refPath, cx);
            }

        }.run(dataSource);
    }

    private String doGet(final String refPath, final Connection cx) throws SQLException {
        final String repo = PGRefDatabase.this.config.repositoryId;
        final String path = Ref.parentPath(refPath) + "/";
        final String localName = Ref.simpleName(refPath);
        final String refsTable = PGRefDatabase.this.refsTableName;
        final String sql = format(
                "SELECT value FROM %s WHERE repository = ? AND path = ? AND name = ?", refsTable);
        try (PreparedStatement st = cx.prepareStatement(log(sql, LOG, repo, path, localName))) {
            st.setString(1, repo);
            st.setString(2, path);
            st.setString(3, localName);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                return null;
            }
        }
    }

    @Override
    public void putRef(String name, String value) {
        putInternal(name, value);
    }

    @Override
    public void putSymRef(String name, String value) {
        checkNotNull(name);
        checkNotNull(value);
        checkArgument(!name.equals(value), "Trying to store cyclic symbolic ref: %s", name);
        checkArgument(!name.startsWith("ref: "),
                "Wrong value, should not contain 'ref: ': %s -> '%s'", name, value);
        value = "ref: " + value;
        putInternal(name, value);
    }

    private void putInternal(final String name, final String value) {
        new DbOp<Void>() {

            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                final String repo = PGRefDatabase.this.config.repositoryId;
                final String path = Ref.parentPath(name) + "/";
                final String localName = Ref.simpleName(name);
                final String refsTable = PGRefDatabase.this.refsTableName;

                final String delete = format(
                        "DELETE FROM %s WHERE repository = ? AND path = ? AND name = ?", refsTable);
                final String insert = format(
                        "INSERT INTO %s (repository, path, name, value) VALUES (?, ?, ?, ?)",
                        refsTable);

                cx.setAutoCommit(false);
                try {
                    try (PreparedStatement ds = cx.prepareStatement(log(delete, LOG, repo, path,
                            localName))) {
                        ds.setString(1, repo);
                        ds.setString(2, path);
                        ds.setString(3, localName);
                        ds.executeUpdate();
                    }
                    try (PreparedStatement is = cx.prepareStatement(log(insert, LOG, repo, path,
                            localName, value))) {
                        is.setString(1, repo);
                        is.setString(2, path);
                        is.setString(3, localName);
                        is.setString(4, value);
                        is.executeUpdate();
                    }
                    cx.commit();
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }
                return null;
            }
        }.run(dataSource);
    }

    @Override
    public String remove(final String refName) {
        return new DbOp<String>() {

            @Override
            protected String doRun(Connection cx) throws IOException, SQLException {
                cx.setAutoCommit(false);
                String oldval;
                int updateCount = 0;
                try {
                    oldval = doGet(refName, cx);
                    if (oldval == null) {
                        cx.rollback();
                    } else {
                        if (oldval.startsWith("ref: ")) {
                            oldval = oldval.substring("ref: ".length());
                        }
                        final String repo = PGRefDatabase.this.config.repositoryId;
                        final String path = Ref.parentPath(refName) + "/";
                        final String localName = Ref.simpleName(refName);
                        final String refsTable = PGRefDatabase.this.refsTableName;
                        final String sql = format(
                                "DELETE FROM %s WHERE repository = ? AND path = ? AND name = ?",
                                refsTable);
                        try (PreparedStatement st = cx.prepareStatement(sql)) {
                            st.setString(1, repo);
                            st.setString(2, path);
                            st.setString(3, localName);
                            updateCount = st.executeUpdate();
                        }
                        cx.commit();
                    }
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }
                return updateCount == 0 ? null : oldval;
            }
        }.run(dataSource);
    }

    @Override
    public Map<String, String> getAll() {
        return getAll("/", Ref.HEADS_PREFIX, Ref.TAGS_PREFIX, Ref.REMOTES_PREFIX);
    }

    @Override
    public Map<String, String> getAll(final String prefix) {
        Preconditions.checkNotNull(prefix, "namespace can't be null");
        return getAll(new String[] { prefix });
    }

    private Map<String, String> getAll(final String... prefixes) {

        return new DbOp<Map<String, String>>() {
            @Override
            protected Map<String, String> doRun(Connection cx) throws IOException, SQLException {
                return doGetall(cx, prefixes);
            }

        }.run(dataSource);
    }

    private Map<String, String> doGetall(Connection cx, final String... prefixes)
            throws SQLException {
        final String repo = PGRefDatabase.this.config.repositoryId;
        final String refsTable = PGRefDatabase.this.refsTableName;

        StringBuilder sql = new StringBuilder("SELECT path, name, value FROM ")//
                .append(refsTable)//
                .append(" WHERE repository = '").append(repo).append("' AND (");
        for (int i = 0; i < prefixes.length; i++) {
            String prefix = prefixes[i];
            sql.append("path LIKE '").append(prefix).append("%' ");
            if (i < prefixes.length - 1) {
                sql.append("OR ");
            }
        }
        sql.append(")");

        Map<String, String> all = new TreeMap<>();

        try (Statement st = cx.createStatement()) {
            try (ResultSet rs = st.executeQuery(log(sql.toString(), LOG))) {
                while (rs.next()) {
                    String path = rs.getString(1);
                    String name = rs.getString(2);
                    String val = rs.getString(3);
                    all.put(Ref.append(path, name), val);
                }
            }
        }
        return all;
    }

    @Override
    public Map<String, String> removeAll(final String namespace) {
        Preconditions.checkNotNull(namespace, "provided namespace is null");

        return new DbOp<Map<String, String>>() {
            @Override
            protected Map<String, String> doRun(Connection cx) throws SQLException {
                cx.setAutoCommit(false);
                Map<String, String> oldvalues;
                try {
                    final String prefix = namespace.endsWith("/") ? namespace : namespace + "/";
                    oldvalues = doGetall(cx, new String[] { prefix });
                    if (oldvalues.isEmpty()) {
                        cx.rollback();
                    } else {
                        final String repo = PGRefDatabase.this.config.repositoryId;
                        final String refsTable = PGRefDatabase.this.refsTableName;
                        String sql = "DELETE FROM " + refsTable
                                + " WHERE repository = ? AND path LIKE '" + prefix + "%'";
                        try (PreparedStatement st = cx
                                .prepareStatement(log(sql, LOG, repo, prefix))) {
                            st.setString(1, repo);
                            st.executeUpdate();
                        }
                        cx.commit();
                    }
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }
                return oldvalues;
            }
        }.run(dataSource);
    }
}
