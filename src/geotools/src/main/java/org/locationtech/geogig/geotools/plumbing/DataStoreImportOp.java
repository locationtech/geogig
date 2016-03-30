/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import org.geotools.data.DataStore;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.repository.WorkingTree;

import com.google.common.base.Supplier;

/**
 * Imports feature tress (layers) into a repository from a GeoTools {@link DataStore}.
 *
 */
public class DataStoreImportOp extends AbstractGeoGigOp<RevCommit> {

    private Supplier<DataStore> dataStoreSupplier;
    // commit options
    private String authorEmail;
    private String authorName;
    private String commitMessage;
    // import options
    private String table;
    private boolean all;
    private boolean add;
    private boolean alter;
    private boolean forceFeatureType;
    private String dest;
    private String fidAttribute;

    /**
     * Set the source {@link DataStore DataStore}, from which features should be imported.
     *
     * @param dataStore A {@link com.google.common.base.Supplier Supplier} for the source
     *                  {@link DataStore DataStore} containing features to import into the
     *                  repository.
     * @return A reference to this Operation.
     */
    public DataStoreImportOp setDataStore(Supplier<DataStore> dataStore) {
        this.dataStoreSupplier = dataStore;
        return this;
    }

    @Override
    protected RevCommit _call() {

        final DataStore dataStore = dataStoreSupplier.get();

        RevCommit revCommit;

        /**
         * Import needs to:
         * 1) Import the data
         * 2) Add changes to be staged
         * 3) Commit staged changes
         */
        try {
            // import data into the repository
            final ImportOp importOp = getImportOp(dataStore);
            importOp.setProgressListener(getProgressListener());
            final RevTree revTree = importOp.call();
            // add the imported data to the staging area
            final WorkingTree workingTree = callAdd();
            // commit the staged changes
            revCommit = callCommit();
        } finally {
            dataStore.dispose();
        }

        return revCommit;
    }

    private WorkingTree callAdd() {
        final AddOp addOp = context.command(AddOp.class);
        addOp.setProgressListener(getProgressListener());
        return addOp.call();
    }

    private RevCommit callCommit() {
        final CommitOp commitOp = context.command(CommitOp.class).setAll(true).setAuthor(
            getAuthorName(), getAuthorEmail()).setMessage(getCommitMessage());
        commitOp.setProgressListener(getProgressListener());
        return commitOp.call();
    }

    protected ImportOp getImportOp(DataStore dataStore) {
        final ImportOp importOp = context.command(ImportOp.class);
        return importOp.setDataStore(dataStore).setTable(table).setAll(all).setOverwrite(!add)
            .setAdaptToDefaultFeatureType(!forceFeatureType).setAlter(alter)
            .setDestinationPath(dest).setFidAttribute(fidAttribute);
    }

    protected String getAuthorName() {
        return this.authorName;
    }

    protected String getAuthorEmail() {
        return this.authorEmail;
    }

    protected String getCommitMessage() {
        return this.commitMessage;
    }

    public DataStoreImportOp setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
        return this;
    }

    public DataStoreImportOp setAuthorName(String authorName) {
        this.authorName = authorName;
        return this;
    }

    public DataStoreImportOp setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
        return this;
    }

    public DataStoreImportOp setAdd(boolean add) {
        this.add = add;
        return this;
    }

    public DataStoreImportOp setAll(boolean all) {
        this.all = all;
        return this;
    }

    public DataStoreImportOp setAlter(boolean alter) {
        this.alter = alter;
        return this;
    }

    public DataStoreImportOp setFidAttribute(String fidAttribute) {
        this.fidAttribute = fidAttribute;
        return this;
    }

    public DataStoreImportOp setTable(String table) {
        this.table = table;
        return this;
    }

    public DataStoreImportOp setForceFeatureType(boolean forceFeatureType) {
        this.forceFeatureType = forceFeatureType;
        return this;
    }

    public DataStoreImportOp setDest(String dest) {
        this.dest = dest;
        return this;
    }
}
