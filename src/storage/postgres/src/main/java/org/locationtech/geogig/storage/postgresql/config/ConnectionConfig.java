package org.locationtech.geogig.storage.postgresql.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Delegate;

public @Value class ConnectionConfig implements Cloneable {

    private final @NonNull @Delegate ConnectionConfig.Key key;

    /**
     * Checks if both connection configs target the same database regardless of the schema, user,
     * and password they're connected with
     */
    public boolean isSameDatabase(@NonNull ConnectionConfig other) {
        boolean same = Objects.equals(key.server, other.key.server) && //
                Objects.equals(key.portNumber, other.key.portNumber) && //
                Objects.equals(key.databaseName, other.key.databaseName);
        return same;
    }

    /**
     * Checks if both connection configs target the same database and schema regardless of the user
     * and password they're connected with
     */
    public boolean isSameDatabaseAndSchema(@NonNull ConnectionConfig other) {
        boolean same = isSameDatabase(other) && //
                Objects.equals(key.schema, other.key.schema)
                && Objects.equals(key.tablePrefix, other.key.tablePrefix);
        return same;
    }

    /**
     * Encapsulates the parts of the connection config that uniquely identify a connection to the
     * database in order to be used as key for {@link DataSourceManager}. As such, #schema and
     * #tablePrefix left aside.
     */
    @EqualsAndHashCode(exclude = { "schema", "tablePrefix" })
    static @Value class Key implements Cloneable {

        final @NonNull String server;

        final int portNumber;

        final @NonNull String databaseName;

        final String user;

        final String password;

        final @NonNull String schema;

        final String tablePrefix;

        public Key withPrefix(String newTablePrefix) {
            return new Key(this.server, this.portNumber, this.databaseName, this.user,
                    this.password, this.schema, newTablePrefix);
        }

        public @Override String toString() {
            return String.format(
                    "%s[host: %s, port: %d, db: %s, schema: %s, user: %s, pwd: %s, prefix: %s]",
                    getClass().getSimpleName(), server, portNumber, databaseName, user, "***");
        }

    }

    ConnectionConfig(final String server, final int portNumber, final String databaseName,
            final String schema, @Nullable final String user, @Nullable final String password,
            @Nullable String tablePrefix) {
        if (tablePrefix != null && tablePrefix.trim().isEmpty()) {
            tablePrefix = null;
        }
        this.key = new Key(server, portNumber, databaseName, user, password, schema, tablePrefix);
    }

    ConnectionConfig(Key key) {
        this.key = key;
    }

    public URI toURIMaskPassword() {
        return toURIInternal(null, true);
    }

    public ConnectionConfig withTablePrefix(String tablePrefix) {
        return new ConnectionConfig(this.key.withPrefix(tablePrefix));
    }

    public URI toURI() {
        return toURIInternal(null, false);
    }

    public URI toURI(final @NonNull String repositoryName) {
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
}