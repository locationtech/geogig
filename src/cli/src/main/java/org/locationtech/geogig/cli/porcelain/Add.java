/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.plumbing.merge.ConflictsCheckOp;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * This command updates the index using the current content found in the working tree, to prepare
 * the content staged for the next commit. It typically adds all unstaged changes, but with a
 * defined pattern, only matching features will be added.
 * <p>
 * The "index" holds a snapshot of the HEAD tree plus any staged changes and is used to determine
 * what will be committed to the repository. Thus after making any changes to the working tree, and
 * before running the commit command, you must use the add command to add any new or modified files
 * to the index.
 * <p>
 * This command can be performed multiple times before a commit. It only adds the content of the
 * specified feature(s) at the time the add command is run; if you want subsequent changes included
 * in the next commit, then you must run {@code geogig add} again to add the new content to the
 * index.
 * <p>
 * CLI proxy for {@link AddOp}
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig add [-n] [<pattern>...]}
 * </ul>
 * 
 * @see AddOp
 */
@Parameters(commandNames = "add", commandDescription = "Add features to the staging area")
public class Add extends AbstractCommand implements CLICommand {

    @Parameter(names = { "--dry-run", "-n" }, description = "Maximum number of commits to log")
    private boolean dryRun;

    @Parameter(names = { "--update",
            "-u" }, description = "Only add features that have already been tracked")
    private boolean updateOnly;

    @Parameter(names = { "--quiet",
            "-q" }, description = "Do not count and report changes. Useful to avoid unnecessary waits on large changesets")
    private boolean quiet;

    @Parameter(description = "<patterns>...")
    private List<String> patterns = new ArrayList<String>();

    /**
     * Executes the add command using the provided options.
     * 
     * @param cli
     * @see org.locationtech.geogig.cli.AbstractCommand#runInternal(org.locationtech.geogig.cli.GeogigCLI)
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        final GeoGIG geogig = cli.getGeogig();

        final Console console = cli.getConsole();

        String pathFilter = null;
        if (patterns.size() == 1) {
            pathFilter = patterns.get(0);
        } else if (patterns.size() > 1) {
            throw new InvalidParameterException("Only a single path is supported so far");
        }

        final boolean hasConflicts = geogig.command(ConflictsCheckOp.class).call().booleanValue();

        if (!quiet) {
            console.print("Counting unstaged elements...");
            console.flush();
            DiffObjectCount unstaged = geogig.getRepository().workingTree()
                    .countUnstaged(pathFilter);
            if (0 == unstaged.count() && !hasConflicts) {
                console.println();
                console.println("No unstaged elements, exiting.");
                return;
            } else {
                console.println(String.valueOf(unstaged.count()));
            }
        }

        console.println("Staging changes...");
        AddOp op = geogig.command(AddOp.class);
        if (patterns.size() == 1) {
            op.addPattern(patterns.get(0));
        }

        WorkingTree workTree = op.setUpdateOnly(updateOnly)
                .setProgressListener(cli.getProgressListener()).call();

        if (quiet) {
            console.println("done.");
        } else {
            DiffObjectCount staged = geogig.getRepository().index().countStaged(null);
            DiffObjectCount unstaged = workTree.countUnstaged(null);

            console.println(String.format("%,d features and %,d trees staged for commit",
                    staged.featureCount(), staged.treeCount()));

            console.println(String.format("%,d features and %,d trees not staged for commit",
                    unstaged.featureCount(), unstaged.treeCount()));
        }
    }

}
