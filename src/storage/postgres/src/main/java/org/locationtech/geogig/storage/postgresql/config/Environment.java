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

import static com.google.common.base.Objects.equal;
import static java.lang.String.format;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.postgresql.v9.PGConfigDatabase;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * <p>
 * There is one PGEnvironment instance per repository, where multiple repositories that share the
 * same database will have the same {@link #connectionConfig} and hence will share the connection
 * pool and other resources that have to be per database singletons, such as object cache, and
 * thread pools.
 */
public class Environment implements Cloneable {

    public static final String KEY_DB_SERVER = "postgres.server";

    public static final String KEY_DB_PORT = "postgres.port";

    public static final int DEFAULT_DB_PORT = 5432;

    public static final String KEY_DB_SCHEMA = "postgres.schema";

    public static final String KEY_DB_NAME = "postgres.database";

    public static final String KEY_DB_USERNAME = "postgres.user";

    public static final String KEY_DB_PASSWORD = "postgres.password";

    public static final String KEY_REPOSITORY_ID = "repository.id";

    public static final String KEY_THREADPOOL_SIZE = "postgres.threadPoolSize";

    public static final String KEY_MAX_CONNECTIONS = "postgres.maxConnections";

    public static final String KEY_GETALL_BATCH_SIZE = "postgres.getAllBatchSize";

    public static final String KEY_PUTALL_BATCH_SIZE = "postgres.putAllBatchSize";

    public static final String KEY_ODB_BYTE_CACHE_MAX_SIZE = "postgres.bytecache.maxSize";

    public static final String KEY_ODB_BYTE_CACHE_CONCURRENCY_LEVEL = "postgres.bytecache.concurrencyLevel";

    public static final String KEY_ODB_BYTE_CACHE_EXPIRE_SECONDS = "postgres.bytecache.expireSeconds";

    public static final String KEY_ODB_BYTE_CACHE_INITIAL_CAPACITY = "postgres.bytecache.initialCapacity";

    /**
     * Initial value for {@link #getRepositoryId()}, indicates it has not been set and needs to be
     * looked up in the database by means of the {@code repo.name} config property.
     * 
     * @see PGConfigDatabase#resolveRepositoryPK(String)
     */
    public static final int REPOSITORY_ID_UNSET = Integer.MIN_VALUE;

    private @Getter final ConnectionConfig connectionConfig;

    private String repositoryName;

    private final TableNames tables;

    private int repositoryId = REPOSITORY_ID_UNSET;

    private boolean readOnly;

    private @NonNull PGDataSourceProvider dataSourceProvider;

    private DataSource dataSource;

    /**
     * @param repositoryName repository id, optional. If not given this config can only be used by
     *        {@link PGConfigDatabase} to access the "global" configuration.
     */
    public Environment(//
            @NonNull ConnectionConfig connectionConfig, //
            @NonNull PGDataSourceProvider dataSource, //
            String repositoryName) {

        this.connectionConfig = connectionConfig;
        this.dataSourceProvider = dataSource;
        this.repositoryName = repositoryName;
        this.tables = createTables(connectionConfig);
    }

    public Environment(//
            @NonNull ConnectionConfig connectionConfig, //
            @NonNull DataSource dataSource, //
            String repositoryName) {

        this.connectionConfig = connectionConfig;
        this.dataSourceProvider = new ProvidedDataSource(dataSource);
        this.repositoryName = repositoryName;
        this.tables = createTables(connectionConfig);
    }

    private static @RequiredArgsConstructor class ProvidedDataSource
            implements PGDataSourceProvider {
        private final DataSource dataSource;

        public @Override DataSource get(ConnectionConfig config) {
            return dataSource;
        }

        public @Override void close(DataSource dataSource) {
            // no-op
        }
    }

    private TableNames createTables(ConnectionConfig connectionConfig) {
        String schema = connectionConfig.getSchema();
        String tablePrefix = connectionConfig.getTablePrefix();
        return new TableNames(schema == null ? TableNames.DEFAULT_SCHEMA : schema,
                tablePrefix == null ? TableNames.DEFAULT_TABLE_PREFIX : tablePrefix);
    }

    public DataSource getDataSource() {
        if (this.dataSource == null) {
            synchronized (this) {
                if (this.dataSource == null) {
                    this.dataSource = this.dataSourceProvider.get(this.connectionConfig);
                }
            }
        }
        return this.dataSource;
    }

    public void close() {
        this.dataSourceProvider.close(this.dataSource);
        this.dataSource = null;
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    final @VisibleForTesting void setReadOnly(boolean ro) {
        this.readOnly = ro;
    }

    public Connection getConnection() {
        return PGStorage.newConnection(getDataSource());
    }

    public Version getServerVersion() {
        try (Connection c = getConnection()) {
            return PGStorage.getServerVersion(c);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Equality comparison based on driver, server, port, user, and password, and database name.
     */
    public @Override boolean equals(Object o) {
        if (!(o instanceof Environment)) {
            return false;
        }
        Environment d = (Environment) o;
        return equal(connectionConfig, d.connectionConfig)
                && equal(repositoryName, d.repositoryName);
    }

    public @Override int hashCode() {
        return Objects.hashCode(connectionConfig, repositoryName);
    }

    public TableNames getTables() {
        return tables;
    }

    private AtomicBoolean repositoryExistsChecked = new AtomicBoolean();

    public static final int GLOBAL_KEY = -1;

    public void checkRepositoryExists() throws IllegalStateException {
        if (repositoryExistsChecked.get()) {
            return;
        }
        Preconditions.checkState(PGStorage.repoExists(this), "Repository %s does not exist",
                this.repositoryName);
        repositoryExistsChecked.set(true);
    }

    public static Environment get(final Hints hints) {
        return new EnvironmentBuilder(hints).build();
    }

    public static Environment get(final Properties properties) {
        return new EnvironmentBuilder(properties).build();
    }

    public static Environment get(final URI repoURI) {
        return new EnvironmentBuilder(repoURI).build();
    }

    public String getDatabaseName() {
        return connectionConfig.getDatabaseName();
    }

    public String getUser() {
        return connectionConfig.getUser();
    }

    public String getPassword() {
        return connectionConfig.getPassword();
    }

    public String getSchema() {
        return connectionConfig.getSchema();
    }

    public int getPortNumber() {
        return connectionConfig.getPortNumber();
    }

    public String getServer() {
        return connectionConfig.getServer();
    }

    @Nullable
    public String getRepositoryName() {
        return repositoryName;
    }

    public int getRepositoryId() {
        if (REPOSITORY_ID_UNSET == repositoryId && repositoryName != null) {
            repositoryId = resolveRepositoryPK().orElseThrow(() -> new IllegalStateException(
                    "Repository " + repositoryName + " does not exist"));
        }
        return repositoryId;
    }

    void setRepositoryId(int repoPK) {
        this.repositoryId = repoPK;
    }

    public boolean isRepositoryIdSet() {
        return repositoryId != REPOSITORY_ID_UNSET;
    }

    public boolean canResolveRepositoryId() {
        return REPOSITORY_ID_UNSET != getRepositoryId();
    }

    public boolean isRepositoryNameSet() {
        return repositoryName != null;
    }

    public @Override Environment clone() {
        try {
            return (Environment) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public Environment withRepository(String repoName) {
        Environment clone = clone();
        clone.repositoryId = REPOSITORY_ID_UNSET;
        clone.repositoryName = repoName;
        return clone;
    }

    public URI toURI() {
        if (null == repositoryName) {
            return connectionConfig.toURI();
        }
        return connectionConfig.toURI(repositoryName);
    }

    public Optional<Integer> resolveRepositoryPK() {
        return resolveRepositoryPK(getRepositoryName());
    }

    public Optional<Integer> resolveRepositoryPK(@NonNull String repositoryName) {
        try (Connection cx = getConnection()) {
            return resolveRepositoryPK(repositoryName, cx);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IllegalStateException(
                    "Unable to connect to resolve repository id of " + repositoryName, e);
        }
    }

    private Optional<Integer> resolveRepositoryPK(String repositoryName, Connection cx)
            throws SQLException {

        final String configTable = getTables().repositoryNamesView();
        final String sql = format("SELECT repository FROM %s WHERE name = ?", configTable);

        Integer repoPK = null;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, repositoryName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    repoPK = rs.getInt(1);
                    List<Integer> all = new ArrayList<>(List.of(repoPK));
                    while (rs.next()) {
                        all.add(rs.getInt(1));
                    }
                    if (all.size() > 1) {
                        throw new IllegalStateException(format(
                                "There're more than one repository named '%s'. "
                                        + "Check the repo.name config property for the following repository ids: %s",
                                repositoryName, all.toString()));
                    }
                }
            }
        }
        return Optional.ofNullable(repoPK);
    }
}
