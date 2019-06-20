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
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.porcelain.index.UpdateIndexOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.jts.geom.Envelope;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@RequiresRepository(true)
@Command(name = "update", aliases = "up", description = "Update the extra attributes of an index.")
public class UpdateIndex extends AbstractCommand implements CLICommand {

    @Option(names = "--tree", required = true, description = "Name or path of the feature tree to update the index for.")
    private String treeRefSpec;

    @Option(names = { "-a", "--attribute" }, description = "Attribute to update the index for.")
    private String attribute;

    @Option(names = { "-e",
            "--extra-attributes" }, description = "Comma separated list of extra attribute names to hold inside index")
    private List<String> extraAttributes;

    @Option(names = { "-o",
            "--overwrite" }, description = "Replace existing list of extra attributes held by the index")
    private boolean overwrite;

    @Option(names = {
            "--add" }, description = "Add new attributes to existing list of extra attributes held by the index")
    private boolean add;

    @Option(names = "--bounds", description = "If specified, the max bounds of the spatial index will be updated to this parameter. <minx,miny,maxx,maxy>")
    private String bbox;

    @Option(names = "--index-history", description = "If specified, indexes will be rebuilt for all commits in the history.")
    private boolean indexHistory = false;

    protected @Override void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {

        Repository repo = cli.getGeogig().getRepository();

        Envelope envelope = SpatialOps.parseNonReferencedBBOX(bbox);

        Index index;
        try {
            index = repo.command(UpdateIndexOp.class)//
                    .setTreeRefSpec(treeRefSpec)//
                    .setAttributeName(attribute)//
                    .setExtraAttributes(extraAttributes)//
                    .setOverwrite(overwrite)//
                    .setAdd(add)//
                    .setIndexHistory(indexHistory)//
                    .setBounds(envelope)//
                    .setProgressListener(cli.getProgressListener())//
                    .call();
        } catch (IllegalStateException e) {
            throw new CommandFailedException(e);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException(e.getMessage(), e);
        }

        if (cli.getProgressListener().isCanceled()) {
            cli.getConsole().println("Index update cancelled.");
        } else {
            cli.getConsole().println("Index updated successfully: "
                    + index.indexTreeId().toString().substring(0, 8));
        }

    }
}
