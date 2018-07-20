/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.FORMAT_NAME;
import static org.locationtech.geogig.storage.postgresql.PGStorageProvider.VERSION;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.newConnection;

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

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
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
        Preconditions.checkArgument(PGStorage.repoExists(config), "Repository %s does not exist",
                config.getRepositoryName());
        Preconditions.checkState(config.isRepositorySet());
        this.configDB = configDB;
        this.config = config;
        this.refsTableName = config.getTables().refs();
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.OBJECT.configure(configDB, FORMAT_NAME, VERSION);
    }

    @Override
    public boolean checkConfig() throws RepositoryConnectionException {
        return StorageType.OBJECT.verify(configDB, FORMAT_NAME, VERSION);
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

    @Override
    public void lock() throws TimeoutException {
        lockWithTimeout(30);
    }

    @VisibleForTesting
    void lockWithTimeout(int timeout) throws TimeoutException {
        final int repo = config.getRepositoryId();
        Connection c = LockConnection.get();
        if (c == null) {
            c = newConnection(dataSource);
            LockConnection.set(c);
        }
        final String repoTable = config.getTables().repositories();
        final String sql = format(
                "SELECT pg_advisory_lock((SELECT repository FROM %s WHERE repository=?));",
                repoTable);
        try (PreparedStatement st = c.prepareStatement(log(sql, LOG, repo))) {
            st.setInt(1, repo);
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public void unlock() {
        final int repo = config.getRepositoryId();
        Connection c = LockConnection.get();
        if (c != null) {
            final String repoTable = config.getTables().repositories();
            final String sql = format(
                    "SELECT pg_advisory_unlock((SELECT repository FROM %s WHERE repository=?));",
                    repoTable);
            try (PreparedStatement st = c.prepareStatement(log(sql, LOG, repo))) {
                st.setInt(1, repo);
                st.executeQuery();
            } catch (SQLException e) {
                throw new RuntimeException(e);
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
        value = unmaskSymRef(value);
        return value;
    }

    private String unmaskSymRef(String value) {
        if (value.startsWith("ref: ")) {
            value = value.substring("ref: ".length());
        }
        return value;
    }

    private String getInternal(final String refPath) {
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            return doGet(refPath, cx);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String doGet(final String refPath, final Connection cx) throws SQLException {
        final int repo = config.getRepositoryId();
        final String path = Ref.parentPath(refPath) + "/";
        final String localName = Ref.simpleName(refPath);
        final String refsTable = refsTableName;
        final String sql = format(
                "SELECT value FROM %s WHERE repository = ? AND path = ? AND name = ?", refsTable);
        try (PreparedStatement st = cx.prepareStatement(log(sql, LOG, repo, path, localName))) {
            st.setInt(1, repo);
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
        Preconditions.checkState(config.isRepositorySet());
        final int repo = config.getRepositoryId();
        final String path = Ref.parentPath(name) + "/";
        final String localName = Ref.simpleName(name);
        final String refsTable = refsTableName;

        final String delete = format(
                "DELETE FROM %s WHERE repository = ? AND path = ? AND name = ?", refsTable);
        final String insert = format(
                "INSERT INTO %s (repository, path, name, value) VALUES (?, ?, ?, ?)", refsTable);

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try {
                try (PreparedStatement ds = cx
                        .prepareStatement(log(delete, LOG, repo, path, localName))) {
                    ds.setInt(1, repo);
                    ds.setString(2, path);
                    ds.setString(3, localName);
                    ds.executeUpdate();
                }
                try (PreparedStatement is = cx
                        .prepareStatement(log(insert, LOG, repo, path, localName, value))) {
                    is.setInt(1, repo);
                    is.setString(2, path);
                    is.setString(3, localName);
                    is.setString(4, value);
                    is.executeUpdate();
                }
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String remove(final String refName) {
        final int repo = config.getRepositoryId();
        final String path = Ref.parentPath(refName) + "/";
        final String localName = Ref.simpleName(refName);
        final String refsTable = refsTableName;
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            String oldval;
            int updateCount = 0;
            try {
                oldval = doGet(refName, cx);
                if (oldval == null) {
                    cx.rollback();
                } else {
                    if (oldval.startsWith("ref: ")) {
                        oldval = unmaskSymRef(oldval);
                    }
                    final String sql = format(
                            "DELETE FROM %s WHERE repository = ? AND path = ? AND name = ?",
                            refsTable);
                    try (PreparedStatement st = cx.prepareStatement(sql)) {
                        st.setInt(1, repo);
                        st.setString(2, path);
                        st.setString(3, localName);
                        updateCount = st.executeUpdate();
                    }
                    cx.commit();
                }
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
            return updateCount == 0 ? null : oldval;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            return doGetall(cx, prefixes);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> doGetall(Connection cx, final String... prefixes)
            throws SQLException {
        final int repo = config.getRepositoryId();
        final String refsTable = refsTableName;

        StringBuilder sql = new StringBuilder("SELECT path, name, value FROM ")//
                .append(refsTable)//
                .append(" WHERE repository = ").append(repo).append(" AND (");
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
                    String val = unmaskSymRef(rs.getString(3));
                    all.put(Ref.append(path, name), val);
                }
            }
        }
        return all;
    }

    @Override
    public Map<String, String> removeAll(final String namespace) {
        Preconditions.checkNotNull(namespace, "provided namespace is null");
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            Map<String, String> oldvalues;
            try {
                final String prefix = namespace.endsWith("/") ? namespace : namespace + "/";
                oldvalues = doGetall(cx, new String[] { prefix });
                if (oldvalues.isEmpty()) {
                    cx.rollback();
                } else {
                    final int repo = config.getRepositoryId();
                    final String refsTable = refsTableName;
                    String sql = "DELETE FROM " + refsTable
                            + " WHERE repository = ? AND path LIKE '" + prefix + "%'";
                    try (PreparedStatement st = cx.prepareStatement(log(sql, LOG, repo, prefix))) {
                        st.setInt(1, repo);
                        st.executeUpdate();
                    }
                    cx.commit();
                }
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
            return oldvalues;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
