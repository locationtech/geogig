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
import org.locationtech.geogig.geotools.cli.DataStoreImport;
import org.locationtech.geogig.geotools.plumbing.ImportOp;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

/**
 * Imports one or more tables from a SQL Server database.
 * 
 * SQL Server CLI proxy for {@link ImportOp}
 * 
 * @see ImportOp
 */
@Parameters(commandNames = "import", commandDescription = "Import SQL Server database")
public class SQLServerImport extends DataStoreImport implements CLICommand {

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

    @Override
    protected String getSourceDatabaseName() {
        return commonArgs.database;
    }

}