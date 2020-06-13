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

public class EnvironmentBuilderTest {

    private static final String UTF8 = StandardCharsets.UTF_8.name();

    private URI buildUri(String host, @Nullable Integer port, String dbName,
            @Nullable String schema, String repoName, String user, String password)
            throws URISyntaxException, UnsupportedEncodingException {

        if (port == null || port == 0) {
            port = 5432;
        }
        // build the path in the form of "/dbName/schema/repoName"
        StringBuilder pathBuilder = new StringBuilder(128);
        pathBuilder.append(dbName).append("/");
        if (schema != null) {
            pathBuilder.append(schema);
        } else {
            pathBuilder.append("public");
        }
        pathBuilder.append("/").append(repoName);
        String uri = String.format("postgresql://%s:%d/%s?user=%s&password=%s", host, port,
                pathBuilder, URLEncoder.encode(user, UTF8), URLEncoder.encode(password, UTF8));
        return URI.create(uri);
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
