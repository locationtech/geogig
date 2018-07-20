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
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.geotools.cli.DataStoreList;
import org.locationtech.geogig.geotools.plumbing.ListOp;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Preconditions;

/**
 * Geopackage CLI proxy for {@link ListOp}
 * 
 * @see ListOp
 */
@ReadOnly
@Parameters(commandNames = "list", commandDescription = "List available feature types in a database")
public class GeopkgList extends DataStoreList implements CLICommand {

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
