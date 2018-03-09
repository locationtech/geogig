/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.geotools.data.DataStore;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp;
import org.locationtech.geogig.geotools.plumbing.ForwardingFeatureIteratorProvider;
import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.WorkingTree;

/**
 * Imports layers from a GeoPackage file to the repository.
 * 
 * @see DataStoreImportOp
 * @see GeopkgAuditImport
 */
public class GeopkgDataStoreImportOp extends DataStoreImportOp<RevCommit> {

    private File geopackage;

    public GeopkgDataStoreImportOp setDatabaseFile(File geopackage) {
        this.geopackage = geopackage;
        return this;
    }

    @Override
    protected RevCommit callInternal() {
        final DataStore dataStore = dataStoreSupplier.get();

        RevCommit revCommit = null;

        /**
         * Import needs to: 1) Import the data 2) Add changes to be staged 3) Commit staged changes
         */
        try {
            // import data into the repository
            final ImportOp importOp = getImportOp(dataStore);
            final GeoPackage geopkg = new GeoPackage(geopackage);
            try {
                final DataSource dataSource = geopkg.getDataSource();
                try (Connection connection = dataSource.getConnection()) {
                    try (GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(connection)) {
                        importOp.setForwardingFeatureIteratorProvider(
                                getFeatureIteratorTransformer(metadata));
                        importOp.setProgressListener(getProgressListener());
                        importOp.call();
                        // add the imported data to the staging area
                        callAdd();
                        // commit the staged changes
                        revCommit = callCommit();
                    }
                }
            } finally {
                geopkg.close();
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            dataStore.dispose();
            dataStoreSupplier.cleanupResources();
        }

        return revCommit;
    }

    private WorkingTree callAdd() {
        final AddOp addOp = context.command(AddOp.class);
        addOp.setProgressListener(getProgressListener());
        return addOp.call();
    }

    private RevCommit callCommit() {
        final CommitOp commitOp = context.command(CommitOp.class).setAll(true)
                .setAuthor(authorName, authorEmail).setMessage(commitMessage);
        commitOp.setProgressListener(getProgressListener());
        return commitOp.call();
    }

    private ImportOp getImportOp(DataStore dataStore) {
        final ImportOp importOp = context.command(ImportOp.class);
        return importOp.setDataStore(dataStore).setTable(table).setAll(all).setOverwrite(!add)
                .setAdaptToDefaultFeatureType(!forceFeatureType).setAlter(alter)
                .setDestinationPath(dest).setFidAttribute(fidAttribute);
    }

    private ForwardingFeatureIteratorProvider getFeatureIteratorTransformer(
            GeopkgGeogigMetadata metadata) {
        return new GeoPkgForwardingFeatureIteratorProvider(metadata);
    }
}
