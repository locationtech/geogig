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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.EnvironmentBuilder;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.PGStorageTableManager;
import org.locationtech.geogig.storage.postgresql.config.TableNames;
import org.locationtech.geogig.storage.postgresql.config.Version;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

/**
 * Given a geogig database, performs any necessary database schema upgrade
 */
public class PGDatabaseUpgrade extends AbstractGeoGigOp<Void> {

    private Environment environment;

    private URI baseURI;

    protected @Override Void _call() {
        Environment env = resolveEnvironment();
        Version serverVersion = PGStorage.getServerVersion(env);
        PGStorageTableManager tableManager = PGStorageTableManager.forVersion(serverVersion);
        TableNames tables = env.getTables();
        DataSource dataSource = PGStorage.newDataSource(env);

        // DDL to tell the user to run if we don't have priviledges to
        List<String> DDL = new ArrayList<>();
        try (Connection cx = dataSource.getConnection()) {
            final int currentSchemaVersion = tableManager.getSchemaVersion(cx, tables);
            final boolean createMetadataTable = currentSchemaVersion == 0;
            if (createMetadataTable) {
                tableManager.createMetadata(DDL, tables);
            }
            final SchemaUpgrade0To1 upgrade = new SchemaUpgrade0To1(env);
            final boolean runUpgrade = upgrade.shouldRun(cx);
            if (runUpgrade) {
                DDL.addAll(upgrade.createDDL());
            }

            if (DDL.isEmpty()) {
                getProgressListener().setDescription(
                        "Database schema is up to date, checking for non DDL related upgrade actions...");
            } else {
                getProgressListener().setDescription("Running DDL script:");
                String script = "-- SCRIPT START --\n" + Joiner.on('\n').join(DDL)
                        + "\n-- SCRIPT END --\n";
                getProgressListener().setDescription(script);

                cx.setAutoCommit(false);
                if (runScript(cx, DDL, tableManager)) {
                    cx.commit();
                    cx.setAutoCommit(true);
                } else {
                    cx.rollback();
                    return null;
                }
            }
            if (runUpgrade) {
                upgrade.run(cx, tableManager, getProgressListener());
                getProgressListener().setDescription(
                        "Finished upgrading the geogig database to the latest version.");
            } else {
                getProgressListener()
                        .setDescription("Nothing to upgrade. Database schema is up to date");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }

    private boolean runScript(Connection cx, List<String> ddl, PGStorageTableManager tableManager) {
        if (ddl.isEmpty()) {
            return true;
        }
        try {
            tableManager.runScript(cx, ddl);
            return true;
        } catch (SQLException e) {
            getProgressListener().setDescription("Error running DDL script: " + e.getMessage()
                    + "\n Please run the script above manually and re-run this command.");
        }
        return false;
    }

    private Environment resolveEnvironment() {
        Preconditions.checkArgument(environment != null || baseURI != null,
                "Environment or base URI argument not provided");
        if (environment != null) {
            return environment;
        }
        EnvironmentBuilder environmentBuilder = new EnvironmentBuilder(baseURI, true);
        Environment env = environmentBuilder.build();
        return env;
    }

    public PGDatabaseUpgrade setEnvironment(Environment env) {
        this.environment = env;
        this.baseURI = null;
        return this;
    }

    public PGDatabaseUpgrade setBaseURI(URI baseURI) {
        this.environment = null;
        this.baseURI = baseURI;
        return this;
    }

}
