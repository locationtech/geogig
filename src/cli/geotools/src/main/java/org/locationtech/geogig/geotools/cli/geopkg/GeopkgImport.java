/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.geotools.data.DataStore;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.geotools.cli.base.DataStoreImport;
import org.locationtech.geogig.geotools.geopkg.GeoPkgForwardingFeatureIteratorProvider;
import org.locationtech.geogig.geotools.geopkg.GeopkgGeogigMetadata;
import org.locationtech.geogig.geotools.plumbing.ForwardingFeatureIteratorProvider;
import org.locationtech.geogig.geotools.plumbing.ImportOp;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Imports one or more tables from a Geopackage database.
 * 
 * Geopackage CLI proxy for {@link ImportOp}
 * 
 * @see ImportOp
 */
@Command(name = "import", description = "Import Geopackage database")
public class GeopkgImport extends DataStoreImport implements CLICommand {

    public @ParentCommand GeopkgCommandProxy commonArgs;

    final GeopkgSupport support = new GeopkgSupport();

    protected @Override DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }

    protected @Override String getSourceDatabaseName() {
        return commonArgs.database;
    }

    private GeopkgGeogigMetadata metadata = null;

    protected @Override void runInternal(GeogigCLI cli) throws IOException {
        File databaseFile = new File(commonArgs.database);
        Preconditions.checkArgument(databaseFile.exists(), "Database file not found.");
        final GeoPackage geopackage = new GeoPackage(databaseFile);
        final DataSource dataSource = geopackage.getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            metadata = new GeopkgGeogigMetadata(connection);
            super.runInternal(cli);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            metadata.close();
            geopackage.close();
        }
    }

    /**
     * Returns a provider that provides a forwarding feature iterator to update the feature ids of
     * incoming features.
     * 
     * @return the forwarding feature iterator provider
     */
    protected ForwardingFeatureIteratorProvider getForwardingFeatureIteratorProvider() {
        return new GeoPkgForwardingFeatureIteratorProvider(metadata);
    }
}
