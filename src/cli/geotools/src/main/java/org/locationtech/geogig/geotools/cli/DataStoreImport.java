/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli;

import java.io.IOException;

import org.geotools.data.DataStore;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.geotools.plumbing.ForwardingFeatureIteratorProvider;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.opengis.filter.Filter;

import com.beust.jcommander.Parameter;

/**
 * Imports one or more feature types from a {@link DataStore} given by the concrete subclass.
 * 
 * @see ImportOp
 */
public abstract class DataStoreImport extends AbstractCommand implements CLICommand {

    /**
     * If this is set, only this table will be imported.
     */
    @Parameter(names = { "--table", "-t" }, description = "Table to import.")
    public String table = "";

    /**
     * If this is set, all tables will be imported.
     */
    @Parameter(names = "--all", description = "Import all tables.")
    public boolean all = false;

    /**
     * do not replace or delete features
     */
    @Parameter(names = {
            "--add" }, description = "Do not replace or delete features on the destination path, but just add new ones")
    boolean add;

    /**
     * Use origin feature type
     */
    @Parameter(names = {
            "--force-featuretype" }, description = "Use origin feature type even if it does not match the default destination featuretype")
    boolean forceFeatureType;

    /**
     * Set the path default feature type to the the feature type of imported features, and modify
     * existing features to match it
     */
    @Parameter(names = {
            "--alter" }, description = "Set the path default feature type to the the feature type of imported features, and modify existing features to match it")
    boolean alter;

    /**
     * Destination path to add features to. Only allowed when importing a single table
     */
    @Parameter(names = { "-d", "--dest" }, description = "Path to import to")
    String destTable;

    /**
     * The attribute to use to create the feature Id
     */
    @Parameter(names = {
            "--fid-attrib" }, description = "Use the specified attribute to create the feature Id")
    String fidAttribute;

    @Parameter(names = { "-f", "--cql-filter" }, description = "GetoTools ECQL filter")
    String cqlFilter;

    protected abstract String getSourceDatabaseName();

    protected abstract DataStore getDataStore();

    /**
     * Executes the import command using the provided options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        DataStore dataStore = getDataStore();

        try {
            Filter filter = Filter.INCLUDE;
            if (cqlFilter != null) {
                try {
                    filter = ECQL.toFilter(cqlFilter);
                } catch (CQLException e) {
                    throw new IllegalArgumentException(
                            "Error parsing CQL filter: " + e.getMessage(), e);
                }
            }

            cli.getConsole().println("Importing from database " + getSourceDatabaseName());

            ProgressListener progressListener = cli.getProgressListener();

            ImportOp op = cli.getGeogig().command(ImportOp.class).setAll(all).setTable(table)
                    .setAlter(alter).setDestinationPath(destTable).setOverwrite(!add)
                    .setDataStore(dataStore).setAdaptToDefaultFeatureType(!forceFeatureType)
                    .setFidAttribute(fidAttribute).setFilter(filter);
            ForwardingFeatureIteratorProvider transformer = getForwardingFeatureIteratorProvider();
            if (transformer != null) {
                op.setForwardingFeatureIteratorProvider(transformer);
            }

            op.setProgressListener(progressListener).call();

            cli.getConsole().println("Import successful.");

        } catch (GeoToolsOpException e) {
            switch (e.statusCode) {
            case TABLE_NOT_DEFINED:
                throw new CommandFailedException(
                        "No tables specified for import. Specify --all or --table <table>.", true);
            case ALL_AND_TABLE_DEFINED:
                throw new CommandFailedException(
                        "Specify --all or --table <table>, both cannot be set.", true);
            case NO_FEATURES_FOUND:
                throw new CommandFailedException("No features were found in the database.", true);
            case TABLE_NOT_FOUND:
                throw new CommandFailedException("Could not find the specified table.", true);
            case UNABLE_TO_GET_NAMES:
                throw new CommandFailedException("Unable to get feature types from the database.",
                        e);
            case UNABLE_TO_GET_FEATURES:
                throw new CommandFailedException("Unable to get features from the database.", e);
            case UNABLE_TO_INSERT:
                throw new CommandFailedException("Unable to insert features into the working tree.",
                        e);
            case INCOMPATIBLE_FEATURE_TYPE:
                throw new CommandFailedException(
                        "The feature type of the data to import does not match the feature type of the destination tree and cannot be imported\n"
                                + "USe the --force-featuretype switch to import using the original featuretype and crete a mixed type tree",
                        true);
            case ALTER_AND_ALL_DEFINED:
                throw new CommandFailedException(
                        "Alter cannot be used with --all option and more than one table.", true);
            default:
                throw new CommandFailedException(
                        "Import failed with exception: " + e.statusCode.name(), e);
            }
        } finally {
            dataStore.dispose();
            cli.getConsole().flush();
        }
    }

    /**
     * Returns a {@link ForwardingFeatureIteratorProvider}. It can be used to transform incoming
     * features. If the function returns {@code null}, the features will not be transformed.
     * 
     * @return the forwarding feature iterator provider
     */
    protected ForwardingFeatureIteratorProvider getForwardingFeatureIteratorProvider() {
        return null;
    }
}
