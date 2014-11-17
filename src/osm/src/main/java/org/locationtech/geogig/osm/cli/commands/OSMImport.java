/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.osm.cli.commands;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.osm.internal.EmptyOSMDownloadException;
import org.locationtech.geogig.osm.internal.Mapping;
import org.locationtech.geogig.osm.internal.OSMImportOp;
import org.locationtech.geogig.osm.internal.OSMReport;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * Imports data from an OSM file
 */
@Parameters(commandNames = "import", commandDescription = "Import OpenStreetMap data from a file")
public class OSMImport extends AbstractCommand implements CLICommand {

    @Parameter(arity = 1, description = "OSM file path", required = true)
    public List<String> apiUrl = Lists.newArrayList();

    @Parameter(names = { "--add" }, description = "Do not remove previous data before importing")
    public boolean add = false;

    @Parameter(names = { "--no-raw" }, description = "Do not import raw data when using a mapping")
    public boolean noRaw = false;

    @Parameter(names = { "--mapping" }, description = "The file that contains the data mapping to use")
    public String mappingFile;

    @Parameter(names = "--message", description = "Message for the commit to create.")
    public String message;

    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(apiUrl != null && apiUrl.size() == 1, "One file must be specified");
        File importFile = new File(apiUrl.get(0));
        checkParameter(importFile.exists(), "The specified OSM data file does not exist");
        checkParameter(!(message != null && noRaw), "cannot use --message if using --no-raw");
        checkParameter(message == null || mappingFile != null,
                "Cannot use --message if not using --mapping");

        Mapping mapping = null;
        if (mappingFile != null) {
            mapping = Mapping.fromFile(mappingFile);
        }

        try {
            message = message == null ? "Updated OSM data" : message;
            Optional<OSMReport> report = cli.getGeogig().command(OSMImportOp.class)
                    .setDataSource(importFile.getAbsolutePath()).setMapping(mapping)
                    .setMessage(message).setNoRaw(noRaw).setAdd(add)
                    .setProgressListener(cli.getProgressListener()).call();
            if (report.isPresent()) {
                OSMReport rep = report.get();
                String msg;
                if (rep.getUnpprocessedCount() > 0) {
                    msg = String
                            .format("\nSome elements returned by the specified filter could not be processed.\n"
                                    + "Processed entities: %,d.\nWrong or uncomplete elements: %,d.\nNodes: %,d.\nWays: %,d.\n",
                                    rep.getCount(), rep.getUnpprocessedCount(), rep.getNodeCount(),
                                    rep.getWayCount());
                } else {
                    msg = String.format("\nProcessed entities: %,d.\n Nodes: %,d.\n Ways: %,d\n",
                            rep.getCount(), rep.getNodeCount(), rep.getWayCount());
                }
                cli.getConsole().println(msg);
            }

        } catch (EmptyOSMDownloadException e) {
            throw new IllegalArgumentException(
                    "The specified filter did not contain any valid element.\n"
                            + "No changes were made to the repository.\n");
        } catch (RuntimeException e) {
            throw new CommandFailedException("Error importing OSM data: " + e.getMessage(), e);
        }

    }

}
