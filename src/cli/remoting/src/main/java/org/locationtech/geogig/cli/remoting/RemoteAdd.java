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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.plumbing.remotes.RemoteAddOp;
import org.locationtech.geogig.plumbing.remotes.RemoteException;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Adds a remote for the repository with the given name and URL.
 * <p>
 * With {@code -t <branch>} option, instead of the default global refspec for the remote to track
 * all branches under the refs/remotes/<name>/ namespace, a refspec to track only <branch> is
 * created.
 * <p>
 * CLI proxy for {@link RemoteAddOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig remote add [-t <branch>] <name> <url>}
 * </ul>
 * 
 * @see RemoteAddOp
 */
@ReadOnly
@Parameters(commandNames = "remote add", commandDescription = "Add a remote for the repository")
public class RemoteAdd extends AbstractCommand implements CLICommand {

    @Parameter(names = { "-t", "--track" }, description = "branch to track")
    private String branch = "*";

    @Parameter(names = { "-u", "--username" }, description = "user name")
    private String username = null;

    @Parameter(names = { "-p", "--password" }, description = "password")
    private String password = null;

    @Parameter(description = "<name> <url>")
    private List<String> params = new ArrayList<String>();

    /**
     * Executes the remote add command using the provided options.
     */
    @Override
    public void runInternal(GeogigCLI cli) {
        if (params == null || params.size() != 2) {
            printUsage(cli);
            throw new CommandFailedException();
        }

        try {
            cli.getGeogig().command(RemoteAddOp.class).setName(params.get(0)).setURL(params.get(1))
                    .setBranch(branch).setUserName(username).setPassword(password).call();
        } catch (RemoteException e) {
            switch (e.statusCode) {
            case REMOTE_ALREADY_EXISTS:
                throw new InvalidParameterException(
                        "Could not add, a remote called '" + params.get(0) + "' already exists.",
                        e);
            default:
                throw new CommandFailedException(e);
            }
        }

    }

}
