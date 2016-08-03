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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.osm.internal.CreateOSMChangesetOp;
import org.locationtech.geogig.repository.AutoCloseableIterator;
import org.locationtech.geogig.repository.GeoGIG;
import org.openstreetmap.osmosis.core.container.v0_6.ChangeContainer;
import org.openstreetmap.osmosis.xml.v0_6.XmlChangeWriter;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Lists;

/**
 * Imports data from OSM using the Overpass API
 */
@Parameters(commandNames = "create-changeset", commandDescription = "Save diff between versions as OSM changeset")
public class CreateOSMChangeset extends AbstractCommand implements CLICommand {

    @Parameter(description = "[<commit> [<commit>]]", arity = 2)
    private List<String> refSpec = Lists.newArrayList();

    @Parameter(names = "-f", description = "File to save changesets to", required = true)
    private String file;

    @Parameter(names = "--id", description = "ID to use for replacing negative changeset IDs")
    private Long id;

    /**
     * Executes the command with the specified options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {

        checkParameter(refSpec.size() < 3, "Commit list is too long :" + refSpec);

        GeoGIG geogig = cli.getGeogig();

        CreateOSMChangesetOp op = geogig.command(CreateOSMChangesetOp.class);

        String oldVersion = resolveOldVersion();
        String newVersion = resolveNewVersion();

        op.setOldVersion(oldVersion).setNewVersion(newVersion).setId(id);

        try (AutoCloseableIterator<ChangeContainer> entries = op
                .setProgressListener(cli.getProgressListener()).call()) {
            if (!entries.hasNext()) {
                cli.getConsole().println("No differences found");
                return;
            }
            BufferedWriter bufWriter = new BufferedWriter(new FileWriter(new File(file)));
            XmlChangeWriter writer = new XmlChangeWriter(bufWriter);
            while (entries.hasNext()) {
                ChangeContainer change = entries.next();
                writer.process(change);
            }
            writer.complete();
            bufWriter.flush();
        }

    }

    private String resolveOldVersion() {
        return refSpec.size() > 0 ? refSpec.get(0) : null;
    }

    private String resolveNewVersion() {
        return refSpec.size() > 1 ? refSpec.get(1) : null;
    }

}
