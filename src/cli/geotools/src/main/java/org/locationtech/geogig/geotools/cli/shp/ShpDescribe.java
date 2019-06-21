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
import org.locationtech.geogig.geotools.cli.base.DataStoreDescribe;

import com.google.common.io.Files;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Describes the schema of a shapefile.
 */
@RequiresRepository(false)
@Command(name = "describe", description = "Describe a shapefile schema")
public class ShpDescribe extends AbstractShpCommand implements CLICommand {

    @Parameters(description = "<shapefile>... path to the shapefiles to describe", arity = "1..*")
    public List<String> files = new ArrayList<>(2);

    protected @Override void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {
        checkParameter(!files.isEmpty(), "No shapefile argument provided");

        for (String shapefile : files) {
            DataStoreDescribe cmd = new DataStoreDescribe() {
                protected @Override DataStore getDataStore() {
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
