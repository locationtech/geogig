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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * {@link ConflictsDatabase} implementation for PostgreSQL.
 */
class PGConflictsDatabase implements ConflictsDatabase {

    final static Logger LOG = LoggerFactory.getLogger(PGConflictsDatabase.class);

    private static final String NULL_NAMESPACE = "";

    private final String conflictsTable;

    private final String repositoryId;

    private DataSource dataSource;

    public PGConflictsDatabase(final DataSource dataSource, final String conflictsTable,
            final String repositoryId) {
        this.dataSource = dataSource;
        this.conflictsTable = conflictsTable;
        this.repositoryId = repositoryId;
    }

    @Override
    public void addConflict(String ns, final Conflict conflict) {
        Preconditions.checkNotNull(conflict);
        final String path = conflict.getPath();
        Preconditions.checkNotNull(path);

        final String namespace = namespace(ns);

        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                String sql = format(
                        "INSERT INTO %s (repository, namespace, path, conflict) VALUES (?,?,?,?)",
                        conflictsTable);

                log(sql, LOG, namespace, path, conflict);

                cx.setAutoCommit(false);
                try (PreparedStatement ps = cx.prepareStatement(sql)) {
                    ps.setString(1, repositoryId);
                    ps.setString(2, namespace);
                    ps.setString(3, path);
                    String conflictStr = conflict.toString();
                    ps.setString(4, conflictStr);

                    ps.executeUpdate();
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
    public Optional<Conflict> getConflict(@Nullable String namespace, String path) {
        List<Conflict> conflicts = getConflicts(namespace(namespace), path);
        if (conflicts.isEmpty()) {
            return Optional.absent();
        }
        return Optional.of(conflicts.get(0));
    }

    @Override
    public boolean hasConflicts(String namespace) {
        int count = count(namespace(namespace));
        return count > 0;
    }

    @Override
    public List<Conflict> getConflicts(final @Nullable String ns, final @Nullable String pathFilter) {

        final String namespace = namespace(ns);

        List<Conflict> rs = new DbOp<List<Conflict>>() {
            @Override
            protected List<Conflict> doRun(Connection cx) throws IOException, SQLException {
                final String sql;
                if (pathFilter == null) {
                    sql = format("SELECT conflict FROM %s WHERE repository = ? AND namespace = ?",
                            conflictsTable);
                } else {
                    sql = format(
                            "SELECT conflict FROM %s WHERE repository = ? AND namespace = ? AND path LIKE ?",
                            conflictsTable);
                }
                List<Conflict> conflicts = new ArrayList<>();
                try (PreparedStatement ps = cx.prepareStatement(sql)) {
                    ps.setString(1, repositoryId);
                    ps.setString(2, namespace);
                    if (pathFilter != null) {
                        ps.setString(3, "%" + pathFilter + "%");
                    }
                    log(sql, LOG, repositoryId, namespace);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String conflictStr = rs.getString(1);
                            conflicts.add(Conflict.valueOf(conflictStr));
                        }
                    }
                }
                return conflicts;
            }
        }.run(dataSource);
        return rs;
    }

    private String namespace(String namespace) {
        return namespace == null ? NULL_NAMESPACE : namespace;
    }

    @Override
    public void removeConflict(final @Nullable String ns, final @Nullable String path) {
        final String namespace = namespace(ns);

        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                final String sql;
                if (path == null) {
                    sql = format("DELETE FROM %s WHERE repository = ? AND namespace = ?",
                            conflictsTable);
                    log(sql, LOG, namespace);
                } else {
                    sql = format(
                            "DELETE FROM %s WHERE repository = ? AND namespace = ? AND path = ?",
                            conflictsTable);
                    log(sql, LOG, namespace, path);
                }

                cx.setAutoCommit(false);
                try (PreparedStatement ps = cx.prepareStatement(sql)) {
                    ps.setString(1, repositoryId);
                    ps.setString(2, namespace);
                    if (path != null) {
                        ps.setString(3, path);
                    }
                    ps.executeUpdate();
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
    public void removeConflicts(String ns) {
        final String namespace = namespace(ns);
        new DbOp<Void>() {
            @Override
            protected Void doRun(Connection cx) throws IOException, SQLException {
                final String sql;
                sql = format("DELETE FROM %s WHERE repository = ? AND namespace = ?",
                        conflictsTable);
                log(sql, LOG, namespace);

                cx.setAutoCommit(false);
                try (PreparedStatement ps = cx.prepareStatement(sql)) {
                    ps.setString(1, repositoryId);
                    ps.setString(2, namespace);
                    ps.executeUpdate();
                    cx.commit();
                } catch (SQLException e) {
                    cx.rollback();
                    throw e;
                }

                return null;
            }
        }.run(dataSource);
    }

    /**
     * Returns the number of conflicts matching the specified namespace filter.
     * 
     * @param namespace Namespace value, may be <code>null</code>.
     * 
     */
    private int count(final String namespace) {
        Integer count = new DbOp<Integer>() {
            @Override
            protected Integer doRun(Connection cx) throws IOException, SQLException {
                String sql = format(
                        "SELECT count(*) FROM %s WHERE repository = ? AND namespace = ?",
                        conflictsTable);

                int count = 0;
                try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, namespace))) {
                    ps.setString(1, repositoryId);
                    ps.setString(2, namespace);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            count = rs.getInt(1);
                        }
                    }
                }
                return Integer.valueOf(count);
            }
        }.run(dataSource);

        return count.intValue();
    }

}
