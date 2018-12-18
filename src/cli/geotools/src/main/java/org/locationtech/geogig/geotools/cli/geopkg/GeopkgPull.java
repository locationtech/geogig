/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import java.io.File;
import java.io.IOException;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditImport;
import org.locationtech.geogig.geotools.geopkg.GeopkgImportResult;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Pulls changes from a geopackage audit log into the current branch
 *
 */
@RequiresRepository(true)
@Parameters(commandNames = "pull", commandDescription = "Import changes from a Geopackage audit log created with geopkg push")
public class GeopkgPull extends AbstractCommand {

    /**
     * Common arguments for Geopackage commands.
     */
    @ParametersDelegate
    final GeopkgCommonArgs commonArgs = new GeopkgCommonArgs();

    @Parameter(names = { "-t",
            "--table" }, description = "Feature table to import.  Required if tables are from multiple commits.")
    String table = null;

    @VisibleForTesting
    @Parameter(names = { "-m", "--message" }, description = "Commit message to ")
    String commitMessage;

    final GeopkgSupport support = new GeopkgSupport();

    @Override
    protected void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {
        Repository repository = cli.getGeogig().getRepository();
        File databaseFile = new File(commonArgs.database);
        Preconditions.checkArgument(databaseFile.exists(), "Database file not found.");

        ProgressListener listener = cli.getProgressListener();
        try {
            GeopkgImportResult result = repository.command(GeopkgAuditImport.class)
                    .setDatabase(databaseFile).setCommitMessage(commitMessage).setTable(table)
                    .setProgressListener(listener).call();

            cli.getConsole().println("Import successful.");
            cli.getConsole().println("Changes committed and merge at " + result.getNewCommit().getId());

            result.close();

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CommandFailedException(e.getMessage(), e);
        }

    }

}
