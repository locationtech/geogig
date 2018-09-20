/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.remoting;

import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.remotes.PushOp;
import org.locationtech.geogig.remotes.SynchronizationException;
import org.locationtech.geogig.remotes.TransferSummary;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Updates remote refs using local refs, while sending objects necessary to complete the given refs.
 * <p>
 * CLI proxy for {@link PushOp}
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig push [<options>] [<repository> [<refspec>...]]}
 * </ul>
 * 
 * @see PushOp
 */
@Parameters(commandNames = "push", commandDescription = "Update remote refs along with associated objects")
public class Push extends AbstractCommand implements CLICommand {

    @Parameter(names = "--all", description = "Instead of naming each ref to push, specifies that all refs under refs/heads/ be pushed.")
    private boolean all = false;

    @Parameter(description = "[<repository> [<refspec>...]]")
    private List<String> args;

    /**
     * Executes the push command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) throws IOException {

        PushOp push = cli.getGeogig().command(PushOp.class);
        push.setProgressListener(cli.getProgressListener());
        push.setAll(all);
        
        if (args != null) {
            if (args.size() > 0) {
                push.setRemote(args.get(0));
            }
            for (int i = 1; i < args.size(); i++) {
                push.addRefSpec(args.get(i));
            }
        }
        try {
            // TODO: listen on progress?
            TransferSummary dataPushed = push.call();
            if (dataPushed.isEmpty()) {
                cli.getConsole().println("Nothing to push.");
            }
        } catch (SynchronizationException e) {
            switch (e.statusCode) {
            case REMOTE_HAS_CHANGES:
                throw new CommandFailedException(
                        "Push failed: The remote repository has changes that would be lost in the event of a push.",
                        true);
            case HISTORY_TOO_SHALLOW:
                throw new CommandFailedException(
                        "Push failed: There is not enough local history to complete the push.",
                        true);
            case CANNOT_PUSH_TO_SYMBOLIC_REF:
                throw new CommandFailedException("Push failed: Cannot push to a symbolic reference",
                        true);
            default:
                break;
            }
        }
    }
}
