/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.cli.porcelain;

import com.beust.jcommander.Parameter;

/**
 * Common arguments for SpatiaLite porcelain commands.
 */
public class SLCommonArgs {

    /**
     * The database to connect to. Default: database
     */
    @Parameter(names = "--database", description = "The database to connect to.  Default: database.sqlite")
    public String database = "database.sqlite";

    /**
     * User name. Default: user
     */
    @Parameter(names = "--user", description = "User name.  Default: user")
    public String username = "user";

}
