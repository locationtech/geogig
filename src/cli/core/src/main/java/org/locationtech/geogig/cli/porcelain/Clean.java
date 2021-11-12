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
import java.util.Optional;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.porcelain.CleanOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "clean", description = "Deletes untracked features from working tree")
public class Clean extends AbstractCommand {

    @Parameters(arity = "0..1", description = "<path>")
    private List<String> path = new ArrayList<String>();

    @Option(names = { "--dry-run",
            "-n" }, description = "Don't actually remove anything, just show what would be done.")
    private boolean dryRun;

    public @Override void runInternal(GeogigCLI cli) throws IOException {
        final Console console = cli.getConsole();
        final Geogig geogig = cli.getGeogig();

        String pathFilter = null;
        if (!path.isEmpty()) {
            pathFilter = path.get(0);
        }

        if (dryRun) {
            if (pathFilter != null) {
                // check that is a valid path
                Repository repository = cli.getGeogig().getRepository();
                NodeRef.checkValidPath(pathFilter);

                Optional<NodeRef> ref = repository.command(FindTreeChild.class)
                        .setParent(repository.context().workingTree().getTree())
                        .setChildPath(pathFilter).call();

                checkParameter(ref.isPresent(), "pathspec '%s' did not match any tree", pathFilter);
                checkParameter(ref.get().getType() == TYPE.TREE,
                        "pathspec '%s' did not resolve to a tree", pathFilter);
            }
            try (AutoCloseableIterator<DiffEntry> unstaged = geogig.command(DiffWorkTree.class)
                    .setFilter(pathFilter).call()) {
                while (unstaged.hasNext()) {
                    DiffEntry entry = unstaged.next();
                    if (entry.changeType() == ChangeType.ADDED) {
                        console.println("Would remove " + entry.path());
                    }
                }
            }
        } else {
            geogig.command(CleanOp.class).setPath(pathFilter).call();
            console.println("Clean operation completed succesfully.");
        }
    }

}
