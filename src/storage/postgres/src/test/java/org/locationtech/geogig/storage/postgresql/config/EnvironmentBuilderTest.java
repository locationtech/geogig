/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.config;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.springframework.web.util.UriComponentsBuilder;

public class EnvironmentBuilderTest {

    private static final String UTF8 = StandardCharsets.UTF_8.name();

    private URI buildUri(String host, @Nullable Integer port, String dbName,
            @Nullable String schema, String repoName, String user, String password)
            throws URISyntaxException, UnsupportedEncodingException {
        /**
         * Using Spring's URI builder utils here because java.net.URI doesn't handle special
         * characters in the query parameter encoding correctly. If we want to build a URI with
         * host=localhost, port=5432, dbName=myDb, schema=mySchema, repoName=myRepo, user=myUser and
         * password=myPass&word, the URI should look like this:
         * <p>
         * postgresql://localhost:5432/myDb/mySchema/myRepo?user=myUser&password=myPass%26word
         * <p>
         * If we use java.net.URI to create a URI from an unencoded String like this:
         * <p>
         * "postgresql://localhost:5432/myDb/mySchema/myRepo?user=myUser&password=myPass&word"
         * <p>
         * java.net.URI will try to Encode the String, but will interpret the `&` in the password
         * value as a query parameter delimiter, in this case yielding 3 query parameters: user,
         * password and myPass.
         * <p>
         * If we URLEncode the password value before building the URI, using this string:
         * <p>
         * "postgresql://localhost:5432/myDb/mySchema/myRepo?user=myUser&password=myPass%26word"
         * <p>
         * java.net.URI will try to Encode the String anyway, thus double encoding the password
         * value, resulting in:
         * <p>
         * postgresql://localhost:5432/myDb/mySchema/myRepo?user=myUser&password=myPass%2526word
         * <p>
         * java.net.URI doesn't have a way to construct a URI with a pre-encoded query parameter
         * value, so there isn't a way to use it to correctly encode special characters in query
         * parameter values. Using Spring-web's URIComponentsBuilder, we can URLEncode the parameter
         * values first, then build a URIComponents such that the URI doesn't double encode the
         * query parameter by using URIComponentsBuilder.build(true), which tells the builder that
         * the URI is already encoded.
         */
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        // set the scheme
        builder.scheme("postgresql");
        // set the host and port
        builder.host(host);
        if (port != null && port > 0) {
            builder.port(port);
        } else {
            builder.port(5432);
        }
        // build the path in the form of "/dbName/schema/repoName"
        StringBuilder pathBuilder = new StringBuilder(128);
        pathBuilder.append("/").append(dbName).append("/");
        if (schema != null) {
            pathBuilder.append(schema);
        } else {
            pathBuilder.append("public");
        }
        pathBuilder.append("/").append(repoName);
        builder.path(pathBuilder.toString());
        // now build the query parameters
        builder.queryParam("user", URLEncoder.encode(user, UTF8));
        builder.queryParam("password", URLEncoder.encode(password, UTF8));
        return builder.build(true).toUri();
    }

    private void test(String host, @Nullable Integer port, String dbName, @Nullable String schema,
            String repoName, String user, String password)
            throws URISyntaxException, UnsupportedEncodingException {
        // build the URI
        URI uri = buildUri(host, port, dbName, schema, repoName, user, password);
        // Build an Environment
        EnvironmentBuilder builder = new EnvironmentBuilder(uri);
        ConnectionConfig config = builder.build().connectionConfig;
        // assert properties
        assertEquals("Unexpected HOST value", host, config.getServer());
        if (port != null) {
            assertEquals("Unexpected PORT value", port.intValue(), config.getPortNumber());
        } else {
            assertEquals("Unexpected PORT value", 5432, config.getPortNumber());
        }
        assertEquals("Unexpected DB NAME value", dbName, config.getDatabaseName());
        if (schema != null) {
            assertEquals("Unexpected SCHEMA value", schema, config.getSchema());
        } else {
            assertEquals("Unexpected SCHEMA value", "public", config.getSchema());
        }
        assertEquals("Unexpected USER value", user, config.getUser());
        assertEquals("Unexpected PASSWORD value", password, config.getPassword());
    }

    @Test
    public void testextractShortKeys() throws URISyntaxException, UnsupportedEncodingException {
        // test some basic values
        test("testHost", null, "testDb", null, "testRepo", "testUser", "testPassword");
    }

    @Test
    public void testextractShortKeys_withPassword_hash()
            throws URISyntaxException, UnsupportedEncodingException {
        // test some basic values
        test("testHost", null, "testDb", null, "testRepo", "testUser", "test#Password");
    }

    @Test
    public void testextractShortKeys_withPassword_ampersand()
            throws URISyntaxException, UnsupportedEncodingException {
        // test some basic values
        test("testHost", null, "testDb", null, "testRepo", "testUser", "test&Password");
    }

    @Test
    public void testextractShortKeys_withPassword_multiSpecial()
            throws URISyntaxException, UnsupportedEncodingException {
        // test some basic values
        test("testHost", null, "testDb", null, "testRepo", "testUser", "!@#$%^&*()");
    }
}
