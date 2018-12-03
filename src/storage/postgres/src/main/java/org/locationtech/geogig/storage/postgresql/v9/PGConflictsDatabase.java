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
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.log;

import java.net.URISyntaxException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

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
public class PGConflictsDatabase implements ConflictsDatabase {

    final static Logger LOG = LoggerFactory.getLogger(PGConflictsDatabase.class);

    private static final String NULL_NAMESPACE = "";

    private final Environment environment;

    private final String conflictsTable;

    private int repositoryId;

    @VisibleForTesting
    DataSource dataSource;

    /**
     * @param hints
     * @throws IllegalArgumentException if {@link Hints#REPOSITORY_URL} is not given in hints
     * @throws URISyntaxException
     */
    @Inject
    public PGConflictsDatabase(Hints hints) throws IllegalArgumentException, URISyntaxException {
        checkNotNull(hints);
        environment = Environment.get(hints);
        Preconditions.checkNotNull(environment.getRepositoryName(), "Repository id not provided");
        conflictsTable = environment.getTables().conflicts();
        repositoryId = Environment.REPOSITORY_ID_UNSET;
    }

    @VisibleForTesting
    PGConflictsDatabase(DataSource mockSource) {
        dataSource = mockSource;
        environment = null;
        conflictsTable = "geogig_conflicts";
    }

    public @Override synchronized void open() {
        if (dataSource == null) {
            dataSource = PGStorage.newDataSource(environment);
            boolean repoExists = PGStorage.repoExists(environment);
            if (!repoExists) {
                close();
                throw new IllegalArgumentException(String.format("Repository %s does not exist",
                        environment.getRepositoryName()));
            }
            repositoryId = environment.getRepositoryId();
        }
    }

