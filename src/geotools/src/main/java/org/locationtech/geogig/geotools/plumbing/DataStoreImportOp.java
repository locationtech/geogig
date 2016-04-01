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

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.repository.WorkingTree;

/**
 * Imports feature tress (layers) into a repository from a GeoTools {@link DataStore}. This
 * operation essentially decorates the {@link ImportOp} operation by calling {@link AddOp} followed
 * by {@link CommitOp} to make the import atomic.
 *
 */
public class DataStoreImportOp extends AbstractGeoGigOp<RevCommit> {

    private DataStoreSupplier dataStoreSupplier;
    // commit options
    @Nullable
    private String authorEmail;
    @Nullable
    private String authorName;
    @Nullable
    private String commitMessage;
    // import options
    @Nullable
    private String table;
    @Nullable
    private boolean all;
    @Nullable
    private boolean add;
    @Nullable
    private boolean alter;
    @Nullable
    private boolean forceFeatureType;
    @Nullable
    private String dest;
    @Nullable
    private String fidAttribute;

    /**
     * Set the source {@link DataStore DataStore}, from which features should be imported.
     *
     * @param dataStore A {@link com.google.common.base.Supplier Supplier} for the source
     *                  {@link DataStore DataStore} containing features to import into the
     *                  repository.
     * @return A reference to this Operation.
     */
    public DataStoreImportOp setDataStore(DataStoreSupplier dataStore) {
        this.dataStoreSupplier = dataStore;
        return this;
    }

    @Override
    protected RevCommit _call() {

        final DataStore dataStore = dataStoreSupplier.get();

        RevCommit revCommit;

        /**
         * Import needs to: 1) Import the data 2) Add changes to be staged 3) Commit staged changes
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
        final CommitOp commitOp = context.command(CommitOp.class).setAll(true).setAuthor(
            authorName, authorEmail).setMessage(commitMessage);
        commitOp.setProgressListener(getProgressListener());
        return commitOp.call();
    }

    private ImportOp getImportOp(DataStore dataStore) {
        final ImportOp importOp = context.command(ImportOp.class);
        return importOp.setDataStore(dataStore).setTable(table).setAll(all).setOverwrite(!add)
            .setAdaptToDefaultFeatureType(!forceFeatureType).setAlter(alter)
            .setDestinationPath(dest).setFidAttribute(fidAttribute);
    }

    /**
     * Set the email address of the committer in the commit. After the import completes, this
     * operation will add the effective changes to the staging area and then commit the changes.
     * Setting this value will be reflected in the commit. Setting this value is optional, but
     * highly recommended for tracking changes. There is no default value for authorEmail.
     *
     * @param authorEmail Email address of the committing author. Example: "john.doe@example.com"
     *
     * @return A reference to this operation.
     *
     * @see org.locationtech.geogig.api.porcelain.CommitOp#setAuthor(java.lang.String,
     * java.lang.String)
     */
    public DataStoreImportOp setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
        return this;
    }

    /**
     * Set the Author name of the committer in the commit. After the import completes, this
     * operation will add the effective changes to the staging area and then commit the changes.
     * Setting this value will be reflected in the commit. Setting this value is optional, but
     * highly recommended for tracking changes. There is no default value for authorName.
     *
     * @param authorName The first and last name of the committing author. Example: "John Doe"
     *
     * @return A reference to this operation.
     *
     * @see org.locationtech.geogig.api.porcelain.CommitOp#setAuthor(java.lang.String,
     * java.lang.String)
     */
    public DataStoreImportOp setAuthorName(String authorName) {
        this.authorName = authorName;
        return this;
    }

