package org.locationtech.geogig.cli.plumbing;

import java.io.IOException;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.index.CreateQuadTree;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.GeoGIG;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

@RequiresRepository(true)
@Parameters(commandNames = {
        "create-index" }, commandDescription = "Creates a spatial index for the specified feature tree")
public class CreateIndex extends AbstractCommand implements CLICommand {

    @Parameter(names = "--tree", required = true, description = "Name or path of the feature tree to create the index for")
    private String treePath;

    @Override
    protected void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {

        GeoGIG geogig = cli.getGeogig();
        CreateQuadTree command;
        command = geogig.command(CreateQuadTree.class);

        Optional<ObjectId> treeId = geogig.command(ResolveTreeish.class).setTreeish(treePath)
                .call();
        if (!treeId.isPresent()) {
            throw new CommandFailedException(
                    String.format("Can't resolve '%s' as a tree ref", treePath));
        }
        command.setFeatureTree(treeId.get());

        ProgressListener listener = cli.getProgressListener();
        RevTree quadTree = command.setProgressListener(listener).call();

        String indexRefName = "indexes/" + treeId.get();
        Optional<Ref> indexRef = geogig.command(UpdateRef.class).setName(indexRefName)
                .setNewValue(quadTree.getId()).call();
        Preconditions.checkState(indexRef.isPresent());
        cli.getConsole().println(String.format("Created quad-tree %s. Size: %,d", quadTree.getId(),
                quadTree.size()));
    }

}
