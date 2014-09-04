/* Copyright (c) 2012-2014 Boundless and others.
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

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.plumbing.diff.DiffObjectCount;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.plumbing.merge.ConflictsReadOp;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.ObjectDatabaseReadOnly;
import org.locationtech.geogig.repository.WorkingTree;

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
 * <li> {@code geogig add [-n] [<pattern>...]}
 * </ul>
 * 
 * @see AddOp
 */
@ObjectDatabaseReadOnly
@Parameters(commandNames = "add", commandDescription = "Add features to the staging area")
public class Add extends AbstractCommand implements CLICommand {

    @Parameter(names = { "--dry-run", "-n" }, description = "Maximum number of commits to log")
    private boolean dryRun;

    @Parameter(names = { "--update", "-u" }, description = "Only add features that have already been tracked")
    private boolean updateOnly;

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

        final ConsoleReader console = cli.getConsole();

        String pathFilter = null;
        if (patterns.size() == 1) {
            pathFilter = patterns.get(0);
        } else if (patterns.size() > 1) {
            throw new InvalidParameterException("Only a single path is supported so far");
        }

        List<Conflict> conflicts = geogig.command(ConflictsReadOp.class).call();

        console.print("Counting unstaged elements...");
        DiffObjectCount unstaged = geogig.getRepository().workingTree().countUnstaged(pathFilter);
        if (0 == unstaged.count() && conflicts.isEmpty()) {
            console.println();
            console.println("No unstaged elements, exiting.");
            return;
        } else {
            console.println(String.valueOf(unstaged.count()));
        }

        console.println("Staging changes...");
        AddOp op = geogig.command(AddOp.class);
        if (patterns.size() == 1) {
            op.addPattern(patterns.get(0));
        }

        WorkingTree workTree = op.setUpdateOnly(updateOnly)
                .setProgressListener(cli.getProgressListener()).call();

        DiffObjectCount staged = geogig.getRepository().index().countStaged(null);
        unstaged = workTree.countUnstaged(null);

        console.println(staged.featureCount() + " features and " + staged.treeCount()
                + " trees staged for commit");
        console.println(unstaged.featureCount() + " features and " + unstaged.treeCount()
                + " trees not staged for commit");
    }

}
