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
import static com.google.common.base.Preconditions.checkNotNull;
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

import javax.sql.DataSource;

import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.locationtech.geogig.storage.postgresql.v9.PGConfigDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Utility class for PostgreSQL storage.
 */
public class PGStorage {

    static final Logger LOG = LoggerFactory.getLogger(PGStorage.class);

    private static final DataSourceManager DATASOURCE_POOL = new DataSourceManager();

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

    public synchronized static DataSource newDataSource(Environment config) {
        DataSource dataSource = newDataSource(config.connectionConfig);
        return dataSource;
    }

    public synchronized static DataSource newDataSource(ConnectionConfig config) {
        DataSource dataSource = DATASOURCE_POOL.acquire(config.getKey());
        return dataSource;
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

    public static void closeDataSource(DataSource ds) {
        if (ds != null) {
            DATASOURCE_POOL.release(ds);
        }
    }

    public static Connection newConnection(DataSource ds) {
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

    public static boolean repoExists(final Environment config) throws IllegalArgumentException {
        checkNotNull(config);
        checkArgument(config.getRepositoryName() != null, "no repository name provided");

        Optional<Integer> repoPK;
        try (PGConfigDatabase configdb = new PGConfigDatabase(config)) {
            if (config.isRepositorySet()) {
                String configuredName = configdb.get("repo.name").orNull();
                Preconditions.checkState(config.getRepositoryName().equals(configuredName));
                return true;
            } else {
                repoPK = configdb.resolveRepositoryPK(config.getRepositoryName());
                if (repoPK.isPresent()) {
                    if (config.isRepositorySet()) {
                        if (repoPK.get() != config.getRepositoryId()) {
                            String msg = format("The provided primary key for the repository '%s' "
                                    + "does not match the one in the database. Provided: %d, returned: %d. "
                                    + "Check the consistency of the repo.name config property for those repository ids");
                            throw new IllegalStateException(msg);
                        }
                    } else {
                        config.setRepositoryId(repoPK.get());
                    }
                }
                return repoPK.isPresent();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Version getServerVersion(final Environment env) {
        DataSource ds = newDataSource(env);
        try (Connection cx = newConnection(ds)) {
            return getServerVersion(cx);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            closeDataSource(ds);
        }
    }

    public static Version getServerVersion(Connection cx) throws SQLException {
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
     * @param config the environment
     * @return the list of repository names
     */
    public static List<String> listRepos(final Environment config) {
        checkNotNull(config);
        final DataSource dataSource = PGStorage.newDataSource(config);
        TableNames tables = config.getTables();

        try (Connection cx = PGStorage.newConnection(dataSource)) {
            return listRepos(cx, tables);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            PGStorage.closeDataSource(dataSource);
        }
    }

    public static List<String> listRepos(final Connection cx, TableNames tables)
            throws SQLException {
        checkNotNull(cx);
        checkNotNull(tables);
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
     * Initializes all the tables as given by the names in {@link Environment#getTables()
     * config.getTables()} if need be, and creates an entry in the {@link TableNames#repositories()
     * repsitories} table with the repository id given by {@link Environment#repositoryId
     * config.repositoryId}, returning true if the entry was created and false if it already
     * existed.
     * 
     * @return {@code true} if the repository was created, {@code false} if it already exists
     */
    public static boolean createNewRepo(final Environment config) throws IllegalArgumentException {
        checkNotNull(config);
        checkArgument(config.getRepositoryName() != null, "no repository name provided");
        createTables(config);

        final String argRepoName = config.getRepositoryName();
        if (config.isRepositorySet()) {
            return false;
        }

        final DataSource dataSource = PGStorage.newDataSource(config);

        try (PGConfigDatabase configdb = new PGConfigDatabase(config)) {
            final Optional<Integer> repositoryPK = configdb.resolveRepositoryPK(argRepoName);
            if (repositoryPK.isPresent()) {
                return false;
            }
            final int pk;
            try (Connection cx = PGStorage.newConnection(dataSource)) {
                final String reposTable = config.getTables().repositories();
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
                        config.setRepositoryId(pk);
                    }
                    cx.commit();
                } catch (SQLException | RuntimeException e) {
                    throw rollbackAndRethrow(cx, e);
                } finally {
                    cx.setAutoCommit(true);
                }
            }
            // set the config option outside the try-with-resources block to avoid acquiring
            // 2
            // connections
            configdb.put("repo.name", argRepoName);
            checkState(pk == configdb.resolveRepositoryPK(argRepoName)
                    .or(Environment.REPOSITORY_ID_UNSET));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            PGStorage.closeDataSource(dataSource);
        }

        checkState(config.isRepositorySet());
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

    public static synchronized void createTables(final Environment config) {
        final DataSource dataSource = PGStorage.newDataSource(config);
        try (Connection cx = PGStorage.newConnection(dataSource)) {
            final Version serverVersion = getServerVersion(cx);
            final PGStorageTableManager tableManager;
            tableManager = PGStorageTableManager.forVersion(serverVersion);
            tableManager.createTables(cx, config);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            PGStorage.closeDataSource(dataSource);
        }

    }

    public static boolean deleteRepository(final Environment env) {
        checkArgument(env.getRepositoryName() != null, "No repository name provided");

        final String repositoryName = env.getRepositoryName();
        final int repositoryPK;

        if (env.isRepositorySet()) {
            repositoryPK = env.getRepositoryId();
        } else {
            try (PGConfigDatabase configdb = new PGConfigDatabase(env)) {
                Optional<Integer> pk = configdb.resolveRepositoryPK(repositoryName);
                if (pk.isPresent()) {
                    repositoryPK = pk.get();
                } else {
                    return false;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        final TableNames tables = env.getTables();
        final String reposTable = tables.repositories();
        boolean deleted = false;

        final String sql = String.format("DELETE FROM %s WHERE repository = ?", reposTable);
        final DataSource ds = PGStorage.newDataSource(env);

        try (Connection cx = PGStorage.newConnection(ds)) {
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
        } finally {
            PGStorage.closeDataSource(ds);
        }

        return Boolean.valueOf(deleted);
    }
}
