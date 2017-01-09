package org.locationtech.geogig.cli.plumbing;

import java.io.IOException;
import java.util.Iterator;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Index.IndexType;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.IndexDatabase;
import org.opengis.feature.type.GeometryType;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

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

        IndexDatabase indexDatabase = geogig.getRepository().indexDatabase();

        if (indexDatabase.getIndex(tree, attribute).isPresent()) {
            throw new CommandFailedException(
                    "An index has already been created on that tree and attribute.");
        }

        Index index = indexDatabase.createIndex(tree, attribute, IndexType.QUADTREE);

        Optional<RevFeatureType> featureTypeOpt = geogig.command(ResolveFeatureType.class)
                .setRefSpec("HEAD:" + tree).call();
        if (!featureTypeOpt.isPresent()) {
            throw new CommandFailedException(
                    String.format("Can't resolve '%s' as a feature type", tree));
        }

        RevFeatureType treeType = featureTypeOpt.get();

        boolean attributeFound = false;
        PropertyType attributeType = null;
        for (PropertyDescriptor descriptor : treeType.descriptors()) {
            if (descriptor.getName().toString().equals(attribute)) {
                attributeFound = true;
                attributeType = descriptor.getType();
            }
        }

        if (!attributeFound) {
            throw new CommandFailedException(String.format(
                    "Could not find an attribute named '%s' in the feature type.", attribute));
        }

        if (!(attributeType instanceof GeometryType)) {
            throw new UnsupportedOperationException(
                    "Only indexes on spatial attributes are currently supported.");
        }

        if (indexHistory) {
            ImmutableList<Ref> branches = geogig.command(BranchListOp.class).setLocal(true)
                    .setRemotes(true)
                    .call();

            for (Ref ref : branches) {
                Iterator<RevCommit> commits = geogig.command(LogOp.class)
                        .setUntil(ref.getObjectId()).call();
                while (commits.hasNext()) {
                    RevCommit next = commits.next();
                    createQuadTree(cli, next.getId().toString(), index);
                }
            }
        } else {
            createQuadTree(cli, "HEAD", index);
        }

    }

    private void createQuadTree(GeogigCLI cli, String commitish, Index index)
            throws IOException {
        GeoGIG geogig = cli.getGeogig();
        IndexDatabase indexDatabase = geogig.getRepository().indexDatabase();
        String treeSpec = commitish + ":" + tree;
        Optional<ObjectId> treeId = geogig.command(ResolveTreeish.class).setTreeish(treeSpec)
                .call();
        if (!treeId.isPresent()) {
            cli.getConsole()
                    .println(String.format("Feature tree not present in %s. Skipping..", treeSpec));
            return;
        }

        if (indexDatabase.resolveTreeId(index, treeId.get()).isPresent()) {
            cli.getConsole().println(String.format("Quad-tree already created for %s.", treeSpec));
            return;
        }

        CreateQuadTree command = geogig.command(CreateQuadTree.class);
        command.setFeatureTree(treeId.get());

        ProgressListener listener = cli.getProgressListener();
        RevTree quadTree = command.setProgressListener(listener).call();
        geogig.getRepository().indexDatabase().updateIndex(index, treeId.get(), quadTree.getId());

        cli.getConsole().println(String.format("Created quad-tree %s. Size: %,d", quadTree.getId(),
                quadTree.size()));
    }
}
