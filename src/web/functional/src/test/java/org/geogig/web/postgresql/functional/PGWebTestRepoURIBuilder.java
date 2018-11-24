/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.geogig.web.postgresql.functional;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.locationtech.geogig.cli.test.functional.TestRepoURIBuilder;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.postgresql.PGTestProperties;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.TableNames;
import org.locationtech.geogig.storage.postgresql.v9.PGStorageTestUtil;

import com.google.common.base.Throwables;

/**
 * URI builder for Postgres-backed repositories for web functional tests.
 */
public final class PGWebTestRepoURIBuilder extends TestRepoURIBuilder {

    private static SecureRandom RND = new SecureRandom();

    private PGTestProperties pgTestProperties;

    private String tablePrefix;

    @Override
    public void before() throws Throwable {
        pgTestProperties = new PGTestProperties();
        synchronized (RND) {
            tablePrefix = "geogig_" + Math.abs(RND.nextInt(100_000)) + "_";
        }
    }

    @Override
    public void after() {
        Environment environment = pgTestProperties.newConfig(null, tablePrefix);
        DataSource dataSource = PGStorageTestUtil.newDataSource(environment);
        try {
            TableNames tables = environment.getTables();
            Connection cx = dataSource.getConnection();
            execute(cx, String.format("DROP VIEW IF EXISTS %s", tables.repositoryNamesView()));
            delete(cx, tables.objects(), true);
            delete(cx, tables.conflicts());
            delete(cx, tables.blobs());

            delete(cx, tables.index());
            delete(cx, tables.indexMappings());
            delete(cx, tables.indexObjects());

            delete(cx, tables.graphMappings());
            delete(cx, tables.graphEdges());
            delete(cx, tables.graphMappings());
            delete(cx, tables.graphProperties());

            delete(cx, tables.refs());
            delete(cx, tables.config());
            delete(cx, tables.repositories());
            cx.close();
        } catch (Exception e) {
            Throwables.propagate(e);
        } finally {
            PGStorageTestUtil.closeDataSource(dataSource);
        }
    }

    private void delete(Connection cx, String table) throws SQLException {
        delete(cx, table, false);
    }

    private void delete(Connection cx, String table, boolean cascade) throws SQLException {
        String sql = String.format("DROP TABLE IF EXISTS %s %s", table, cascade ? "CASCADE" : "");
        execute(cx, sql);
    }

    private void execute(Connection cx, String sql) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }

    @Override
    public URI newRepositoryURI(String name, Platform platform) throws URISyntaxException {
        String repoURI = pgTestProperties.buildRepoURL(name, tablePrefix);
        URI repoUri = null;

        try {
            repoUri = new URI(repoURI);
        } catch (URISyntaxException e) {
            Throwables.propagate(e);
        }
        return repoUri;
    }

    @Override
    public URI buildRootURI(Platform platform) {
        String rootURI = pgTestProperties.buildRepoURL(null, tablePrefix);
        URI rootUri = null;

        try {
            rootUri = new URI(rootURI);
        } catch (URISyntaxException e) {
            Throwables.propagate(e);
        }
        return rootUri;
    }
}
