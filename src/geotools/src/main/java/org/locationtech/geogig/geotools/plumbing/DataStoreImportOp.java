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
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

/**
 * Imports feature tress (layers) into a repository from a GeoTools {@link DataStore}. This
 * operation essentially decorates the {@link ImportOp} operation by calling {@link AddOp} followed
 * by {@link CommitOp} to make the import atomic.
 *
 */
public abstract class DataStoreImportOp<T> extends AbstractGeoGigOp<T> {

    /**
     * Extends the {@link com.google.common.base.Supplier} interface to provide for a way to request
     * resource cleanup, if applicable to the {@link org.geotools.data.DataStore DataStore}.
     */
    public static interface DataStoreSupplier extends Supplier<DataStore> {
        /**
         * Called after {@link DataStore#dispose()} on the supplied data store to clean up any
         * resource needed after {@link DataStoreImportOp} finished.
         */
        void cleanupResources();
    }

    protected DataStoreSupplier dataStoreSupplier;

    // commit options
    @Nullable
    protected String authorEmail;

    @Nullable
    protected String authorName;

    @Nullable
    protected String commitMessage;

    @Nullable
    protected String root;

    // import options
    @Nullable // except if all == false
    protected String table;

    protected boolean all = false;

    protected boolean add = false;

    protected boolean alter = false;

    protected boolean forceFeatureType = false;

    @Nullable
    protected String dest;

    @Nullable
    protected String fidAttribute;

    /**
     * Set the source {@link DataStore DataStore}, from which features should be imported.
     *
     * @param dataStore A {@link com.google.common.base.Supplier Supplier} for the source
     *        {@link DataStore DataStore} containing features to import into the repository.
     * @return A reference to this Operation.
     */
    public DataStoreImportOp<T> setDataStore(DataStoreSupplier dataStore) {
        this.dataStoreSupplier = dataStore;
        return this;
    }

