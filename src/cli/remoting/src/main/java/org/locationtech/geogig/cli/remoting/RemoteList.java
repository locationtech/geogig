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
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.remotes.RemoteListOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.storage.ConfigException;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Shows a list of existing remotes.
 * <p>
 * With the {@code -v} option, be a little more descriptive and show the remote URL after the name.
 * <p>
 * CLI proxy for {@link RemoteListOp}
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig remote list [-v]}
 * </ul>
 * 
 * @see RemoteListOp
 */
@ReadOnly
@Command(name = "list", description = "List all remotes for the current repository")
public class RemoteList extends AbstractCommand implements CLICommand {

    @Option(names = { "-v",
            "--verbose" }, description = "Be a little more verbose and show remote url after name.")
    boolean verbose = false;

    /**
     * Executes the remote list command.
     */
    public @Override void runInternal(GeogigCLI cli) throws IOException {

        final List<Remote> remoteList;
        try {
            remoteList = cli.getGeogig().command(RemoteListOp.class).call();
        } catch (ConfigException e) {
            throw new CommandFailedException("Could not access the config database.", e);
        }

        for (Remote remote : remoteList) {
            if (verbose) {
                cli.getConsole()
                        .println(remote.getName() + " " + remote.getFetchURL() + " (fetch)");
                cli.getConsole().println(remote.getName() + " " + remote.getPushURL() + " (push)");
            } else {
                cli.getConsole().println(remote.getName());
            }
        }

    }

}
