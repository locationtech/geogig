/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.postgis;

import org.geotools.data.DataStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.geotools.cli.DataStoreExport;
import org.locationtech.geogig.geotools.plumbing.ExportOp;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

/**
 * Exports features from a feature type into a PostGIS database.
 * 
 * @see ExportOp
 */
@ReadOnly
@Parameters(commandNames = "export", commandDescription = "Export to PostGIS")
public class PGExport extends DataStoreExport implements CLICommand {

    /**
     * Common arguments for PostGIS commands.
     */
    @ParametersDelegate
    public PGCommonArgs commonArgs = new PGCommonArgs();

    final PGSupport support = new PGSupport();

    @Override
    protected DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }
}
