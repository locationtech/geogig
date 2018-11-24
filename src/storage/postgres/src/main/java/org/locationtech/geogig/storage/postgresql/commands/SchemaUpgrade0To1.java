/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.commands;

import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.locationtech.geogig.plumbing.RebuildGraphOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.PGStorageTableManager;

/**
 * Upgrades a geogig database from the schema used for Postgres 9/geogig < 1.2.1, to Postgres
 * 10/geogig 1.2.1
 * <p>
 * For instance, adds the columns {@code srcindex} and {@code dstindex} to the
 * {@code geogig_graph_edge} table, and runs {@link RebuildGraphOp} on each repository in the
 * database.
 * 
 * @since 1.2.1
 */
public class SchemaUpgrade0To1 {

    private Environment env;

    public SchemaUpgrade0To1(Environment env) {
        this.env = env;
    }

    public boolean shouldRun(Connection cx) throws SQLException {
        DatabaseMetaData md = cx.getMetaData();

        final String tableName = env.getTables().graphEdges();
        final String schema = PGStorageTableManager.schema(tableName);
        final String table = PGStorageTableManager.stripSchema(tableName);

        try (ResultSet columns = md.getColumns(null, schema, table, "dstindex")) {
            return !columns.next();
        }
    }

    public List<String> createDDL() {
        List<String> ddl = new ArrayList<>();
        String edges = env.getTables().graphEdges();
        ddl.add(String.format("TRUNCATE %s;", edges));
        ddl.add(String.format("ALTER TABLE %s ADD COLUMN dstindex INT NOT NULL;", edges));
        return ddl;
    }

    public void run(Connection cx, PGStorageTableManager tableManager, ProgressListener progress)
            throws SQLException {
        List<String> repoNames = PGStorage.listRepos(cx, env.getTables());
        progress.setDescription(String.format("Upgrading commit graph for all %,d repositories...",
                repoNames.size()));
        Collections.sort(repoNames);
        int i = 0;
        for (String repoName : repoNames) {
            progress.setProgressIndicator(
                    p -> String.format("Upgrading graph for repository %s", repoName));
            URI repoURI = env.connectionConfig.toURI(repoName);
            Repository repo;
            try {
                repo = RepositoryResolver.load(repoURI);
            } catch (RepositoryConnectionException e) {
                throw new IllegalStateException("Unable to connnect to repo " + repoName, e);
            }
            try {
                progress.setProgress(++i);
                rebuildGraph(repo);
            } finally {
                repo.close();
            }
        }
        progress.setProgressIndicator(ProgressListener.DEFAULT_PROGRES_INDICATOR);
    }

    private void rebuildGraph(Repository repo) {
        repo.command(RebuildGraphOp.class).call();
    }
}
