/* Copyright (c) 2012-2014 Boundless and others.
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

import jline.console.ConsoleReader;

import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.plumbing.DiffCount;
import org.locationtech.geogig.api.plumbing.diff.DiffObjectCount;
import org.locationtech.geogig.api.porcelain.TransferSummary;
import org.locationtech.geogig.api.porcelain.PullOp;
import org.locationtech.geogig.api.porcelain.PullResult;
import org.locationtech.geogig.api.porcelain.SynchronizationException;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.RemotesReadOnly;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Objects;

/**
 * Incorporates changes from a remote repository into the current branch.
 * <p>
 * More precisely, {@code geogig pull} runs {@code geogig fetch} with the given parameters and calls
 * {@code geogig merge} to merge the retrieved branch heads into the current branch. With
 * {@code --rebase}, it runs {@code geogig rebase} instead of {@code geogig merge}.
 * <p>
 * CLI proxy for {@link PullOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig pull [options] [<repository> [<refspec>...]]}
 * </ul>
 * 
 * @see PullOp
 */
@RemotesReadOnly
@Parameters(commandNames = "pull", commandDescription = "Fetch from and merge with another repository or a local branch")
public class Pull extends AbstractCommand implements CLICommand {

    @Parameter(names = "--all", description = "Fetch all remotes.")
    private boolean all = false;

    @Parameter(names = "--rebase", description = "Rebase the current branch on top of the upstream branch after fetching.")
    private boolean rebase = false;

    @Parameter(names = { "--depth" }, description = "Depth of the pull.")
    private int depth = 0;

    @Parameter(names = { "--fulldepth" }, description = "Pull the full history from the repository.")
    private boolean fulldepth = false;

    @Parameter(description = "[<repository> [<refspec>...]]")
    private List<String> args;

    /**
     * Executes the pull command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(depth > 0 ? !fulldepth : true,
                "Cannot specify a depth and full depth.  Use --depth <depth> or --fulldepth.");

        GeoGIG geogig = cli.getGeogig();
        if (depth > 0 || fulldepth) {
            if (!geogig.getRepository().getDepth().isPresent()) {
                throw new CommandFailedException(
                        "Depth operations can only be used on a shallow clone.");
            }
        }

        PullOp pull = geogig.command(PullOp.class);
        pull.setProgressListener(cli.getProgressListener());
        pull.setAll(all).setRebase(rebase).setFullDepth(fulldepth);
        pull.setDepth(depth);

        if (args != null) {
            if (args.size() > 0) {
                pull.setRemote(args.get(0));
            }
            for (int i = 1; i < args.size(); i++) {
                pull.addRefSpec(args.get(i));
            }
        }

        try {
            final PullResult result = pull.call();

            ConsoleReader console = cli.getConsole();
            TransferSummary fetchResult = result.getFetchResult();
            FetchResultPrinter.print(fetchResult, console);

            final Ref oldRef = result.getOldRef();
            final Ref newRef = result.getNewRef();

            if (oldRef == null && newRef == null) {
                console.println("Nothing to pull.");
            } else if (Objects.equal(oldRef, newRef)) {
                String name = oldRef == null ? newRef.getName() : oldRef.getName();
                name = Ref.localName(name);
                console.println(name + " already up to date.");
            } else {
                String oldTreeish;
                String newTreeish = newRef.getObjectId().toString();
                if (oldRef == null) {
                    console.println("From " + result.getRemoteName());
                    console.println(" * [new branch]     " + newRef.localName() + " -> "
                            + newRef.getName());

                    oldTreeish = ObjectId.NULL.toString();
                } else {
                    oldTreeish = oldRef.getObjectId().toString();
                }

                DiffObjectCount count = geogig.command(DiffCount.class).setOldVersion(oldTreeish)
                        .setNewVersion(newTreeish).call();
                long added = count.getFeaturesAdded();
                long removed = count.getFeaturesRemoved();
                long modified = count.getFeaturesChanged();
                console.println(String.format("Features Added: %,d Removed: %,d Modified: %,d",
                        added, removed, modified));
            }
        } catch (SynchronizationException e) {
            switch (e.statusCode) {
            case HISTORY_TOO_SHALLOW:
            default:
                throw new CommandFailedException("Unable to pull, the remote history is shallow.",
                        e);
            }
        }

    }
}
