/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.spatialite;

import org.geotools.data.DataStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.geotools.cli.DataStoreDescribe;
import org.locationtech.geogig.geotools.plumbing.DescribeOp;

import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

/**
 * Describes a table from a SpatiaLite database.
 * 
 * SpatiaLite CLI proxy for {@link DescribeOp}
 * 
 * @see DescribeOp
 */
@ReadOnly
@Parameters(commandNames = "describe", commandDescription = "Describe a SpatiaLite table")
public class SLDescribe extends DataStoreDescribe implements CLICommand {

    /**
     * Common arguments for SpatiaLite commands.
     */
    @ParametersDelegate
    public SLCommonArgs commonArgs = new SLCommonArgs();

    SpatialiteSupport support = new SpatialiteSupport();
    
    @Override
    protected DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }
}
