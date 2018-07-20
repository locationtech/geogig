/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geojson;

import java.io.IOException;
import java.util.List;

import org.geotools.data.DataStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.ProgressListener;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;

@Parameters(commandNames = "import", commandDescription = "Import GeoJSON")
public class GeoJsonImport extends AbstractGeoJsonCommand implements CLICommand {

    /**
     * GeoJSON files to import.
     */
    @Parameter(description = "<geojson> [<geojson>]...")
    List<String> geoJSONList;

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
     * Name to use for geometry attribute, replacing the default one ("geometry")
     */
    @Parameter(names = {
            "--geom-name" }, description = "Name to use for geometry attribute, replacing the default one ('geometry')")
    String geomName;

    /**
     * Name to use for geometry attribute, replacing the default one ("geometry")
     */
    @Parameter(names = {
            "--geom-name-auto" }, description = "Uses the name of the geometry descriptor in the destination feature type")
    boolean geomNameAuto;

    /**
     * The attribute to use to create the feature Id
     */
    @Parameter(names = {
            "--fid-attrib" }, description = "Use the specified attribute to create the feature Id")
    String fidAttribute;

    @Override
    protected void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {
        checkParameter(geoJSONList != null && !geoJSONList.isEmpty(), "No GeoJSON specified");
        checkParameter(geomName == null || !geomNameAuto,
                "Cannot use --geom-name and --geom-name-auto at the same time");

        for (String geoJSON : geoJSONList) {
            DataStore dataStore = null;
            try {
                dataStore = getDataStore(geoJSON);
            } catch (InvalidParameterException e) {
                cli.getConsole().println(
                        "The GeoJSON file '" + geoJSON + "' could not be found, skipping...");
                continue;
            }
            if (fidAttribute != null) {
                AttributeDescriptor attrib = dataStore.getSchema(dataStore.getNames().get(0))
                        .getDescriptor(fidAttribute);
                if (attrib == null) {
                    throw new InvalidParameterException(
                            "The specified attribute does not exist in the selected GeoJSON file");
                }
            }
            if (geomNameAuto) {
                String destPath = destTable;
                if (destPath == null) {
                    destPath = dataStore.getSchema(dataStore.getNames().get(0)).getTypeName();
                }
                Optional<RevFeatureType> ft = cli.getGeogig().command(RevObjectParse.class)
                        .setRefSpec("WORK_HEAD:" + destPath).call(RevFeatureType.class);
                // If there is previous data in the destination tree, we try to get the name of the
                // geom attribute.
                // If the destination tree does not exist, we use the default name for the geometry
                // attribute
                if (ft.isPresent()) {
                    GeometryDescriptor geomDescriptor = ft.get().type().getGeometryDescriptor();
                    if (geomDescriptor != null) {
                        geomName = geomDescriptor.getLocalName();
                    }
                }
            }
            try {
                cli.getConsole().println("Importing from GeoJSON " + geoJSON);

                ProgressListener progressListener = cli.getProgressListener();
                cli.getGeogig().command(ImportOp.class).setAll(true).setTable(null).setAlter(alter)
                        .setOverwrite(!add).setDestinationPath(destTable).setDataStore(dataStore)
                        .setFidAttribute(fidAttribute).setGeometryNameOverride(geomName)
                        .setAdaptToDefaultFeatureType(!forceFeatureType)
                        .setProgressListener(progressListener).call();

                cli.getConsole().println(geoJSON + " imported successfully.");

            } catch (GeoToolsOpException e) {
                switch (e.statusCode) {
                case NO_FEATURES_FOUND:
                    throw new CommandFailedException("No features were found in the GeoJSON file.",
                            true);
                case UNABLE_TO_GET_NAMES:
                    throw new CommandFailedException(
                            "Unable to get feature types from the GeoJSON file.", e);
                case UNABLE_TO_GET_FEATURES:
                    throw new CommandFailedException(
                            "Unable to get features from the GeoJSON file.", e);
                case UNABLE_TO_INSERT:
                    throw new CommandFailedException(
                            "Unable to insert features into the working tree.", e);
                case INCOMPATIBLE_FEATURE_TYPE:
                    throw new CommandFailedException(
                            "The feature type of the data to import does not match the feature type of the destination tree and cannot be imported\n"
                                    + "USe the --force-featuretype switch to import using the original featuretype and crete a mixed type tree",
                            true);
                default:
                    throw new CommandFailedException(
                            "Import failed with exception: " + e.statusCode.name(), e);
                }
            }
        }

    }
}
