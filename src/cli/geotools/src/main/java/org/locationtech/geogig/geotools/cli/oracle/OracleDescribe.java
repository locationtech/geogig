/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.oracle;

import org.geotools.data.DataStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.geotools.cli.DataStoreDescribe;
import org.locationtech.geogig.geotools.plumbing.DescribeOp;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

/**
 * Describes a table from an Oracle database.
 * 
 * PostGIS CLI proxy for {@link DescribeOp}
 * 
 * @see DescribeOp
 */
@Parameters(commandNames = "describe", commandDescription = "Describe an Oracle table")
public class OracleDescribe extends DataStoreDescribe implements CLICommand {

    /**
     * Common arguments for Oracle commands.
     */
    @ParametersDelegate
    public OracleCommonArgs commonArgs = new OracleCommonArgs();

    final OracleSupport support = new OracleSupport();

    @Override
    protected DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }
}
