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

    public ConnectionConfig getConnectionConfig() {
        String server = get(Environment.KEY_DB_SERVER, String.class).orElse(null);
        String port = get(Environment.KEY_DB_PORT, String.class).orElse("5432");
        String schema = get(Environment.KEY_DB_SCHEMA, String.class).orElse("public");
        String dbName = get(Environment.KEY_DB_NAME, String.class).orElse(null);
        String user = get(Environment.KEY_DB_USERNAME, String.class).orElse(null);
        String pwd = get(Environment.KEY_DB_PASSWORD, String.class).orElse(null);
        int portNumber = Integer.parseInt(port);
        ConnectionConfig config = new ConnectionConfig(server, portNumber, dbName, schema, user,
                pwd, null);
        return config;
    }

}
