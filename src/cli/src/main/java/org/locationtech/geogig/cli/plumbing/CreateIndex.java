package org.locationtech.geogig.cli.plumbing;

import java.io.IOException;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.plumbing.index.CreateIndexInfoOp;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@RequiresRepository(true)
@Parameters(commandNames = {
        "create-index" }, commandDescription = "Creates a spatial index for the specified feature tree")
public class CreateIndex extends AbstractCommand implements CLICommand {

    @Parameter(names = "--tree", required = true, description = "Name or path of the feature tree to create the index for.")
    private String tree;

    @Parameter(names = "--attribute", required = true, description = "Attribute to create the index for.")
    private String attribute;

    @Parameter(names = "--indexHistory", description = "If specified, indexes will be created for all commits in the history.")
    private boolean indexHistory = false;

    @Override
    protected void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {

        GeoGIG geogig = cli.getGeogig();

        geogig.command(CreateIndexInfoOp.class).setTreeName(tree)
                .setAttributeName(attribute).setIndexHistory(indexHistory)
                .setProgressListener(cli.getProgressListener()).call();

        cli.getConsole().println("Index created successfully.");

    }
}
