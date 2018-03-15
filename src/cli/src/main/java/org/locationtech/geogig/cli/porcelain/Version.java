/* Copyright (c) 2013-2016 Boundless and others.
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

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.porcelain.VersionInfo;
import org.locationtech.geogig.porcelain.VersionOp;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameters;

/**
 * This command displays the GeoGig version information.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code geogig [--]version}
 * </ul>
 */
@ReadOnly
@RequiresRepository(false)
@Parameters(commandNames = { "--version",
        "version" }, commandDescription = "Display GeoGig version information")
public class Version implements CLICommand {

    private Console console;

    private static int PROPERTY_NAME_WIDTH = 24;

    /**
     * Executes the version command.
     * 
     * @param cli
     * @see org.locationtech.geogig.cli.CLICommand#run(org.locationtech.geogig.cli.GeogigCLI)
     */
    public void run(GeogigCLI cli) {
        GeoGIG geogig = cli.getGeogig();
        if (geogig == null) {
            geogig = cli.newGeoGIG();
        }
        this.console = cli.getConsole();
        VersionInfo info = geogig.command(VersionOp.class).call();

        try {
            printVersionProperty("Project Version", info.getProjectVersion());
            printVersionProperty("Build Time", info.getBuildTime());
            printVersionProperty("Build User Name", info.getBuildUserName());
            printVersionProperty("Build User Email", info.getBuildUserEmail());
            printVersionProperty("Git Branch", info.getBranch());
            printVersionProperty("Git Commit ID", info.getCommitId());
            printVersionProperty("Git Commit Time", info.getCommitTime());
            printVersionProperty("Git Commit Author Name", info.getCommitUserName());
            printVersionProperty("Git Commit Author Email", info.getCommitUserEmail());
            printVersionProperty("Git Commit Message", info.getCommitMessageFull());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void printVersionProperty(String propertyName, @Nullable String propertyValue)
            throws IOException {
        console.print(String.format("%1$" + PROPERTY_NAME_WIDTH + "s : ", propertyName));
        console.print((propertyValue != null ? propertyValue : "Unspecified") + "\n");
    }
}
