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
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.porcelain.CheckoutException;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CheckoutResult;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Lists;

/**
 * This command checks out a branch into the working tree. Checkout also updates HEAD to set the
 * specified branch as the current branch. This command can also be used to discard local changes if
 * used with force option.
 * <p>
 * When used with the {@code -p} option and path names are given it will update those paths in the
 * working tree from the index tree if {@code <branchOrCommitName>} isn't given otherwise it will
 * update from that tree. Note that this doesn't switch what branch you are on.
 * <p>
 * CLI proxy for {@link CheckoutOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig checkout [-f] [<branchName>]}
 * <li> {@code geogig checkout [<branchOrCommitName>] [-p <paths>...]}
 * </ul>
 * 
 * @see CheckoutOp
 */
@Parameters(commandNames = "checkout", commandDescription = "Checkout a branch or paths to the working tree")
public class Checkout extends AbstractCommand implements CLICommand {

    @Parameter(arity = 1, description = "<branch|commit>")
    private List<String> branchOrStartPoint = Lists.newArrayList();

    @Parameter(names = { "--force", "-f" }, description = "When switching branches, proceed even if the index or the "
            + "working tree differs from HEAD. This is used to throw away local changes.")
    private boolean force = false;

    @Parameter(names = { "--path", "-p" }, description = "Don't switch branches just update the named paths in the "
            + "working tree from the index tree or a named treeish object.", variableArity = true)
    private List<String> paths = Lists.newArrayList();

    @Parameter(names = "--ours", description = "When checking out paths from the index, check out 'ours' version for unmerged paths")
    private boolean ours;

    @Parameter(names = "--theirs", description = "When checking out paths from the index, check out 'theirs' version for unmerged paths")
    private boolean theirs;

    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        final GeoGIG geogig = cli.getGeogig();
        checkParameter(branchOrStartPoint.size() != 0 || !paths.isEmpty(),
                "no branch or paths specified");
        checkParameter(branchOrStartPoint.size() < 2, "too many arguments");

        try {
            final Console console = cli.getConsole();
            String branchOrCommit = (branchOrStartPoint.size() > 0 ? branchOrStartPoint.get(0)
                    : null);

            CheckoutResult result = geogig.command(CheckoutOp.class).setForce(force)
                    .setSource(branchOrCommit).addPaths(paths).setOurs(ours).setTheirs(theirs)
                    .call();

            switch (result.getResult()) {
            case CHECKOUT_LOCAL_BRANCH:
                console.println("Switched to branch '" + result.getNewRef().localName() + "'");
                break;
            case CHECKOUT_REMOTE_BRANCH:
                console.println("Branch '" + result.getNewRef().localName()
                        + "' was set up to track remote branch '" + result.getNewRef().localName()
                        + "' from " + result.getRemoteName() + ".");
                console.println("Switched to a new branch '" + result.getNewRef().localName() + "'");
                break;
            case UPDATE_OBJECTS:
                console.println("Objects in the working tree were updated to the specifed version.");
                break;
            case DETACHED_HEAD:
                console.println("You are in 'detached HEAD' state. HEAD is now at "
                        + result.getOid().toString().substring(0, 8) + "...");
                break;
            default:
                break;
            }
        } catch (CheckoutException e) {
            switch (e.statusCode) {
            case LOCAL_CHANGES_NOT_COMMITTED:
                throw new CommandFailedException(
                        "Working tree and index are not clean. To overwrite local changes, use the --force option",
                        true);
            case UNMERGED_PATHS:
                throw new CommandFailedException(e.getMessage(), true);
            }
        }
    }

}
