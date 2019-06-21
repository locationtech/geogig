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

import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.ObjectDatabaseReadOnly;
import org.locationtech.geogig.plumbing.remotes.RemoteException;
import org.locationtech.geogig.plumbing.remotes.RemoteRemoveOp;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Remove the remote named <name>. All remote-tracking branches and configuration settings for the
 * remote are removed.
 * 
 * <p>
 * CLI proxy for {@link RemoteRemoveOp}
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig rm <name>}
 * </ul>
 * 
 * @see RemoteRemoveOp
 */
@ObjectDatabaseReadOnly
@Command(name = "rm", description = "Remove a remote from the repository")
public class RemoteRemove extends AbstractCommand implements CLICommand {

    @Parameters(arity = "1", description = "<name> Name of remote to remove")
    private List<String> name;

    /**
     * Executes the remote remove command.
     */
    public @Override void runInternal(GeogigCLI cli) {
        try {
            cli.getGeogig().command(RemoteRemoveOp.class).setName(name.get(0)).call();
        } catch (RemoteException e) {
            switch (e.statusCode) {
            case REMOTE_NOT_FOUND:
                throw new InvalidParameterException(
                        "Could not find a remote called '" + name.get(0) + "'.", e);
            default:
                throw new CommandFailedException(e.getMessage(), e);
            }
        }
    }

}
