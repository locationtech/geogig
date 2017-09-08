/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;

/**
 * Utility for handling GeoGIG repository init requests. This class will pull repository creation
 * details (like parent directory, or PostgreSQL database connection parameters) from the Request
 * and build a GeoGIG repository form them, by converting the request into a
 * {@link org.locationtech.geogig.repository.Hints Hints}.
 */
public class InitRequestUtil {

    private static final InitRequestUtil INSTANCE = new InitRequestUtil();

    private static final String SCHEME = "postgresql";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String SLASH = "/";
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    static final String REPO_ATTR = "repository";

    // Form parameter names
    /**
     * Directory option for Parent Directory.
     */
    static final String DIR_PARENT_DIR = "parentDirectory";
    /**
     * Database option for Host.
     */
    static final String DB_HOST = "dbHost";
    /**
     * Database option for Port.
     */
    static final String DB_PORT = "dbPort";
    /**
     * Database option for database name.
     */
    static final String DB_NAME = "dbName";
    /**
     * Database option for schema name.
     */
    static final String DB_SCHEMA = "dbSchema";
    /**
     * Database option for username.
     */
    static final String DB_USER = "dbUser";
    /**
     * Database option for password.
     */
    static final String DB_PASSWORD = "dbPassword";

    /**
     * Updates the provided Hints instance with a GeoGig Repository URL based on the supplied parameter map. This
     * method builds the Repository location URI form the supplied parameter map and sets it on the provided Hints
     * instance so that the RepositoryResolver can correctly build the repository at the requested location.
     *
     * @param hints Hints instance that will be used to build the requested GeoGig Repository.
     * @param params Key-Value map of repository location specifics.
     * @throws UnsupportedEncodingException
     * @throws URISyntaxException
     * @throws IOException
     * @throws RepositoryConnectionException
     */
    private void updateHintsWithParams(Hints hints, Map<String, String> params)
            throws UnsupportedEncodingException, URISyntaxException, IOException, RepositoryConnectionException {
        // get parameters
        final String parentDir = params.get(DIR_PARENT_DIR);
        final String dbHost = params.getOrDefault(DB_HOST, "localhost");
        final String dbPort = params.getOrDefault(DB_PORT, "5432");
        final String dbName = params.get(DB_NAME);
        final String dbSchema = params.getOrDefault(DB_SCHEMA, "public");
        final String dbUser = params.getOrDefault(DB_USER, "postgres");
        final String dbPassword = params.get(DB_PASSWORD);
        // use parent directory if present
        if (parentDir != null) {
            final String newRepoName = hints.get(Hints.REPOSITORY_NAME).get().toString();
            final URI parentUri = new File(parentDir).getCanonicalFile().toURI();
            final RepositoryResolver resolver = RepositoryResolver.lookup(parentUri);
            // use the existing repo URI
            URI repoURI = resolver.buildRepoURI(parentUri, newRepoName);
            hints.uri(repoURI);
        } else if (dbName != null && dbPassword != null) {
            // try to build a URI from the db parameters
            // build the path from database, schema and repository name
            final String repoName = hints.get(Hints.REPOSITORY_NAME).get().toString();
            final StringBuilder pathBuilder = new StringBuilder(128);
            // build the path as "/dbName/schema/repoName"
            pathBuilder.append(SLASH).append(dbName).append(SLASH).append(dbSchema).append(SLASH).append(repoName);
            // now build the query part, i.e. user=username&password=password
            // need to URLEncode these to make sure things are encoded that need to be, and things don't get double
            // encoded
            final StringBuilder queryBuilder = new StringBuilder(64);

            queryBuilder.append(USER).append('=').append(URLEncoder.encode(dbUser, UTF8))
                    .append('&').append(PASSWORD).append('=').append(URLEncoder.encode(dbPassword, UTF8));
            // convert the port to an Integer
            int port = 5432;
            try {
                port = Integer.parseInt(dbPort);
            } catch (NumberFormatException nfe) {
                // just ues the default of 5432
            }
            final URI repoUri = new URI(
                    SCHEME, null, dbHost, port, pathBuilder.toString(), queryBuilder.toString(), null);
            hints.set(Hints.REPOSITORY_URL, repoUri);
        }
    }

    public static Hints createHintsFromParameters(final String repositoryName,
            final Map<String, String> parameters) throws UnsupportedEncodingException,
            URISyntaxException, IOException, RepositoryConnectionException {
        final Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_NAME, repositoryName);
        // try to build the Repo URI from any Request parameters.
        INSTANCE.updateHintsWithParams(hints, parameters);
        return hints;
    }
}
