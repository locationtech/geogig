/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.sqlserver;

import org.geotools.data.DataStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.geotools.cli.DataStoreDescribe;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

@Parameters(commandNames = "describe", commandDescription = "Describe a SQL Server table")
public class SQLServerDescribe extends DataStoreDescribe implements CLICommand {

    /**
     * Common arguments for SQL Server commands.
     */
    @ParametersDelegate
    public SQLServerCommonArgs commonArgs = new SQLServerCommonArgs();

    final SQLServerSupport support = new SQLServerSupport();

    @Override
    protected DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }

}