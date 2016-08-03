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

import static com.google.common.base.Objects.equal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.Hints;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * <p>
 * There is one PGEnvironment instance per repository, where multiple repositories that share the
 * same database will have the same {@link #connectionConfig} and hence will share the connection
 * pool and other resources that have to be per database singletons, such as object cache, and
 * thread pools.
 */
public class Environment {

    public static final String KEY_DB_SERVER = "postgres.server";

    public static final String KEY_DB_PORT = "postgres.port";

    public static final String KEY_DB_SCHEMA = "postgres.schema";

    public static final String KEY_DB_NAME = "postgres.database";

    public static final String KEY_DB_USERNAME = "postgres.user";

    public static final String KEY_DB_PASSWORD = "postgres.password";

    public static final String KEY_REPOSITORY_ID = "repository.id";

    public static final String KEY_THREADPOOL_SIZE = "postgres.threadPoolSize";

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
    static final int REPOSITORY_ID_UNSET = Integer.MIN_VALUE;

    static class ConnectionConfig {

        private final String user;

        private final String password;

        private final String databaseName;

        private final String schema;

        private final int portNumber;

        private final String server;

        ConnectionConfig(final String server, final int portNumber, final String databaseName,
                final String schema, final String user, final String password) {
            this.server = server;
            this.portNumber = portNumber;
            this.databaseName = databaseName;
            this.schema = schema;
            this.user = user;
            this.password = password;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ConnectionConfig)) {
                return false;
            }
            ConnectionConfig d = (ConnectionConfig) o;
            return equal(getServer(), d.getServer()) && equal(getPortNumber(), d.getPortNumber())
                    && equal(getDatabaseName(), d.getDatabaseName())
                    && equal(getSchema(), d.getSchema()) && equal(getUser(), d.getUser())
                    && equal(getPassword(), d.getPassword());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getServer(), getPortNumber(), getDatabaseName(), getSchema(),
                    getUser(), getPassword());
        }

        String getDatabaseName() {
            return databaseName;
        }

        String getUser() {
            return user;
        }

        String getPassword() {
            return password;
        }

        String getSchema() {
            return schema;
        }

        int getPortNumber() {
            return portNumber;
        }

        String getServer() {
            return server;
        }
    }

    final ConnectionConfig connectionConfig;

    private final String repositoryName;

    private final TableNames tables;

    private int repositoryId = REPOSITORY_ID_UNSET;

    /**
     * @param server postgres server name
     * @param portNumber postgres server port number
     * @param databaseName postgres database name
     * @param schema database schema name
     * @param user postgres connection user name
     * @param password postgres user password
     * @param repositoryName repository id, optional. If not given this config can only be used by
     *        {@link PGConfigDatabase} to access the "global" configuration.
     */
    Environment(final String server, final int portNumber, final String databaseName,
            final String schema, final String user, final String password,
            final @Nullable String repositoryName, final @Nullable String tablePrefix) {

        this.connectionConfig = new ConnectionConfig(server, portNumber, databaseName, schema, user,
                password);
        this.repositoryName = repositoryName;
        this.tables = new TableNames(schema == null ? TableNames.DEFAULT_SCHEMA : schema,
                tablePrefix == null ? TableNames.DEFAULT_TABLE_PREFIX : tablePrefix);
    }

    /**
     * Equality comparison based on driver, server, port, user, and password, and database name.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Environment)) {
            return false;
        }
        Environment d = (Environment) o;
        return equal(connectionConfig, d.connectionConfig)
                && equal(repositoryName, d.repositoryName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(connectionConfig, repositoryName);
    }

    public TableNames getTables() {
        return tables;
    }

    private AtomicBoolean repositoryExistsChecked = new AtomicBoolean();

    public void checkRepositoryExists() throws IllegalStateException {
        if (repositoryExistsChecked.get()) {
            return;
        }
        Preconditions.checkState(PGStorage.repoExists(this), "Repository %s does not exist",
                this.repositoryName);
        repositoryExistsChecked.set(true);
    }

    public static Environment get(final Hints hints) throws URISyntaxException {
        return new EnvironmentBuilder(hints).build();
    }

    public static Environment get(final Properties properties) throws URISyntaxException {
        return new EnvironmentBuilder(properties).build();
    }

    public static Environment get(final URI repoURI) throws URISyntaxException {
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
        return repositoryId;
    }

    public void setRepositoryId(int repoPK) {
        this.repositoryId = repoPK;
    }

    public boolean isRepositorySet() {
        return repositoryId != REPOSITORY_ID_UNSET;
    }
}
