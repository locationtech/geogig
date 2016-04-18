/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import java.io.File;
import java.io.IOException;

import org.geotools.data.DataStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.geotools.cli.DataStoreExport;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditExport;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.locationtech.geogig.repository.Repository;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

/**
 * Exports features from a feature type into a Geopackage database.
 * 
 * @see ExportOp
 */
@RequiresRepository(true)
@ReadOnly
@Parameters(commandNames = "export", commandDescription = "Export to Geopackage")
public class GeopkgExport extends DataStoreExport implements CLICommand {
    /**
     * Common arguments for Geopackage commands.
     */
    @ParametersDelegate
    final GeopkgCommonArgs commonArgs = new GeopkgCommonArgs();

    final GeopkgSupport support = new GeopkgSupport();

    @Parameter(names = { "-i", "--interchange" }, description = "Export as geogig mobile interchange format")
    private boolean interchangeFormat;

    @Override
    protected DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }

    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        super.runInternal(cli);

        if (interchangeFormat) {
            final String sourcePathspec = args.get(0);
            final String targetTableName = args.get(1);
            File file = new File(commonArgs.database);

            Repository repo = cli.getGeogig().getRepository();
            try {

                repo.command(GeopkgAuditExport.class).setDatabase(file)
                        .setSourcePathspec(sourcePathspec).setTargetTableName(targetTableName)
                        .setProgressListener(cli.getProgressListener()).call();

            } catch (Exception e) {
                throw new CommandFailedException("Unable to export: " + e.getMessage(), e);
            } finally {
                cli.close();
            }
        }
    }
}
