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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.postgresql.v9.PGConfigDatabase;

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

    public static class ConnectionConfig {

        private final Key key;

        /**
         * Encapsulates the parts of the connection config that uniquely identify a connection to
         * the database in order to be used as key for {@link DataSourceManager}. As such, #schema
         * and #tablePrefix are ignored by {@link #equals(Object)} and {@link #hashCode()}, while
         * they're taking into account for {@link ConnectionConfig} itself.
         *
         */
        static class Key {

            final String server;

            final int portNumber;

            final String databaseName;

            @Nullable
            final String user;

            @Nullable
            final String password;

            final String schema;

            @Nullable
            final String tablePrefix;

            Key(String server, int portNumber, String databaseName, String schema, String user,
                    String password, String tablePrefix) {
                this.server = server;
                this.portNumber = portNumber;
                this.databaseName = databaseName;
                this.schema = schema;
                this.user = user;
                this.password = password;
                this.tablePrefix = tablePrefix;
            }

            public @Override boolean equals(Object o) {
                if (o instanceof Key) {
                    Key k = (Key) o;
                    return equal(server, k.server) && equal(portNumber, k.portNumber)
                            && equal(databaseName, k.databaseName) && equal(user, k.user)
                            && equal(password, k.password);
                }
                return false;
            }

            public @Override int hashCode() {
                return Objects.hashCode(server, portNumber, databaseName, user, password);
            }

            public @Override String toString() {
                return String.format(
                        "%s[host: %s, port: %d, db: %s, schema: %s, user: %s, pwd: %s, prefix: %s]",
                        getClass().getSimpleName(), server, portNumber, databaseName, schema, user,
                        "***", tablePrefix);
            }

        }

        ConnectionConfig(final String server, final int portNumber, final String databaseName,
                final String schema, @Nullable final String user, @Nullable final String password,
                @Nullable String tablePrefix) {
            this.key = new Key(server, portNumber, databaseName, schema, user, password,
                    tablePrefix);
        }

        public URI toURI() {
            return toURIInternal(null);
        }

        public URI toURI(final String repositoryName) {
            Preconditions.checkNotNull(repositoryName);
            return toURIInternal(repositoryName);
        }

        private URI toURIInternal(final @Nullable String repositoryName) {

            // postgresql://<server>:<port>/<database>/<schema>[/<repoid>]?user=<username>][&password=<pwd>][&tablePrefix=<prefix>]
            StringBuilder sb = new StringBuilder("postgresql://").append(key.server).append(":")
                    .append(key.portNumber).append("/").append(key.databaseName).append("/")
                    .append(key.schema);

            if (repositoryName != null) {
                sb.append("/").append(repositoryName);
            }
            StringBuilder args = new StringBuilder();
            if (key.user != null) {
                args.append("user=").append(key.user);
            }
            if (key.password != null) {
                args.append(args.length() > 0 ? "&password=" : "password=").append(key.password);
            }
            if (key.tablePrefix != null) {
                args.append(args.length() > 0 ? "&tablePrefix=" : "tablePrefix=")
                        .append(key.tablePrefix);
            }
            if (args.length() > 0) {
                sb.append("?").append(args);
            }

            URI repoURI = null;
            try {
                repoURI = new URI(sb.toString());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            return repoURI;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ConnectionConfig)) {
                return false;
            }
            ConnectionConfig d = (ConnectionConfig) o;
            return equal(key, d.key) && equal(getSchema(), d.getSchema())
                    && equal(key.tablePrefix, d.key.tablePrefix);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key, key.schema, key.tablePrefix);
        }

        public @Override String toString() {
            return String.format("%s[%s]", getClass().getSimpleName(), key);
        }

        public String getDatabaseName() {
            return key.databaseName;
        }

        public @Nullable String getUser() {
            return key.user;
        }

        public @Nullable String getPassword() {
            return key.password;
        }

        public String getSchema() {
            return key.schema;
        }

        public int getPortNumber() {
            return key.portNumber;
        }

        public String getServer() {
            return key.server;
        }

        public @Nullable String getTablePrefix() {
            return key.tablePrefix;
        }

        public Key getKey() {
            return key;
        }
    }

    public final ConnectionConfig connectionConfig;

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
    public Environment(final String server, final int portNumber, final String databaseName,
            final String schema, final String user, final String password,
            final @Nullable String repositoryName, @Nullable String tablePrefix) {

        if (tablePrefix != null && tablePrefix.trim().isEmpty()) {
            tablePrefix = null;
        }

        this.connectionConfig = new ConnectionConfig(server, portNumber, databaseName, schema, user,
                password, tablePrefix);
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

    public static final int GLOBAL_KEY = -1;

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
