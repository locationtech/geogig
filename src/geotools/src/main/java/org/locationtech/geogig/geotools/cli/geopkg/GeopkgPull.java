/* Copyright (c) 2015 Boundless and others.
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
import java.util.List;

import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.geotools.geopkg.AuditReport;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditImport;
import org.locationtech.geogig.repository.Repository;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

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

    @Parameter(names = { "-m", "--message" }, description = "Commit message to ")
    private String commitMessage;

    @Parameter(names = { "-n", "--no-commit" }, description = "Do not create a commit from the audit log, just import to WORK_HEAD", arity = 0)
    private boolean noCommit = false;

    final GeopkgSupport support = new GeopkgSupport();

    @Override
    protected void runInternal(GeogigCLI cli) throws InvalidParameterException,
            CommandFailedException, IOException {

        Repository repository = cli.getGeogig().getRepository();
        final File file = new File(commonArgs.database);

        ProgressListener listener = cli.getProgressListener();

        List<AuditReport> report;
        try {
            report = repository.command(GeopkgAuditImport.class).setDatabase(file)
                    .setCommitMessage(commitMessage).setNoCommit(noCommit)
                    .setProgressListener(listener).call();

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CommandFailedException(e.getMessage(), e);
        }

    }

}