    /**
     * Set the commit message. After the import completes, this operation will add the effective
     * changes to the staging area and then commit the changes. Setting this value will be reflected
     * in the commit. Setting this value is optional, but highly recommended for tracking changes.
     * There is no default commit message.
     *
     * @param commitMessage The commit message for the commit. Example: "Update Buildings layer with
     *                      new campus buildings"
     *
     * @return A reference to this operation.
     *
     * @see org.locationtech.geogig.api.porcelain.CommitOp#setMessage(java.lang.String)
     */
    public DataStoreImportOp setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
        return this;
    }

    /**
     * Sets the add flag. If set to true, only features that do not currently exist in the
     * repository will be added. Existing features will <b>not</b> be overwritten. If set to false,
     * any features existing in the repository will be overwritten, if they match the path and ID of
     * a feature contained within this import operations. This flag is an inversion of the
     * <b>overwite</b> flag in {@link ImportOp ImportOp}. The default is <b>false</b>, i.e. any
     * pre-existing features will be overwritten if they match path and ID of imported features.
     *
     * @param add True if this import should add <b>only</b> non-existing features. False if
     *            imported features should overwrite any pre-existing features that match on path
     *            and ID
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setOverwrite(boolean)
     */
    public DataStoreImportOp setAdd(boolean add) {
        this.add = add;
        return this;
    }

    /**
     * Sets the all flag. If set to true, all tables from the source datastore will be imported into
     * the repository. If set to false, {@link DataStoreImportOp#setTable(java.lang.String)} must be
     * used to set a specific table to import. Attributes <b>all</b> and <b>table</b> are mutually
     * exclusive. You <b>MUST</b> set one and leave the other unset. The default is false.
     *
     * @param all True if all tables from the datastore should be imported into the repository,
     *            false if only a specified table should be imported.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setAll(boolean)
     * @see ImportOp#setTable(java.lang.String)
     * @see DataStoreImportOp#setTable(java.lang.String)
     */
    public DataStoreImportOp setAll(boolean all) {
        this.all = all;
        return this;
    }

    /**
     * Sets the alter flag. If set to true, this import operation will set the default feature type
     * of the repository path destination to match the feature type of the features being imported,
     * and <b>alter</b> the feature type of all features in the destination to match the feature
     * type of the features being imported. The default is false.
     *
     * @param alter True if this import operation should alter the feature type of the repository
     *              path destination to the feature type of the features being imported, false if no
     *              altering should occur.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setAlter(boolean)
     */
    public DataStoreImportOp setAlter(boolean alter) {
        this.alter = alter;
        return this;
    }

    /**
     * Sets the attribute from which the Feature Id should be created. If not set, a default value
     * is used to create the Feature Id. The default is <b>null</b> and uses the default method in
     * {@link ImportOp ImportOp}.
     *
     * @param fidAttribute The Attribute from which the Feature Id should be created. Null if the
     *                     default Feature Id creation should be used.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setFidAttribute(java.lang.String)
     */
    public DataStoreImportOp setFidAttribute(String fidAttribute) {
        this.fidAttribute = fidAttribute;
        return this;
    }

    /**
     * Sets the table name within the source DataStore from which features should be imported. If a
     * table name is set, the <b>all</b> flag must NOT be set. If no table name is set,
     * {@link DataStoreImportOp#setAll(boolean)} must be used to set <b>all</b> to true to import
     * all tables. Attributes <b>all</b> and <b>table</b> are mutually exclusive. You <b>MUST</b>
     * set one and leave the other unset. The default is null/unset.
     *
     * @param table The name of the table within the source DataStore from which features should be
     *              imported, NULL if all tables should be imported.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setTable(java.lang.String)
     * @see ImportOp#setAll(boolean)
     * @see DataStoreImportOp#setAll(boolean)
     */
    public DataStoreImportOp setTable(String table) {
        this.table = table;
        return this;
    }

    /**
     * Sets the Force Feature Type flag. If set to true, use the feature type of the features to be
     * imported from the source DataStore, even if it does not match the default feature type of the
     * repository destination path. If set to false, this import operation will try to adapt the
     * features being imported to the feature type of the repository destination path, if it is not
     * the same. The default is false. NOTE: this flag behaves as the inverse of
     * {@link ImportOp#setAdaptToDefaultFeatureType(boolean)}
     *
     * @param forceFeatureType True if the source feature type should be used on import, false if
     *                         the destination feature type should be used on import.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setAdaptToDefaultFeatureType(boolean)
     */
    public DataStoreImportOp setForceFeatureType(boolean forceFeatureType) {
        this.forceFeatureType = forceFeatureType;
        return this;
    }

    /**
     * Sets the repository destination path. If this value is set, the value provided will be used
     * as the repository destination path name on import. If not set, the path name will be derived
     * from the feature table being imported. The default is null/unset.
     *
     * @param dest The name of the repository destination path into which features should be
     *             imported, or null if the path should be derived from the table name of the
     *             features being imported.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setDestinationPath(java.lang.String)
     */
    public DataStoreImportOp setDest(String dest) {
        this.dest = dest;
        return this;
    }
}
