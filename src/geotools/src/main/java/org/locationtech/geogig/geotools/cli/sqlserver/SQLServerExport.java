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
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.geotools.cli.DataStoreExport;
import org.locationtech.geogig.geotools.plumbing.ExportOp;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

/**
 * Exports features from a feature type into a SQL Server database.
 * 
 * @see ExportOp
 */

@ReadOnly
@Parameters(commandNames = "export", commandDescription = "Export to SQL Server")
public class SQLServerExport extends DataStoreExport implements CLICommand {

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
