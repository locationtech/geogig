/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.cli.commands;

import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.api.porcelain.NothingToCommitException;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.osm.internal.OSMDownloadOp;
import org.locationtech.geogig.osm.internal.OSMReport;
import org.locationtech.geogig.osm.internal.OSMUpdateOp;
import org.locationtech.geogig.osm.internal.OSMUtils;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * Imports data from OSM using the Overpass API
 * 
 * Data is filtered using an Overpass QL filter.
 * 
 * If the --update modifier is used, it updates previously imported OSM data, connecting again to
 * the Overpass API
 * 
 * It reuses the filter from the last import that can be reached from the the current branch
 * 
 * WARNING: The current update mechanism is not smart, since it downloads all data, not just the
 * elements modified/added since the last import (which is stored on the OSM log file)
 * 
 */
@Parameters(commandNames = "download", commandDescription = "Download OpenStreetMap data")
public class OSMDownload extends AbstractCommand implements CLICommand {

    @Parameter(names = { "--filter", "-f" }, description = "The filter file to use.")
    private File filterFile;

    @Parameter(arity = 1, description = "<OSM Overpass api URL. eg: http://api.openstreetmap.org/api>", required = false)
    public List<String> apiUrl = Lists.newArrayList();

    @Parameter(names = { "--bbox", "-b" }, description = "The bounding box to use as filter (S W N E).", arity = 4)
    private List<String> bbox;

    @Parameter(names = "--saveto", description = "Directory where to save the dowloaded OSM data files.")
    public File saveFile;

    @Parameter(names = "--message", description = "Message for the commit to create.")
    public String message;

    @Parameter(names = { "--keep-files", "-k" }, description = "If specified, downloaded files are kept in the --saveto folder")
    public boolean keepFiles = false;

    @Parameter(names = { "--update", "-u" }, description = "Update the OSM data currently in the geogig repository")
    public boolean update = false;

    @Parameter(names = { "--rebase" }, description = "Use rebase instead of merge when updating")
    public boolean rebase = false;

    @Parameter(names = { "--mapping" }, description = "The file that contains the data mapping to use")
    public File mappingFile;

    private String osmAPIUrl;

    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        checkParameter(filterFile != null ^ bbox != null || update,
                "You must specify a filter file or a bounding box");
        checkParameter((filterFile != null || bbox != null) ^ update,
                "Filters cannot be used when updating");
        GeoGIG geogig = cli.getGeogig();
        checkState(geogig.getRepository().index().isClean()
                && geogig.getRepository().workingTree().isClean(),
                "Working tree and index are not clean");

        checkParameter(!rebase || update, "--rebase switch can only be used when updating");
        checkParameter(filterFile == null || filterFile.exists(),
                "The specified filter file does not exist");
        checkParameter(bbox == null || bbox.size() == 4,
                "The specified bounding box is not correct");

        osmAPIUrl = resolveAPIURL();

        Optional<OSMReport> report;
        GeogigTransaction tx = geogig.command(TransactionBegin.class).call();
        try {
            AbstractGeoGigOp<Optional<OSMReport>> cmd;
            if (update) {
                cmd = tx.command(OSMUpdateOp.class).setAPIUrl(osmAPIUrl).setRebase(rebase)
                        .setMessage(message).setProgressListener(cli.getProgressListener());
            } else {
                cmd = tx.command(OSMDownloadOp.class).setBbox(bbox).setFilterFile(filterFile)
                        .setKeepFiles(keepFiles).setMessage(message).setMappingFile(mappingFile)
                        .setOsmAPIUrl(osmAPIUrl).setSaveFile(saveFile)
                        .setProgressListener(cli.getProgressListener());
            }
            report = cmd.call();
            tx.commit();
        } catch (RuntimeException e) {
            tx.abort();
            if (e instanceof NothingToCommitException) {
                throw new CommandFailedException(e.getMessage(), true);
            }
            throw e;
        }
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
    }

    private String resolveAPIURL() {
        String osmAPIUrl;
        if (apiUrl.isEmpty()) {
            osmAPIUrl = OSMUtils.DEFAULT_API_ENDPOINT;
        } else {
            osmAPIUrl = apiUrl.get(0);
        }
        return osmAPIUrl;
    }

}