    public @Override synchronized void close() {
        if (dataSource != null) {
            try {
                PGStorage.closeDataSource(dataSource);
            } finally {
                dataSource = null;
            }
        }
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

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            log(sql, LOG, namespace, path, conflict);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setInt(1, repositoryId);
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addConflicts(@Nullable String ns, Iterable<Conflict> conflicts) {
        Preconditions.checkNotNull(conflicts);
        final String namespace = namespace(ns);

        final String sql = format(
                "INSERT INTO %s (repository, namespace, path, ancestor, ours, theirs) VALUES (?,?,?,?,?,?)",
                conflictsTable);

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                for (Conflict conflict : conflicts) {
                    final String path = conflict.getPath();
                    Preconditions.checkNotNull(path);

                    ps.setInt(1, repositoryId);
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
                    ps.addBatch();
                }
                ps.executeBatch();
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
    public Optional<Conflict> getConflict(@Nullable String namespace, String path) {
        checkNotNull(path);
        namespace = namespace(namespace);
        final String sql;
        {
            StringBuilder sb = new StringBuilder("SELECT path,ancestor,ours,theirs FROM ")
                    .append(conflictsTable)
                    .append(" WHERE repository = ? AND namespace = ? AND path = ?");
            sql = sb.toString();
        }

        Conflict conflict = null;

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setInt(1, repositoryId);
                ps.setString(2, namespace(namespace));
                ps.setString(3, path);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        @Nullable
                        byte[] ancestorb = rs.getBytes(2);
                        ObjectId ancestor = ancestorb == null ? ObjectId.NULL
                                : ObjectId.create(ancestorb);
                        ObjectId ours = ObjectId.create(rs.getBytes(3));
                        ObjectId theirs = ObjectId.create(rs.getBytes(4));
                        conflict = new Conflict(path, ancestor, ours, theirs);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.fromNullable(conflict);
    }

    @Override
    public boolean hasConflicts(@Nullable final String namespace) {
        final String sql = format(
                "SELECT TRUE WHERE EXISTS ( SELECT 1 FROM %s WHERE repository = ? AND namespace = ? )",
                conflictsTable);

        boolean hasConflicts;
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, namespace))) {
                ps.setInt(1, repositoryId);
                ps.setString(2, namespace(namespace));
                try (ResultSet rs = ps.executeQuery()) {
                    hasConflicts = rs.next();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return hasConflicts;
    }

    @Override
    public Iterator<Conflict> getByPrefix(@Nullable String namespace, @Nullable String treePath) {
        return new ConflictsIterator(this, namespace, treePath);
    }

    List<Conflict> getBatch(@Nullable String namespace, @Nullable String treePath, int offset,
            int limit) throws SQLException {

        checkArgument(offset >= 0);
        checkArgument(limit > 0);

        final String sql;
        {
            StringBuilder sb = new StringBuilder("SELECT path,ancestor,ours,theirs FROM ")
                    .append(conflictsTable).append(" WHERE repository = ? AND namespace = ?");
            if (treePath != null) {
                sb.append(" AND (path = ? OR path LIKE ?)");
            }
            sb.append(" ORDER BY repository, namespace, path OFFSET ").append(offset)
                    .append(" LIMIT ").append(limit);
            sql = sb.toString();
        }

        List<Conflict> batch = new ArrayList<>();
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setInt(1, repositoryId);
                ps.setString(2, namespace(namespace));
                if (treePath != null) {
                    ps.setString(3, treePath);
                    ps.setString(4, treePath + "/%");
                }
                log(sql, LOG, repositoryId, namespace);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String path = rs.getString(1);
                        @Nullable
                        byte[] ancestorb = rs.getBytes(2);
                        ObjectId ancestor = ancestorb == null ? ObjectId.NULL
                                : ObjectId.create(ancestorb);
                        ObjectId ours = ObjectId.create(rs.getBytes(3));
                        ObjectId theirs = ObjectId.create(rs.getBytes(4));
                        batch.add(new Conflict(path, ancestor, ours, theirs));
                    }
                }
            }
        }
        return batch;
    }

    private static class ConflictsIterator extends AbstractIterator<Conflict> {

        private final PGConflictsDatabase db;

        private final String namespace;

        private final String treePath;

        private final int pageSize = 1000;

        private int offset = 0;

        private int currentPageSize;

        private Iterator<Conflict> page;

        public ConflictsIterator(PGConflictsDatabase db, @Nullable String namespace,
                @Nullable String treePath) {
            this.db = db;
            this.namespace = namespace;
            this.treePath = treePath;
            this.page = nextPage();
        }

        @Override
        protected Conflict computeNext() {
            if (page.hasNext()) {
                return page.next();
            }
            if (currentPageSize < pageSize) {
                return endOfData();
            }
            page = nextPage();
            return computeNext();
        }

        private Iterator<Conflict> nextPage() {
            List<Conflict> batch;
            try {
                batch = db.getBatch(namespace, treePath, offset, pageSize);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            this.offset += pageSize;
            this.currentPageSize = batch.size();
            return batch.iterator();
        }
    }

    @Override
    public long getCountByPrefix(@Nullable String namespace, @Nullable String treePath) {
        namespace = namespace(namespace);

        final String sql;
        if (null == treePath) {
            sql = format("SELECT count(*) FROM %s WHERE repository = ? AND namespace = ?",
                    conflictsTable);
        } else {
            sql = format("SELECT count(*) FROM %s WHERE repository = ? AND namespace = ? "
                    + "AND (path = ? OR path LIKE ?)", conflictsTable);
        }

        int count;
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(log(sql, LOG, namespace))) {
                ps.setInt(1, repositoryId);
                ps.setString(2, namespace);
                if (null != treePath) {
                    String likeArg = treePath + "/%";
                    ps.setString(3, treePath);
                    ps.setString(4, likeArg);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    Preconditions.checkState(rs.next());// count returns always a record
                    count = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return count;
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

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setInt(1, repositoryId);
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeConflicts(final @Nullable String ns, final Iterable<String> paths) {
        checkNotNull(paths, "paths is null");
        final String namespace = namespace(ns);

        final String sql = format(
                "DELETE FROM %s WHERE repository = ? AND namespace = ? AND path = ANY(?)",
                conflictsTable);

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                final int partitionSize = 1000;
                Iterable<List<String>> partitions = Iterables.partition(paths, partitionSize);
                for (List<String> partition : partitions) {
                    String[] pathsArg = partition.toArray(new String[partition.size()]);
                    Array array = cx.createArrayOf("varchar", pathsArg);

                    ps.clearParameters();
                    ps.setInt(1, repositoryId);
                    ps.setString(2, namespace);
                    ps.setArray(3, array);
                    ps.executeUpdate();
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
    public void removeConflicts(@Nullable final String ns) {
        final String namespace = namespace(ns);
        final String sql;
        sql = format("DELETE FROM %s WHERE repository = ? AND namespace = ?", conflictsTable);
        log(sql, LOG, namespace);

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setInt(1, repositoryId);
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> findConflicts(@Nullable String namespace, Set<String> paths) {
        checkNotNull(paths, "paths is null");

        Set<String> matches = new HashSet<>();

        namespace = namespace(namespace);

        final int partitionSize = 1000;

        final String sql = format(
                "SELECT path FROM %s WHERE repository = ? AND namespace = ? AND path = ANY(?)",
                conflictsTable);

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            cx.setAutoCommit(true);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                Iterable<List<String>> partitions = Iterables.partition(paths, partitionSize);
                for (List<String> partition : partitions) {
                    String[] pathsArg = partition.toArray(new String[partition.size()]);
                    Array array = cx.createArrayOf("varchar", pathsArg);

                    ps.clearParameters();
                    ps.setInt(1, repositoryId);
                    ps.setString(2, namespace);
                    ps.setArray(3, array);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            matches.add(rs.getString(1));
                        }
                    }
                }
            } catch (SQLException e) {
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return matches;
    }

    @Override
    public void removeByPrefix(@Nullable String namespace, @Nullable String pathPrefix) {

        namespace = namespace(namespace);

        final String sql;
        {
            StringBuilder sb = new StringBuilder("DELETE FROM ").append(conflictsTable)
                    .append(" WHERE repository = ? AND namespace = ?");
            if (pathPrefix != null) {
                sb.append(" AND (path = ? OR path LIKE ?)");
            }
            sql = sb.toString();
        }

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                ps.setInt(1, repositoryId);
                ps.setString(2, namespace(namespace));
                if (pathPrefix != null) {
                    ps.setString(3, pathPrefix);
                    ps.setString(4, pathPrefix + "/%");
                }
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
