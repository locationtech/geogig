/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.porcelain.RemoveOp;
import org.locationtech.geogig.repository.DiffObjectCount;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 *
 */
@Parameters(commandNames = "rm", commandDescription = "Remove features or trees")
public class Remove extends AbstractCommand implements CLICommand {

    /**
     * True if the remove operation should delete the contents of a path in case it resolves to a
     * tree, and tree itself. If a path resolving to a tree is used and this flag is set to false,
     * the path will not be deleted, nor its contents
     */
    @Parameter(names = { "-r",
            "--recursive" }, description = "Recursively remove trees, including the tree nodes themselves")
    private boolean recursive;

    @Parameter(names = { "-t", "--truncate" }, description = "Truncate trees, leaving them empty")
    private boolean truncate;

    @Parameter(description = "<path_to_remove>  [<path_to_remove>]...")
    private List<String> pathsToRemove = new ArrayList<String>();

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {

        Console console = cli.getConsole();

        // check that there is something to remove
        if (pathsToRemove.isEmpty()) {
            printUsage(cli);
            throw new CommandFailedException();
        }

        /* Perform the remove operation */
        RemoveOp op = cli.getGeogig().command(RemoveOp.class).setRecursive(recursive)
                .setTruncate(truncate);

        for (String pathToRemove : pathsToRemove) {
            op.addPathToRemove(pathToRemove);
        }

        DiffObjectCount result;
        try {
            result = op.setProgressListener(cli.getProgressListener()).call();
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException(e.getMessage());
        }
        /* And inform about it */
        console.println(String.format("Deleted %,d feature(s)", result.getFeaturesRemoved()));
        if (result.getTreesRemoved() > 0) {
            console.println(String.format("Deleted %,d trees", result.getTreesRemoved()));
        }
        if (result.getFeaturesRemoved() == 0 && result.getTreesRemoved() == 0) {
            throw new CommandFailedException();
        }
    }

}
