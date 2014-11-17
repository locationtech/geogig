/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.cli.porcelain;

import java.io.IOException;

import org.geotools.data.DataStore;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ObjectDatabaseReadOnly;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.geotools.plumbing.ImportOp;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Imports one or more tables from a SpatiaLite database.
 * 
 * SpatiaLite CLI proxy for {@link ImportOp}
 * 
 * @see ImportOp
 */
@ObjectDatabaseReadOnly
@Parameters(commandNames = "import", commandDescription = "Import SpatiaLite database")
public class SLImport extends AbstractSLCommand implements CLICommand {

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
    @Parameter(names = { "--add" }, description = "Do not replace or delete features on the destination path, but just add new ones")
    boolean add;

    /**
     * Use origin feature type
     */
    @Parameter(names = { "--force-featuretype" }, description = "Use origin feature type even if it does not match the default destination featuretype")
    boolean forceFeatureType;

    /**
     * Set the path default feature type to the the feature type of imported features, and modify
     * existing features to match it
     */
    @Parameter(names = { "--alter" }, description = "Set the path default feature type to the the feature type of imported features, and modify existing features to match it")
    boolean alter;

    /**
     * Executes the import command using the provided options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        DataStore dataStore = getDataStore();

        try {

            cli.getConsole().println("Importing from database " + commonArgs.database);

            ProgressListener progressListener = cli.getProgressListener();
            cli.getGeogig().command(ImportOp.class).setAll(all).setTable(table).setAlter(alter)
                    .setOverwrite(!add).setDataStore(dataStore)
                    .setAdaptToDefaultFeatureType(!forceFeatureType)
                    .setProgressListener(progressListener).call();

            cli.getConsole().println("Import successful.");

        } catch (GeoToolsOpException e) {
            switch (e.statusCode) {
            case TABLE_NOT_DEFINED:
                throw new CommandFailedException(
                        "No tables specified for import. Specify --all or --table <table>.", e);
            case ALL_AND_TABLE_DEFINED:
                throw new CommandFailedException(
                        "Specify --all or --table <table>, both cannot be set.", e);
            case NO_FEATURES_FOUND:
                throw new CommandFailedException("No features were found in the database.", e);
            case TABLE_NOT_FOUND:
                throw new CommandFailedException("Could not find the specified table.", e);
            case UNABLE_TO_GET_NAMES:
                throw new CommandFailedException("Unable to get feature types from the database.",
                        e);
            case UNABLE_TO_GET_FEATURES:
                throw new CommandFailedException("Unable to get features from the database.", e);
            case UNABLE_TO_INSERT:
                throw new CommandFailedException(
                        "Unable to insert features into the working tree.", e);
            case INCOMPATIBLE_FEATURE_TYPE:
                throw new CommandFailedException(
                        "The feature type of the data to import does not match the feature type of the destination tree and cannot be imported\n"
                                + "USe the --force-featuretype switch to import using the original featuretype and crete a mixed type tree",
                        e);
            case ALTER_AND_ALL_DEFINED:
                throw new CommandFailedException(
                        "Alter cannot be used with --all option and more than one table.", e);
            default:
                throw new CommandFailedException("Import failed with exception: "
                        + e.statusCode.name(), e);
            }
        } finally {
            dataStore.dispose();
            cli.getConsole().flush();
        }
    }
}
