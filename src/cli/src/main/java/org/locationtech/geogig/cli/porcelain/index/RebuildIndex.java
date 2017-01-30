/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain.index;

import java.io.IOException;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.porcelain.index.BuildFullHistoryIndexOp;
import org.locationtech.geogig.repository.Repository;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@RequiresRepository(true)
@Parameters(commandNames = {
        "rebuild" }, commandDescription = "Rebuild the index trees of an index for the whole history of the repository.")
public class RebuildIndex extends AbstractCommand implements CLICommand {

    @Parameter(names = "--tree", required = true, description = "Name or path of the feature tree to rebuild the index for.")
    private String treeRefSpec;

    @Parameter(names = { "-a",
            "--attribute" }, description = "Attribute to rebuild the index for.")
    private String attribute;

    @Override
    protected void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {

        Repository repo = cli.getGeogig().getRepository();
        int treesRebuilt = repo.command(BuildFullHistoryIndexOp.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setAttributeName(attribute)//
                .setProgressListener(cli.getProgressListener())//
                .call();

        cli.getConsole().println(treesRebuilt + " trees were rebuilt.");

    }
}
