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

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditImport;
import org.locationtech.geogig.geotools.geopkg.GeopkgImportResult;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;

import com.google.common.annotations.VisibleForTesting;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Pulls changes from a geopackage audit log into the current branch
 *
 */
@RequiresRepository(true)
@Command(name = "pull", description = "Import changes from a Geopackage audit log created with geopkg push")
public class GeopkgPull extends AbstractCommand {

    public @ParentCommand GeopkgCommandProxy commonArgs;

    @Option(names = { "-t",
            "--table" }, description = "Feature table to import.  Required if tables are from multiple commits.")
    String table = null;

    @VisibleForTesting
    @Option(names = { "-m", "--message" }, description = "Commit message to ")
    String commitMessage;

    final GeopkgSupport support = new GeopkgSupport();

    protected @Override void runInternal(GeogigCLI cli)
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
            cli.getConsole()
                    .println("Changes committed and merge at " + result.getNewCommit().getId());

            result.close();

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CommandFailedException(e.getMessage(), e);
        }

    }

}
