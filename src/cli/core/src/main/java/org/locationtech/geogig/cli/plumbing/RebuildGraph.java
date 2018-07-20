/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.plumbing;

import java.io.IOException;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.RebuildGraphOp;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;

/**
 * Rebuilds the graph database and prints a list of commits that were incomplete or missing.
 * 
 * @see RebuildGraphOp
 */
@Parameters(commandNames = "rebuild-graph", commandDescription = "Rebuilds the graph database.")
public class RebuildGraph extends AbstractCommand implements CLICommand {

    @Parameter(names = "--quiet", description = "Print only a summary of the fixed entries.")
    private boolean quiet = false;

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        ImmutableList<ObjectId> updatedObjects = cli.getGeogig().command(RebuildGraphOp.class)
                .call();

        final Console console = cli.getConsole();
        if (updatedObjects.size() > 0) {
            if (quiet) {
                console.println(updatedObjects.size() + " graph elements (commits) were fixed.");
            } else {
                console.println("The following graph elements (commits) were incomplete or missing and have been fixed:");
                for (ObjectId object : updatedObjects) {
                    console.println(object.toString());
                }
            }
        } else {
            console.println("No missing or incomplete graph elements (commits) were found.");
        }
    }
}
