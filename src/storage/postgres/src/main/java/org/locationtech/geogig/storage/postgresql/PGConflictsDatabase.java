/* Copyright (c) 2015-2016 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;
import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.PGStorage.log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * {@link ConflictsDatabase} implementation for PostgreSQL.
 * <p>
 * Stores {@link Conflict conflicts} on the following table structure:
 * 
 * <pre>
 * CREATE TABLE geogig_conflict (
 *         repository TEXT, namespace TEXT, path TEXT, ancestor bytea, ours bytea NOT NULL, theirs bytea NOT NULL,
 *         PRIMARY KEY(repository, namespace, path),
 *         FOREIGN KEY (repository) REFERENCES geogig_repository(repository) ON DELETE CASCADE
 *         )
 * </pre>
 * <p>
 * Where table fields map to Conflict as:
 * <ul>
 * <li>{@link Conflict#getPath()} -> {@code path}
 * <li>{@link Conflict#getAncestor()} -> {@code ancestor}
 * <li>{@link Conflict#getOurs()} -> {@code ours}
 * <li>{@link Conflict#getTheirs()} -> {@code theirs}
 * </ul>
 * The {@code repository} columns matches the repository identifier, the {@code namespace} column
 * identifies the geogig {@link GeogigTransaction#getTransactionId() transaction id} on which the
 * conflicts are encountered, and default to the empty string if the conflicts are on the
 * repository's head instead of inside a transaction.
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
    public void addConflict(@Nullable String ns, final Conflict conflict) {
        Preconditions.checkNotNull(conflict);
        final String path = conflict.getPath();
        Preconditions.checkNotNull(path);

        final String namespace = namespace(ns);
        final String sql = format(
                "INSERT INTO %s (repository, namespace, path, ancestor, ours, theirs) VALUES (?,?,?,?,?,?)",
                conflictsTable);

        try (Connection cx = dataSource.getConnection()) {
            cx.setAutoCommit(false);
            log(sql, LOG, namespace, path, conflict);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setString(1, repositoryId);
                ps.setString(2, namespace);
                ps.setString(3, path);
                ObjectId ancestor = conflict.getAncestor();
                if (ancestor.isNull()) {
                    ps.setNull(4, java.sql.Types.OTHER, "bytea");
                } else {
                    ps.setBytes(4, ancestor.getRawValue());
                }
                ps.setBytes(5, conflict.getOurs().getRawValue());
                ps.setBytes(6, conflict.getTheirs().getRawValue());

                ps.executeUpdate();
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
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
    public boolean hasConflicts(@Nullable final String namespace) {
        int count = count(namespace(namespace));
        return count > 0;
    }

    @Override
    public List<Conflict> getConflicts(final @Nullable String ns,
            final @Nullable String pathFilter) {

        final String namespace = namespace(ns);

        final String sql;
        if (pathFilter == null) {
            sql = format(
                    "SELECT path,ancestor,ours,theirs FROM %s WHERE repository = ? AND namespace = ?",
                    conflictsTable);
        } else {
            sql = format(
                    "SELECT path,ancestor,ours,theirs FROM %s WHERE repository = ? AND namespace = ? AND path LIKE ?",
                    conflictsTable);
        }
        List<Conflict> conflicts = new ArrayList<>();
        try (Connection cx = dataSource.getConnection()) {
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setString(1, repositoryId);
                ps.setString(2, namespace);
                if (pathFilter != null) {
                    ps.setString(3, "%" + pathFilter + "%");
                }
                log(sql, LOG, repositoryId, namespace);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String path = rs.getString(1);
                        @Nullable
                        byte[] ancestorb = rs.getBytes(2);
                        ObjectId ancestor;
                        if (ancestorb == null) {
                            ancestor = ObjectId.NULL;
                        } else {
                            ancestor = ObjectId.createNoClone(ancestorb);
                        }
                        ObjectId ours = ObjectId.createNoClone(rs.getBytes(3));
                        ObjectId theirs = ObjectId.createNoClone(rs.getBytes(4));
                        conflicts.add(new Conflict(path, ancestor, ours, theirs));
                    }
                }
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
        return conflicts;
    }

    private String namespace(String namespace) {
        return namespace == null ? NULL_NAMESPACE : namespace;
    }

    @Override
    public void removeConflict(final @Nullable String ns, final String path) {
        checkNotNull(path, "path is null");
        final String namespace = namespace(ns);

        final String sql = format(
                "DELETE FROM %s WHERE repository = ? AND namespace = ? AND path = ?",
                conflictsTable);
        log(sql, LOG, namespace, path);

        try (Connection cx = dataSource.getConnection()) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setString(1, repositoryId);
                ps.setString(2, namespace);
                ps.setString(3, path);
                ps.executeUpdate();
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    @Override
    public void removeConflicts(@Nullable final String ns) {
        final String namespace = namespace(ns);
        final String sql;
        sql = format("DELETE FROM %s WHERE repository = ? AND namespace = ?", conflictsTable);
        log(sql, LOG, namespace);

        try (Connection cx = dataSource.getConnection()) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setString(1, repositoryId);
                ps.setString(2, namespace);
                ps.executeUpdate();
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
    }

    /**
     * Returns the number of conflicts matching the specified namespace filter.
     * 
     * @param namespace Namespace value, may be <code>null</code>.
     * 
     */
    private int count(final String namespace) {
        final String sql = format("SELECT count(*) FROM %s WHERE repository = ? AND namespace = ?",
                conflictsTable);

        int count;
        try (Connection cx = dataSource.getConnection()) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, namespace))) {
                ps.setString(1, repositoryId);
                ps.setString(2, namespace);
                try (ResultSet rs = ps.executeQuery()) {
                    Preconditions.checkState(rs.next());// count returns always a record
                    count = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw propagate(e);
        }
        return count;
    }

}
