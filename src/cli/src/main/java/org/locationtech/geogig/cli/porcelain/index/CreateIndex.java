/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain.index;

import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.Repository;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@RequiresRepository(true)
@Parameters(commandNames = {
        "create-index" }, commandDescription = "Creates a spatial index for the specified feature tree")
public class CreateIndex extends AbstractCommand implements CLICommand {

    @Parameter(names = "--tree", required = true, description = "Name or path of the feature tree to create the index for.")
    private String treeRefSpec;

    @Parameter(names = { "-a",
            "--attribute" }, required = false, description = "Attribute to create the index for.")
    private String attribute;

    @Parameter(names = "--indexHistory", description = "If specified, indexes will be created for all commits in the history.")
    private boolean indexHistory = false;

    @Parameter(names = { "-e",
            "--extra-attributes" }, description = "Comma separated list of extra attribute names to hold inside index")
    private List<String> extraAttributes;

    @Override
    protected void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {

        Repository repo = cli.getGeogig().getRepository();

        Index index = repo.command(CreateQuadTree.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setGeometryAttributeName(attribute)//
                .setExtraAttributes(extraAttributes)//
                .setIndexHistory(indexHistory)//
                .setProgressListener(cli.getProgressListener())//
                .call();

        cli.getConsole().println(
                "Index created successfully: " + index.indexTreeId().toString().substring(0, 8));
    }
}
