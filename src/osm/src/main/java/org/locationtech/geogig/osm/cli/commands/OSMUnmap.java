/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.cli.commands;

import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.osm.internal.OSMUnmapOp;
import org.locationtech.geogig.repository.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Updates the raw OSM data of the repository (stored in the "node" and "way" trees), with the data
 * in a tree that represents a mapped version of that raw data
 * 
 * @see OSMUnmapOp
 */
@Parameters(commandNames = "unmap", commandDescription = "Updates the raw OSM data, unmapping the mapped OSM data in a given tree in the working tree")
public class OSMUnmap extends AbstractCommand implements CLICommand {

    @Parameter(description = "<path>")
    public List<String> args;

    private GeoGIG geogig;

    /**
     * Executes the map command using the provided options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {

        if (args == null || args.isEmpty() || args.size() != 1) {
            printUsage(cli);
            throw new CommandFailedException();
        }

        String path = args.get(0);

        geogig = cli.getGeogig();

        ObjectId oldTreeId = geogig.getRepository().workingTree().getTree().getId();

        ObjectId newTreeId = geogig.command(OSMUnmapOp.class).setPath(path).call().getId();

        Console console = cli.getConsole();
        if (newTreeId.equals(oldTreeId)) {
            console.println("No differences were found after unmapping.\n"
                    + "No changes have been made to the working tree");
        } else {
            // print something?
        }
    }

}
