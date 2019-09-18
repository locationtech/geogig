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

import static java.lang.String.format;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.log;
import static org.locationtech.geogig.storage.postgresql.config.PGStorage.newConnection;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.AbstractStore;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import lombok.NonNull;

public class PGRefDatabase extends AbstractStore implements RefDatabase {

    private static final Logger LOG = LoggerFactory.getLogger(PGRefDatabase.class);

    private static ThreadLocal<Connection> LockConnection = new ThreadLocal<>();

    private Environment config;

    private DataSource dataSource;

    private final PGRefDatabaseWorker worker;

    public PGRefDatabase(Hints hints) throws URISyntaxException {
        super(Hints.isRepoReadOnly(hints));
        Environment config = Environment.get(hints);

        Preconditions.checkArgument(PGStorage.repoExists(config), "Repository %s does not exist",
                config.getRepositoryName());
        Preconditions.checkState(config.isRepositorySet());

        this.config = config;
        this.worker = new PGRefDatabaseWorker(config);
    }

    public @Override void checkOpen() {
        super.checkOpen();
        Preconditions.checkState(config.isRepositorySet(), "Repository ID is not set");
    }

    public @Override synchronized void open() {
        if (!isOpen()) {
            dataSource = PGStorage.newDataSource(config);
            super.open();
        }
    }

    public @Override synchronized void close() {
        super.close();
        if (dataSource != null) {
            try {
                PGStorage.closeDataSource(dataSource);
            } finally {
                dataSource = null;
            }
        }
    }

    public @Override void lock() throws TimeoutException {
        checkWritable();
        lockWithTimeout(30);
    }

    @VisibleForTesting
    void lockWithTimeout(int timeout) throws TimeoutException {
        Preconditions.checkState(config.isRepositorySet());
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

    public @Override void unlock() {
        checkWritable();

        final int repo = config.getRepositoryId();

        final String repoTable = config.getTables().repositories();
        final String sql = format(
                "SELECT pg_advisory_unlock((SELECT repository FROM %s WHERE repository=?));",
                repoTable);

        Connection c = LockConnection.get();
        if (c != null) {
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

    public @Override @NonNull RefChange put(@NonNull Ref ref) {
        checkWritable();
        return runInTransaction(conn -> worker.put(conn, ref));
    }

    public @Override @NonNull RefChange putRef(@NonNull String name, @NonNull ObjectId value) {
        checkWritable();
        return put(new Ref(name, value));
    }

    public @Override @NonNull RefChange putSymRef(@NonNull String name, @NonNull String target) {
        checkWritable();
        Optional<Ref> t = get(target);
        Preconditions.checkArgument(t.isPresent(), "Target ref %s does not exist for symref %s",
                target, name);
        return put(new SymRef(name, t.get()));
    }

    public @Override @NonNull List<RefChange> putAll(@NonNull Iterable<Ref> refs) {
        checkWritable();
        List<Ref> list = refs instanceof List ? (List<Ref>) refs : Lists.newArrayList(refs);
        return runInTransaction(c -> worker.putAll(c, list));
    }

    public @Override Optional<Ref> get(@NonNull String name) {
        checkOpen();
        return run(conn -> worker.get(conn, name));
    }

    public @Override @NonNull RefChange delete(@NonNull Ref ref) {
        checkWritable();
        return delete(ref.getName());
    }

    public @Override @NonNull RefChange delete(@NonNull String refName) {
        checkWritable();
        List<RefChange> removed = delete(Collections.singletonList(refName));
        return removed.get(0);
    }

    public @Override @NonNull List<RefChange> delete(@NonNull Iterable<String> refNames) {
        checkWritable();
        return runInTransaction(conn -> worker.deleteByName(conn, refNames));
    }

    public @Override @NonNull List<Ref> deleteAll() {
        checkWritable();
        return runInTransaction(c -> worker.deleteByPrefix(c, Arrays.asList("")));
    }

    public @Override List<Ref> deleteAll(@NonNull String namespace) {
        checkWritable();
        return runInTransaction(c -> worker.deleteByPrefix(c, Arrays.asList(namespace)));
    }

    public @Override List<Ref> getAll() {
        checkOpen();
        return getAllByPrefix("", Ref.HEADS_PREFIX, Ref.TAGS_PREFIX, Ref.REMOTES_PREFIX);
    }

    public @Override List<Ref> getAllPresent(@NonNull Iterable<String> names) {
        return run(c -> worker.getPresent(c, names));
    }

    public @Override List<Ref> getAll(final @NonNull String prefix) {
        checkOpen();
        return getAllByPrefix(prefix);
    }

    private List<Ref> getAllByPrefix(@NonNull String... prefixes) {
        return run(conn -> worker.getByPrefix(conn, Arrays.asList(prefixes)));
    }

    private static @FunctionalInterface interface Command<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> T run(Command<T> cmd) {
        T result;
        try (Connection conn = dataSource.getConnection()) {
            result = cmd.run(conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private <T> T runInTransaction(Command<T> cmd) {
        T result;
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                result = cmd.run(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                Throwables.throwIfInstanceOf(e, SQLException.class);
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

}
