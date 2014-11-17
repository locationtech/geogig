/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.porcelain.RemoveOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ObjectDatabaseReadOnly;
import org.locationtech.geogig.repository.Repository;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;

/**
 *
 */
@ObjectDatabaseReadOnly
@Parameters(commandNames = "rm", commandDescription = "Remove features or trees")
public class Remove extends AbstractCommand implements CLICommand {

    /**
     * True if the remove operation should delete the contents of a path in case it resolves to a
     * tree. If a path resolving to a tree is used and this flag is set to false, the path will not
     * be deleted
     */
    @Parameter(names = { "-r" }, description = "Recursively remove tree contents")
    private boolean recursive;

    @Parameter(description = "<path_to_remove>  [<path_to_remove>]...")
    private List<String> pathsToRemove = new ArrayList<String>();

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {

        ConsoleReader console = cli.getConsole();

        // check that there is something to remove
        if (pathsToRemove.isEmpty()) {
            printUsage(cli);
            throw new CommandFailedException();
        }

        /*
         * Separate trees and features, and check that, if there are trees to remove, the -r
         * modifier is used
         */
        ArrayList<String> trees = new ArrayList<String>();
        Repository repository = cli.getGeogig().getRepository();
        for (String pathToRemove : pathsToRemove) {
            NodeRef.checkValidPath(pathToRemove);

            Optional<NodeRef> node = repository.command(FindTreeChild.class)
                    .setParent(repository.workingTree().getTree()).setIndex(true)
                    .setChildPath(pathToRemove).call();
            checkParameter(node.isPresent(), "pathspec '%s' did not match any feature or tree",
                    pathToRemove);
            NodeRef nodeRef = node.get();
            if (nodeRef.getType() == TYPE.TREE) {
                checkParameter(recursive, "Cannot remove tree %s if -r is not specified",
                        nodeRef.path());
                trees.add(pathToRemove);
            }
        }
        int featuresCount = pathsToRemove.size() - trees.size();

        /* Perform the remove operation */
        RemoveOp op = cli.getGeogig().command(RemoveOp.class);

        for (String pathToRemove : pathsToRemove) {
            op.addPathToRemove(pathToRemove);
        }

        op.setProgressListener(cli.getProgressListener()).call();

        /* And inform about it */
        if (featuresCount > 0) {
            console.print(String.format("Deleted %d feature(s)", featuresCount));
        }

        for (String tree : trees) {
            console.print(String.format("Deleted %s tree", tree));
        }

    }

}
