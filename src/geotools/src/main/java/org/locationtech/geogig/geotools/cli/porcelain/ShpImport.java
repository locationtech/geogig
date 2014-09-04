/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.porcelain;

import java.io.IOException;
import java.util.List;

import org.geotools.data.DataStore;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.ObjectDatabaseReadOnly;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.opengis.feature.type.AttributeDescriptor;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Imports features from one or more shapefiles.
 * 
 * PostGIS CLI proxy for {@link ImportOp}
 * 
 * @see ImportOp
 */
@ObjectDatabaseReadOnly
@Parameters(commandNames = "import", commandDescription = "Import Shapefile")
public class ShpImport extends AbstractShpCommand implements CLICommand {

    /**
     * Shapefiles to import.
     */
    @Parameter(description = "<shapefile> [<shapefile>]...")
    List<String> shapeFile;

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
     * Destination path to add features to. Only allowed when importing a single table
     */
    @Parameter(names = { "-d", "--dest" }, description = "Path to import to")
    String destTable;

    /**
     * The attribute to use to create the feature Id
     */
    @Parameter(names = { "--fid-attrib" }, description = "Use the specified attribute to create the feature Id")
    String fidAttribute;

    /**
     * Executes the import command using the provided options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(shapeFile != null && !shapeFile.isEmpty(), "No shapefile specified");

        for (String shp : shapeFile) {

            DataStore dataStore = null;
            try {
                dataStore = getDataStore(shp);
            } catch (InvalidParameterException e) {
                cli.getConsole().println(
                        "The shapefile '" + shp + "' could not be found, skipping...");
                continue;
            }
            if (fidAttribute != null) {
                AttributeDescriptor attrib = dataStore.getSchema(dataStore.getNames().get(0))
                        .getDescriptor(fidAttribute);
                if (attrib == null) {
                    throw new InvalidParameterException(
                            "The specified attribute does not exist in the selected shapefile");
                }
            }

            try {
                cli.getConsole().println("Importing from shapefile " + shp);

                ProgressListener progressListener = cli.getProgressListener();
                ImportOp command = cli.getGeogig().command(ImportOp.class).setAll(true)
                        .setTable(null).setAlter(alter).setOverwrite(!add)
                        .setDestinationPath(destTable).setDataStore(dataStore)
                        .setFidAttribute(fidAttribute)
                        .setAdaptToDefaultFeatureType(!forceFeatureType);

                // force the import not to use paging due to a bug in the shapefile datastore
                command.setUsePaging(false);

                command.setProgressListener(progressListener).call();

                cli.getConsole().println(shp + " imported successfully.");
            } catch (GeoToolsOpException e) {
                switch (e.statusCode) {
                case NO_FEATURES_FOUND:
                    throw new CommandFailedException("No features were found in the shapefile.", e);
                case UNABLE_TO_GET_NAMES:
                    throw new CommandFailedException(
                            "Unable to get feature types from the shapefile.", e);
                case UNABLE_TO_GET_FEATURES:
                    throw new CommandFailedException("Unable to get features from the shapefile.",
                            e);
                case UNABLE_TO_INSERT:
                    throw new CommandFailedException(
                            "Unable to insert features into the working tree.", e);
                case INCOMPATIBLE_FEATURE_TYPE:
                    throw new CommandFailedException(
                            "The feature type of the data to import does not match the feature type of the destination tree and cannot be imported\n"
                                    + "USe the --force-featuretype switch to import using the original featuretype and crete a mixed type tree",
                            e);
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
}
