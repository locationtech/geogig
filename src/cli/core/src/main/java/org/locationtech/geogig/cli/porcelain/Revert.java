/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.RevertConflictsException;
import org.locationtech.geogig.porcelain.RevertOp;

import com.google.common.base.Suppliers;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Given one or more existing commits, revert the changes that the related patches introduce, and
 * record some new commits that record them. This requires your working tree to be clean (no
 * modifications from the HEAD commit).
 * 
 * <p>
 * CLI Proxy for {@link RevertOp}
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig revert [--continue] [--abort] [--no-commit] <commit>...}
 * </ul>
 * 
 * @see RevertOp
 */
@Command(name = "revert", description = "Revert commits to undo the changes made")
public class Revert extends AbstractCommand implements CLICommand {

    @Parameters(arity = "0..*", description = "<commit>...")
    private List<String> commits = new ArrayList<>();

    @Option(names = "--no-commit", description = "Do not create new commit with reverted changes")
    private boolean noCommit;

    @Option(names = "--continue", description = "Continue a revert process stopped because of conflicts")
    private boolean continueRevert;

    @Option(names = "--abort", description = "Abort a revert process stopped because of conflicts")
    private boolean abort;

    /**
     * Executes the revert command.
     */
    protected @Override void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(commits.size() > 0 || abort || continueRevert,
                "nothing specified for reverting");

        final Geogig geogig = cli.getGeogig();
        RevertOp revert = geogig.command(RevertOp.class);

        for (String st : commits) {
            Optional<ObjectId> commitId = geogig.command(RevParse.class).setRefSpec(st).call();
            checkParameter(commitId.isPresent(),
                    "Couldn't resolve '" + st + "' to a commit, aborting revert.");
            revert.addCommit(Suppliers.ofInstance(commitId.get()));
        }
        try {
            revert.setCreateCommit(!noCommit).setAbort(abort).setContinue(continueRevert).call();
        } catch (RevertConflictsException e) {
            StringBuilder sb = new StringBuilder();
            sb.append(e.getMessage() + "\n");
            sb.append(
                    "When you have fixed these conflicts, run 'geogig revert --continue' to continue the revert operation.\n");
            sb.append("To abort the revert operation, run 'geogig revert --abort'\n");
            throw new CommandFailedException(sb.toString(), true);
        }

        if (abort) {
            cli.getConsole().println("Revert aborted successfully.");
        }

    }
}
