/* Copyright (c) 2013-2016 Boundless and others.
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

import org.geotools.data.DataStore;
import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.geotools.cli.base.DataStoreDescribe;
import org.locationtech.geogig.geotools.plumbing.DescribeOp;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Describes a table from a Geopackage database.
 * 
 * Geopackage CLI proxy for {@link DescribeOp}
 * 
 * @see DescribeOp
 */
@Command(name = "describe", description = "Describe a Geopackage table")
public class GeopkgDescribe extends DataStoreDescribe implements CLICommand {

    public @ParentCommand GeopkgCommandProxy commonArgs;

    final GeopkgSupport support = new GeopkgSupport();

    protected @Override DataStore getDataStore() {
        File databaseFile = new File(commonArgs.database);
        Preconditions.checkArgument(databaseFile.exists(), "Database file not found.");
        return support.getDataStore(commonArgs);
    }

}
