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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.Hints;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

class EnvironmentBuilder {

    private Environment config;

    public EnvironmentBuilder(Hints hints) throws URISyntaxException {
        Optional<Serializable> repoUrl = hints.get(Hints.REPOSITORY_URL);
        checkArgument(repoUrl.isPresent(), "%s was not given", Hints.REPOSITORY_URL);
        URI url = new URI(String.valueOf(repoUrl.get()));
        init(url);
    }

    public EnvironmentBuilder(Properties props) {
        init(props);
    }

    /**
     * @param repoUrl repository URL of the form
     *        {@code postgresql://<server>[:<port>]/database[/<schema>]/<repoid>?user=<username>&password=<pwd>}
     */
    public EnvironmentBuilder(URI repoUrl) {
        init(repoUrl);
    }

    private void init(URI repoUrl) {
        // postgresql://<server>[:<port>]/database[/<schema>]/<repoid>?user=<username>&password=<pwd>
        Preconditions.checkNotNull(repoUrl);
        final String uriScheme = repoUrl.getScheme();
        Preconditions.checkArgument("postgresql".equals(uriScheme),
                "Wrong URL protocol. Expected postgresql, got ", uriScheme);
        final String host = repoUrl.getHost();
        final String portNumber;
        final String dbName;
        final String schema;
        final String repsitoryId;
        final String user, password;
        final String tablePrefix;// mainly used for unit testing

        int port = repoUrl.getPort();
        if (-1 == port) {
            port = 5432;
        }
        portNumber = String.valueOf(port);

        List<String> path = Splitter.on('/').omitEmptyStrings().splitToList(repoUrl.getPath());
        Preconditions
                .checkArgument(
                        path.size() >= 2 && path.size() <= 3,
                        "Path in URI must be like postgresql://<server>[:<port>]/database[/<schema>]/<repoid>?user=<username>&password=<pwd>",
                        repoUrl);

        dbName = path.get(0);
        schema = path.size() == 2 ? "public" : path.get(1);
        repsitoryId = path.size() == 2 ? path.get(1) : path.get(2);
        Map<String, String> shortKeys = new HashMap<>();
        for (String pair : Splitter.on('&').split(repoUrl.getQuery())) {
            List<String> p = Splitter.on('=').splitToList(pair);
            shortKeys.put(p.get(0), p.get(1));
        }
        user = shortKeys.get("user");
        password = shortKeys.get("password");
        tablePrefix = shortKeys.get("tablePrefix");

        Properties props = new Properties();
        props.setProperty(Environment.KEY_DB_SERVER, host);
        props.setProperty(Environment.KEY_DB_PORT, portNumber);
        props.setProperty(Environment.KEY_DB_NAME, dbName);
        props.setProperty(Environment.KEY_DB_SCHEMA, schema);
        props.setProperty(Environment.KEY_REPOSITORY_ID, repsitoryId);
        props.setProperty(Environment.KEY_DB_USERNAME, user);
        props.setProperty(Environment.KEY_DB_PASSWORD, password);
        if (!Strings.isNullOrEmpty(tablePrefix)) {
            props.setProperty("tablePrefix", tablePrefix);
        }
        init(props);
    }

    private void init(Properties props) {
        String server = props.getProperty(Environment.KEY_DB_SERVER);
        String portNumber = props.getProperty(Environment.KEY_DB_PORT);
        String databaseName = props.getProperty(Environment.KEY_DB_NAME);
        String schema = props.getProperty(Environment.KEY_DB_SCHEMA);
        String userName = props.getProperty(Environment.KEY_DB_USERNAME);
        String password = props.getProperty(Environment.KEY_DB_PASSWORD);
        @Nullable
        String repoId = props.getProperty(Environment.KEY_REPOSITORY_ID);

        checkArgument(server != null, "postgres.server config is not set");
        checkArgument(databaseName != null, "postgres.database config is not set");
        checkArgument(schema != null, "postgres.schema config is not set");
        checkArgument(userName != null, "postgres.user config is not set");
        checkArgument(password != null, "postgres.password config is not set");
        // checkArgument(repoId != null, "repository.id config is not set");

        int port;
        try {
            port = portNumber == null ? 4532 : Integer.parseInt(portNumber);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(String.format(
                    "'%s' can't be parsed as integer for argument %s", portNumber,
                    Environment.KEY_DB_PORT), nfe);
        }
        String tablePrefix = props.getProperty("tablePrefix");// only used by tests
        if (tablePrefix != null && tablePrefix.trim().isEmpty()) {
            tablePrefix = null;
        }
        this.config = new Environment(server, port, databaseName, schema, userName, password,
                repoId, tablePrefix);
    }

    public Environment build() {
        return config;
    }

}
