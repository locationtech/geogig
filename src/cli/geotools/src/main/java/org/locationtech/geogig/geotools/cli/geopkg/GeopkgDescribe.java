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
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.geotools.cli.DataStoreDescribe;
import org.locationtech.geogig.geotools.plumbing.DescribeOp;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Preconditions;

/**
 * Describes a table from a Geopackage database.
 * 
 * Geopackage CLI proxy for {@link DescribeOp}
 * 
 * @see DescribeOp
 */
@Parameters(commandNames = "describe", commandDescription = "Describe a Geopackage table")
public class GeopkgDescribe extends DataStoreDescribe implements CLICommand {
    /**
     * Common arguments for Geopackage commands.
     */
    @ParametersDelegate
    final GeopkgCommonArgs commonArgs = new GeopkgCommonArgs();

    final GeopkgSupport support = new GeopkgSupport();

    @Override
    protected DataStore getDataStore() {
        File databaseFile = new File(commonArgs.database);
        Preconditions.checkArgument(databaseFile.exists(), "Database file not found.");
        return support.getDataStore(commonArgs);
    }

}
