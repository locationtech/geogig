/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.shp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.DataStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.geotools.cli.DataStoreDescribe;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.io.Files;

/**
 * Describes the schema of a shapefile.
 */
@RequiresRepository(false)
@Parameters(commandNames = "describe", commandDescription = "Describe a shapefile schema")
public class ShpDescribe extends AbstractShpCommand implements CLICommand {

    @Parameter(description = "<shapefile>... path to the shapefile to describe", arity = 1)
    public List<String> args = new ArrayList<>(2);

    @Override
    protected void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {
        checkParameter(!args.isEmpty(), "No shapefile argument provided");

        for (String shapefile : args) {
            DataStoreDescribe cmd = new DataStoreDescribe() {
                @Override
                protected DataStore getDataStore() {
                    DataStore dataStore = ShpDescribe.this.getDataStore(shapefile, null);
                    return dataStore;
                }
            };

            String typeName = Files.getNameWithoutExtension(shapefile);
            cmd.table = typeName;
            cmd.run(cli);
        }
    }

}
