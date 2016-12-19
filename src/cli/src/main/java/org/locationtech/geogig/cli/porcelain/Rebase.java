/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.CheckoutException;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.RebaseConflictsException;
import org.locationtech.geogig.porcelain.RebaseOp;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

/**
 * Forward-port local commits to the updated upstream head.
 * <p>
 * If {@code <branch>} is specified, {@code geogig rebase} will perform an automatic
 * {@code geogig checkout <branch>} before doing anything else. Otherwise it remains on the current
 * branch.
 * <p>
 * All changes made by commits in the current branch but that are not in {@code <upstream>} are
 * saved to a temporary area.
 * <p>
 * The current branch is reset to {@code <upstream>}, or {@code <newbase>} if the {@code --onto}
 * option was supplied.
 * <p>
 * The commits that were previously saved into the temporary area are then reapplied to the current
 * branch, one by one, in order.
 * <p>
 * CLI proxy for {@link RebaseOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig rebase [--onto <newbase>] [<upstream>] [<branch>]}
 * </ul>
 * 
 * @see RebaseOp
 */
@Parameters(commandNames = { "rebase" }, commandDescription = "Forward-port local commits to the updated upstream head")
public class Rebase extends AbstractCommand implements CLICommand {

    @Parameter(names = { "--onto" }, description = "Starting point at which to create the new commits.")
    private String onto;

    @Parameter(names = { "--abort" }, description = "Abort a conflicted rebase.")
    private boolean abort;

    @Parameter(names = { "--continue" }, description = "Continue a conflicted rebase.")
    private boolean continueRebase;

    @Parameter(names = { "--skip" }, description = "Skip the current conflicting commit.")
    private boolean skip;

    @Parameter(names = { "--squash" }, description = "Squash commits instead of applying them one by one. A message has to be provided to use for the squashed commit")
    private String squash;

    @Parameter(description = "[<upstream>] [<branch>]")
    private List<String> arguments;

    /**
     * Executes the rebase command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {

        checkParameter(!(skip && continueRebase), "Cannot use both --skip and --continue");
        checkParameter(!(skip && abort), "Cannot use both --skip and --abort");
        checkParameter(!(abort && continueRebase), "Cannot use both --abort and --continue");

        GeoGIG geogig = cli.getGeogig();
        RebaseOp rebase = geogig.command(RebaseOp.class).setSkip(skip).setContinue(continueRebase)
                .setAbort(abort).setSquashMessage(squash);
        rebase.setProgressListener(cli.getProgressListener());

        if (arguments == null || arguments.size() == 0) {
            if (abort || skip || continueRebase) {
            } else {
                // Rebase onto remote branch
                throw new UnsupportedOperationException("remote branch rebase not yet supported");
            }
        } else {
            checkParameter(arguments.size() < 3, "Too many arguments specified.");
            if (arguments.size() == 2) {
                // Make sure branch is valid
                Optional<ObjectId> branchRef = geogig.command(RevParse.class)
                        .setRefSpec(arguments.get(1)).call();
                checkParameter(branchRef.isPresent(), "The branch reference could not be resolved.");
                // Checkout <branch> prior to rebase
                try {
                    geogig.command(CheckoutOp.class).setSource(arguments.get(1)).call();
                } catch (CheckoutException e) {
                    throw new CommandFailedException(e.getMessage(), true);
                }

            }

            Optional<Ref> upstreamRef = geogig.command(RefParse.class).setName(arguments.get(0))
                    .call();
            checkParameter(upstreamRef.isPresent(), "The upstream reference could not be resolved.");
            rebase.setUpstream(Suppliers.ofInstance(upstreamRef.get().getObjectId()));
        }

        if (onto != null) {
            Optional<ObjectId> ontoId = geogig.command(RevParse.class).setRefSpec(onto).call();
            checkParameter(ontoId.isPresent(), "The onto reference could not be resolved.");
            rebase.setOnto(Suppliers.ofInstance(ontoId.get()));
        }

        try {
            rebase.call();
        } catch (RebaseConflictsException e) {
            StringBuilder sb = new StringBuilder();
            sb.append(e.getMessage() + "\n");
            sb.append("When you have fixed this conflicts, run 'geogig rebase --continue' to continue rebasing.\n");
            sb.append("If you would prefer to skip this commit, instead run 'geogig rebase --skip.\n");
            sb.append("To check out the original branch and stop rebasing, run 'geogig rebase --abort'\n");
            throw new CommandFailedException(sb.toString(), true);
        }catch(IllegalStateException e){
            throw new CommandFailedException(e.getMessage(), true);
        }

        if (abort) {
            cli.getConsole().println("Rebase aborted successfully.");
        }
    }
}
