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

import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.util.List;

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.osm.internal.Mapping;
import org.locationtech.geogig.osm.internal.OSMMapOp;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Creates new data in the geogig repository based on raw OSM data already in the repository.
 * 
 * 
 * @see OSMMapOp
 */
@Parameters(commandNames = "map", commandDescription = "Create new data in the repository, applying a mapping to the current OSM data")
public class OSMMap extends AbstractCommand implements CLICommand {

    @Parameter(description = "<file>")
    public List<String> args;

    @Parameter(names = { "--message", "-m" }, description = "The message for the commit to create")
    public String message;

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

        checkState(cli.getGeogig().getRepository().index().isClean()
                && cli.getGeogig().getRepository().workingTree().isClean(),
                "Working tree and index are not clean");

        String mappingFilepath = args.get(0);

        Mapping mapping = Mapping.fromFile(mappingFilepath);

        geogig = cli.getGeogig();

        ObjectId oldTreeId = geogig.getRepository().workingTree().getTree().getId();

        message = message == null ? "Applied mapping " + new File(mappingFilepath).getName()
                : message;

        ObjectId newTreeId = geogig.command(OSMMapOp.class).setMapping(mapping).setMessage(message)
                .call().getId();

        ConsoleReader console = cli.getConsole();
        if (newTreeId.equals(oldTreeId)) {
            console.println("No features matched the specified filter, or they provided no updated data.\n"
                    + "No changes have been made to the working tree");
        } else {
            // print something?
        }
    }

}
