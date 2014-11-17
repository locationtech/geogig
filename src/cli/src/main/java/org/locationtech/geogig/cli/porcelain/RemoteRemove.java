/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.cli.porcelain;

import java.util.List;

import org.locationtech.geogig.api.porcelain.RemoteException;
import org.locationtech.geogig.api.porcelain.RemoteRemoveOp;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ObjectDatabaseReadOnly;
import org.locationtech.geogig.cli.annotation.StagingDatabaseReadOnly;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Remove the remote named <name>. All remote-tracking branches and configuration settings for the
 * remote are removed.
 * 
 * <p>
 * CLI proxy for {@link RemoteRemoveOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig rm <name>}
 * </ul>
 * 
 * @see RemoteRemoveOp
 */
@ObjectDatabaseReadOnly
@StagingDatabaseReadOnly
@Parameters(commandNames = "rm", commandDescription = "Remove a remote from the repository")
public class RemoteRemove extends AbstractCommand implements CLICommand {

    @Parameter(description = "<name>")
    private List<String> params;

    /**
     * Executes the remote remove command.
     */
    @Override
    public void runInternal(GeogigCLI cli) {
        if (params == null || params.size() != 1) {
            printUsage(cli);
            throw new CommandFailedException();
        }

        try {
            cli.getGeogig().command(RemoteRemoveOp.class).setName(params.get(0)).call();
        } catch (RemoteException e) {
            switch (e.statusCode) {
            case REMOTE_NOT_FOUND:
                throw new CommandFailedException("Could not find a remote called '" + params.get(0)
                        + "'.", e);
            default:
                throw new CommandFailedException(e.getMessage(), e);
            }
        }
    }

}
