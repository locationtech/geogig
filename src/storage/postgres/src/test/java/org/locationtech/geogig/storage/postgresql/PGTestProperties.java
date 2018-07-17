/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.test.integration.OnlineTestProperties;

public class PGTestProperties extends OnlineTestProperties {

    public static final String CONFIG_FILE = ".geogig-pg-backend-tests.properties";

    public static final String TESTS_ENABLED_KEY = "postgres.enabled";

    private static final String[] DEFAULTS = { //
            TESTS_ENABLED_KEY, "false", //
            Environment.KEY_DB_SERVER, "localhost", //
            Environment.KEY_DB_PORT, "5432", //
            Environment.KEY_DB_SCHEMA, "public", //
            Environment.KEY_DB_NAME, "database", //
            Environment.KEY_DB_USERNAME, "postgres", //
            Environment.KEY_DB_PASSWORD, "postgres"//
    };

    public PGTestProperties() {
        super(CONFIG_FILE, DEFAULTS);
    }

    public Environment newConfig(@Nullable String repositoryId) {
        return newConfig(repositoryId, null);
    }

    public Environment newConfig(@Nullable String repositoryId, @Nullable String tablePrefix) {
        String server = get(Environment.KEY_DB_SERVER, String.class).orNull();
        String port = get(Environment.KEY_DB_PORT, String.class).or("5432");
        String schema = get(Environment.KEY_DB_SCHEMA, String.class).or("public");
        String dbName = get(Environment.KEY_DB_NAME, String.class).orNull();
        String user = get(Environment.KEY_DB_USERNAME, String.class).orNull();
        String pwd = get(Environment.KEY_DB_PASSWORD, String.class).orNull();
        int portNumber = Integer.parseInt(port);
        return new Environment(server, portNumber, dbName, schema, user, pwd, repositoryId,
                tablePrefix);
    }

    public String buildRepoURL(@Nullable String repoId, @Nullable String tablePrefix) {
        String server = get(Environment.KEY_DB_SERVER, String.class).orNull();
        String port = get(Environment.KEY_DB_PORT, String.class).or("5432");
        String schema = get(Environment.KEY_DB_SCHEMA, String.class).or("public");
        String dbName = get(Environment.KEY_DB_NAME, String.class).orNull();
        String user = get(Environment.KEY_DB_USERNAME, String.class).orNull();
        String pwd = get(Environment.KEY_DB_PASSWORD, String.class).orNull();
        String url;
        if (repoId == null) {
            url = String.format("postgresql://%s:%s/%s/%s?user=%s&password=%s", server, port,
                    dbName, schema, user, pwd);
        } else {
            url = String.format("postgresql://%s:%s/%s/%s/%s?user=%s&password=%s", server, port,
                    dbName, schema, repoId, user, pwd);
        }

        if (tablePrefix != null) {
            url += "&tablePrefix=" + tablePrefix;
        }
        return url;
    }

}
