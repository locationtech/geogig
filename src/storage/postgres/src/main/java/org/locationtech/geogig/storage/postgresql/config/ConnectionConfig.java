package org.locationtech.geogig.storage.postgresql.config;

import static com.google.common.base.Objects.equal;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class ConnectionConfig implements Cloneable {

    private final ConnectionConfig.Key key;

    /**
     * Checks if both connection configs target the same database regardless of the schema, user,
     * and password they're connected with
     */
    public boolean isSameDatabase(ConnectionConfig other) {
        Preconditions.checkNotNull(other);
        boolean same = Objects.equal(key.server, other.key.server) && //
                Objects.equal(key.portNumber, other.key.portNumber) && //
                Objects.equal(key.databaseName, other.key.databaseName);
        return same;
    }

    /**
     * Checks if both connection configs target the same database and schema regardless of the user
     * and password they're connected with
     */
    public boolean isSameDatabaseAndSchema(ConnectionConfig other) {
        boolean same = isSameDatabase(other) && //
                Objects.equal(key.schema, other.key.schema)
                && Objects.equal(key.tablePrefix, other.key.tablePrefix);
        return same;
    }

    /**
     * Encapsulates the parts of the connection config that uniquely identify a connection to the
     * database in order to be used as key for {@link DataSourceManager}. As such, #schema and
     * #tablePrefix are ignored by {@link #equals(Object)} and {@link #hashCode()}, while they're
     * taking into account for {@link ConnectionConfig} itself.
     *
     */
    static class Key implements Cloneable {

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
            if (o instanceof ConnectionConfig.Key) {
                ConnectionConfig.Key k = (ConnectionConfig.Key) o;
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
        this.key = new Key(server, portNumber, databaseName, schema, user, password, tablePrefix);
    }

    public URI toURIMaskPassword() {
        return toURIInternal(null, true);
    }

    public URI toURI() {
        return toURIInternal(null, false);
    }

    public URI toURI(final String repositoryName) {
        Preconditions.checkNotNull(repositoryName);
        return toURIInternal(repositoryName, false);
    }

    private URI toURIInternal(final @Nullable String repositoryName, final boolean maskPassword) {

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
            String p = maskPassword ? "****" : key.password;
            args.append(args.length() > 0 ? "&password=" : "password=").append(p);
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

    public ConnectionConfig.Key getKey() {
        return key;
    }
}