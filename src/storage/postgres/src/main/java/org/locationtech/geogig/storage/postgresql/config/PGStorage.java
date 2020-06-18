/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.config;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.locationtech.geogig.storage.postgresql.v9.PGConfigDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import lombok.NonNull;

/**
 * Utility class for PostgreSQL storage.
 */
public class PGStorage {

    static final Logger LOG = LoggerFactory.getLogger(PGStorage.class);

    /**
     * Logs a (prepared) sql statement.
     * 
     * @param sql Base sql to log.
     * @param log The logger object.
     * @param args Optional arguments to the statement.
     * 
     * @return The original statement.
     */
    public static String log(String sql, Logger log, Object... args) {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder(sql);
            if (args.length > 0) {
                sb.append(";");
                for (int i = 0; i < args.length; i++) {
                    sb.append(i).append("=").append(args[i]).append(", ");
                }
                sb.setLength(sb.length() - 2);
            }
            String statement = sb.toString();
            if (!statement.trim().endsWith(";")) {
                statement += ";";
            }
            log.debug(statement);
        }
        return sql;
    }

    public static void verifyDatabaseCompatibility(DataSource dataSource, Environment config) {
        try (Connection cx = dataSource.getConnection()) {
            Version version = getServerVersion(cx);
            PGStorageTableManager tableManager = PGStorageTableManager.forVersion(version);
            tableManager.checkCompatibility(cx, config);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static Connection newConnection(DataSource ds) {
        try {
            Connection connection = ds.getConnection();
            return connection;
        } catch (SQLTransientConnectionException e) {
            throw new RepositoryBusyException("No available connections to the repository.", e);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to obatain connection: " + e.getMessage(), e);
        }
    }

    public static SQLException rollbackAndRethrow(Connection c, Exception e) throws SQLException {
        c.rollback();
        if (e instanceof SQLException) {
            throw (SQLException) e;
        }
        throw new RuntimeException(e);
    }

    public static boolean repoExists(final @NonNull Environment env)
            throws IllegalArgumentException {
        checkArgument(env.getRepositoryName() != null, "no repository name provided");

        Optional<Integer> repoPK;
        try (PGConfigDatabase configdb = new PGConfigDatabase(env)) {
            if (env.isRepositoryIdSet()) {
                String configuredName = configdb.get("repo.name").orElse(null);
                Preconditions.checkState(env.getRepositoryName().equals(configuredName));
                return true;
            } else {
                repoPK = env.resolveRepositoryPK();
                if (repoPK.isPresent()) {
                    if (env.isRepositoryIdSet()) {
                        if (repoPK.get() != env.getRepositoryId()) {
                            String msg = format("The provided primary key for the repository '%s' "
                                    + "does not match the one in the database. Provided: %d, returned: %d. "
                                    + "Check the consistency of the repo.name config property for those repository ids");
                            throw new IllegalStateException(msg);
                        }
                    } else {
                        env.setRepositoryId(repoPK.get());
                    }
                }
                return repoPK.isPresent();
            }
        }
    }

    static Version getServerVersion(Connection cx) throws SQLException {
        try (Statement st = cx.createStatement()) {
            try (ResultSet rs = st.executeQuery("SHOW server_version")) {
                Preconditions.checkState(rs.next(),
                        "Query 'SHOW server_version' did not produce a result");
                final String v = rs.getString(1);
                return getVersionFromQueryResult(v);
            }
        }
    }

    static Version getVersionFromQueryResult(final String versionQueryResult) {
        return Version.valueOf(versionQueryResult);
    }

    /**
     * List the names of all repositories in the given {@link Environment}.
     * 
     * @param env the environment
     * @return the list of repository names
     */
    public static List<String> listRepos(final @NonNull Environment env) {
        TableNames tables = env.getTables();
        try (Connection cx = env.getConnection()) {
            return listRepos(cx, tables);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> listRepos(final @NonNull Connection cx, @NonNull TableNames tables)
            throws SQLException {
        List<String> repoNames = new ArrayList<>();
        final String repoNamesView = tables.repositoryNamesView();
        if (!tableExists(cx, repoNamesView)) {
            return repoNames;
        }
        String sql = format("SELECT name FROM %s", repoNamesView);
        try (Statement st = cx.createStatement()) {
            try (ResultSet repos = st.executeQuery(sql)) {
                while (repos.next()) {
                    repoNames.add(repos.getString(1));
                }
            }
        }
        return repoNames;
    }

    /**
     * Creates an entry in the {@link TableNames#repositories() repsitories} table with the
     * repository id given by {@link Environment#repositoryId environment.repositoryId}, returning
     * true if the entry was created and false if it already existed.
     * 
     * @return {@code true} if the repository was created, {@code false} if it already exists
     */
    public static boolean createNewRepo(final @NonNull Environment environment)
            throws IllegalArgumentException {
        checkArgument(environment.getRepositoryName() != null, "no repository name provided");

        final String argRepoName = environment.getRepositoryName();
        if (environment.isRepositoryIdSet()) {
            return false;
        }

        try (PGConfigDatabase configdb = new PGConfigDatabase(environment)) {
            final Optional<Integer> repositoryPK = environment.resolveRepositoryPK(argRepoName);
            if (repositoryPK.isPresent()) {
                return false;
            }
            final int pk;
            try (Connection cx = environment.getConnection()) {
                final String reposTable = environment.getTables().repositories();
                cx.setAutoCommit(false);
                try {
                    String sql = format("INSERT INTO %s (created) VALUES (NOW())", reposTable);
                    try (Statement st = cx.createStatement()) {
                        int updCnt = st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
                        checkState(updCnt == 1);
                        try (ResultSet generatedKeys = st.getGeneratedKeys()) {
                            checkState(generatedKeys.next());
                            pk = generatedKeys.getInt(1);
                        }
                        environment.setRepositoryId(pk);
                    }
                    cx.commit();
                } catch (SQLException | RuntimeException e) {
                    throw rollbackAndRethrow(cx, e);
                } finally {
                    cx.setAutoCommit(true);
                }
            }
            // set the config option outside the try-with-resources block to avoid acquiring
            // 2 connections
            configdb.put("repo.name", argRepoName);
            checkState(pk == environment.resolveRepositoryPK(argRepoName)
                    .orElse(Environment.REPOSITORY_ID_UNSET));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        checkState(environment.isRepositoryIdSet());
        return true;
    }

    public static boolean tableExists(final DataSource dataSource, final String tableName) {
        boolean tableExists = false;
        if (dataSource != null) {
            try (Connection cx = PGStorage.newConnection(dataSource)) {
                tableExists = tableExists(cx, tableName);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return tableExists;
    }

    public static boolean tableExists(final Connection cx, final String tableName)
            throws SQLException {
        boolean tableExists = false;
        DatabaseMetaData md = cx.getMetaData();
        final String schema = PGStorageTableManager.schema(tableName);
        final String table = PGStorageTableManager.stripSchema(tableName);
        try (ResultSet tables = md.getTables(null, schema, table, null)) {
            tableExists = tables.next();
        }
        return tableExists;
    }

    public static void createTables(final Environment environment) {
        try (Connection cx = environment.getConnection()) {
            try {
                cx.setAutoCommit(false);
                final Version serverVersion = getServerVersion(cx);
                final PGStorageTableManager tableManager;
                tableManager = PGStorageTableManager.forVersion(serverVersion);
                tableManager.createTables(cx, environment);
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean deleteRepository(final Environment env) {
        checkArgument(env.getRepositoryName() != null, "No repository name provided");

        final @NonNull String repositoryName = env.getRepositoryName();
        final int repositoryPK;

        if (env.isRepositoryIdSet()) {
            repositoryPK = env.getRepositoryId();
        } else {
            Optional<Integer> pk = env.resolveRepositoryPK();
            if (pk.isPresent()) {
                repositoryPK = pk.get();
            } else {
                return false;
            }
        }
        final TableNames tables = env.getTables();
        final String reposTable = tables.repositories();
        boolean deleted = false;

        final String sql = String.format("DELETE FROM %s WHERE repository = ?", reposTable);
        try (Connection cx = env.getConnection()) {
            cx.setAutoCommit(false);
            try (PreparedStatement st = cx.prepareStatement(log(sql, LOG, repositoryName))) {
                st.setInt(1, repositoryPK);
                int rowCount = st.executeUpdate();
                deleted = rowCount > 0;
                cx.commit();
            } catch (SQLException e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Boolean.valueOf(deleted);
    }
}