    /**
     * Set the email address of the committer in the commit.
     * <p>
     * After the import completes, this operation will add the effective changes to the staging area
     * and then commit the changes. Setting this value will be reflected in the commit. Setting this
     * value is optional, but highly recommended for tracking changes. There is no default value for
     * authorEmail, and will default to the default {@link CommitOp} mechanism for resolving the
     * author email from the config database.
     *
     * @param authorEmail Email address of the committing author. Example: "john.doe@example.com"
     *
     * @return A reference to this operation.
     *
     * @see org.locationtech.geogig.porcelain.CommitOp#setAuthor(java.lang.String, java.lang.String)
     */
    public DataStoreImportOp<T> setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
        return this;
    }

    /**
     * Set the Author name of the committer in the commit.
     * <p>
     * After the import completes, this operation will add the effective changes to the staging area
     * and then commit the changes. Setting this value will be reflected in the commit. Setting this
     * value is optional, but highly recommended for tracking changes. There is no default value for
     * authorName, and will default to the default {@link CommitOp} mechanism for resolving the
     * author name from the config database.
     *
     * @param authorName The first and last name of the committing author. Example: "John Doe"
     *
     * @return A reference to this operation.
     *
     * @see org.locationtech.geogig.porcelain.CommitOp#setAuthor(java.lang.String, java.lang.String)
     */
    public DataStoreImportOp<T> setAuthorName(String authorName) {
        this.authorName = authorName;
        return this;
    }

    /**
     * Set the commit message.
     * <p>
     * After the import completes, this operation will add the effective changes to the staging area
     * and then commit the changes. Setting this value will be reflected in the commit. Setting this
     * value is optional, but highly recommended for tracking changes. There is no default commit
     * message.
     *
     * @param commitMessage The commit message for the commit. Example: "Update Buildings layer with
     *        new campus buildings"
     *
     * @return A reference to this operation.
     *
     * @see org.locationtech.geogig.porcelain.CommitOp#setMessage(java.lang.String)
     */
    public DataStoreImportOp<T> setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
        return this;
    }

    /**
     * Controls whether to import on top of existing features, or truncate the destination feature
     * tree before importing.
     * <p>
     * If set to {@code true}, the import proceeds on top of the existing feature. New features will
     * be recognized as added and existing features (matching feature ids) as modifications, but
     * it's impossible to identify deleted features.
     * <p>
     * If set to {@code false}, the existing feature tree for a given layer is first truncated, and
     * then the import proceeds on top of the emptied feature tree. The end result is that, compared
     * to the previous commit, both adds, modifications, and deletes are recognized by a diff
     * operation. Note however this is only practical if the whole layer is being re-imported.
     *
     * @param add {@code true} if features should be imported on top a pre-existing feature tree (if
     *        such exists) matching the imported layer name, {@code false} if the import shall
     *        proceed on an empty feature tree (truncating it first if such feature tree exists).
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setOverwrite(boolean)
     */
    public DataStoreImportOp<T> setAdd(boolean add) {
        this.add = add;
        return this;
    }

    /**
     * Sets the all flag, that then true, all tables from the source datastore will be imported into
     * the repository.
     * <p>
     * If set to false, {@link DataStoreImportOp#setTable(java.lang.String)} must be used to set a
     * specific table to import. Attributes <b>all</b> and <b>table</b> are mutually exclusive. You
     * <b>MUST</b> set one and leave the other unset. The default is false.
     *
     * @param all True if all tables from the datastore should be imported into the repository,
     *        false if only a specified table should be imported.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setAll(boolean)
     * @see ImportOp#setTable(java.lang.String)
     * @see DataStoreImportOp#setTable(java.lang.String)
     */
    public DataStoreImportOp<T> setAll(boolean all) {
        this.all = all;
        return this;
    }

    /**
     * Sets the alter flag.
     * <p>
     * If set to true, this import operation will set the default feature type of the repository
     * path destination to match the feature type of the features being imported, and <b>alter</b>
     * the feature type of all features in the destination to match the feature type of the features
     * being imported. The default is false.
     *
     * @param alter True if this import operation should alter the feature type of the repository
     *        path destination to the feature type of the features being imported, false if no
     *        altering should occur.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setAlter(boolean)
     */
    public DataStoreImportOp<T> setAlter(boolean alter) {
        this.alter = alter;
        return this;
    }

    /**
     * Sets the attribute from which the Feature Id should be created.
     * <p>
     * If not set, the Feature Id provided by each {@link Feature#getIdentifier()} is used. The
     * default is <b>null</b> and uses the default method in {@link ImportOp ImportOp}.
     * <p>
     * This is useful when the source DataStore can't provide stable feature ids (e.g. the Shapefile
     * datastore)
     *
     * @param fidAttribute The Attribute from which the Feature Id should be created. Null if the
     *        default Feature Id creation should be used.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setFidAttribute(java.lang.String)
     */
    public DataStoreImportOp<T> setFidAttribute(String fidAttribute) {
        this.fidAttribute = fidAttribute;
        return this;
    }

    /**
     * Sets the table name within the source DataStore from which features should be imported.
     * <p>
     * If a table name is set, the <b>all</b> flag must NOT be set. If no table name is set,
     * {@link DataStoreImportOp#setAll(boolean)} must be used to set <b>all</b> to true to import
     * all tables. Attributes <b>all</b> and <b>table</b> are mutually exclusive. You <b>MUST</b>
     * set one and leave the other unset. The default is null/unset.
     *
     * @param table The name of the table within the source DataStore from which features should be
     *        imported, NULL if all tables should be imported.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setTable(java.lang.String)
     * @see ImportOp#setAll(boolean)
     * @see DataStoreImportOp#setAll(boolean)
     */
    public DataStoreImportOp<T> setTable(String table) {
        this.table = table;
        return this;
    }

    /**
     * Sets the Force Feature Type flag.
     * <p>
     * If set to true, use the feature type of the features to be imported from the source
     * DataStore, even if it does not match the default feature type of the repository destination
     * path. If set to false, this import operation will try to adapt the features being imported to
     * the feature type of the repository destination path, if it is not the same. The default is
     * false. NOTE: this flag behaves as the inverse of
     * {@link ImportOp#setAdaptToDefaultFeatureType(boolean)}
     *
     * @param forceFeatureType True if the source feature type should be used on import, false if
     *        the destination feature type should be used on import.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setAdaptToDefaultFeatureType(boolean)
     */
    public DataStoreImportOp<T> setForceFeatureType(boolean forceFeatureType) {
        this.forceFeatureType = forceFeatureType;
        return this;
    }

    /**
     * Sets the repository destination path.
     * <p>
     * If this value is set, the value provided will be used as the repository destination path name
     * on import. If not set, the path name will be derived from the feature table being imported.
     * The default is null/unset.
     *
     * @param dest The name of the repository destination path into which features should be
     *        imported, or null if the path should be derived from the table name of the features
     *        being imported.
     *
     * @return A reference to this operation.
     *
     * @see ImportOp#setDestinationPath(java.lang.String)
     */
    public DataStoreImportOp<T> setDest(String dest) {
        this.dest = dest;
        return this;
    }

    /**
     * Sets the root for the import.
     * <p>
     * If this value is set, the imported features will be applied to the specified branch. If not
     * set, it will default to the HEAD branch.
     * 
     * @param root The branch to import the features on to.
     * 
     * @return A reference to this operation.
     */
    public DataStoreImportOp<T> setRoot(String root) {
        this.root = root;
        return this;
    }

    @Override
    protected T _call() {
        SymRef originalHead = null;
        if (root != null) {
            Preconditions.checkArgument(
                    !root.startsWith(Ref.HEADS_PREFIX) && !root.startsWith(Ref.REMOTES_PREFIX));
            Optional<Ref> head = command(RefParse.class).setName(Ref.HEAD).call();
            Preconditions.checkState(head.isPresent(), "Could not find HEAD ref.");
            Preconditions.checkState(head.get() instanceof SymRef,
                    "Unable to import with detatched HEAD.");
            originalHead = (SymRef) head.get();
            Optional<Ref> rootBranch = command(RefParse.class).setName(root).call();
            Preconditions.checkArgument(rootBranch.isPresent(),
                    "Unable to resolve '" + root + "' branch.");
            Preconditions.checkArgument(rootBranch.get().getName().startsWith(Ref.HEADS_PREFIX),
                    "Root must be a local branch.");
            command(CheckoutOp.class).setSource(rootBranch.get().getName()).call();
        }
        T importOpResult = callInternal();
        if (originalHead != null) {
            command(CheckoutOp.class).setSource(originalHead.getTarget()).call();
        }
        return importOpResult;
    }

    protected abstract T callInternal();
}
