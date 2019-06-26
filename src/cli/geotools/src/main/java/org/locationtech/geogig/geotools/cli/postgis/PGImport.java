/* Copyright (c) 2013-2016 Boundless and others.
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
import org.locationtech.geogig.geotools.cli.base.DataStoreImport;
import org.locationtech.geogig.geotools.plumbing.ImportOp;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Imports one or more tables from a PostGIS database.
 * 
 * PostGIS CLI proxy for {@link ImportOp}
 * 
 * @see ImportOp
 */
@Command(name = "import", description = "Import PostGIS database")
public class PGImport extends DataStoreImport implements CLICommand {

    public @ParentCommand PGCommandProxy commonArgs;

    final PGSupport support = new PGSupport();

    protected @Override DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }

    protected @Override String getSourceDatabaseName() {
        return commonArgs.database;
    }
}
