/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import com.beust.jcommander.Parameter;

/**
 * Common arguments for Geopackage porcelain commands.
 */
public class GeopkgCommonArgs {

    /**
     * The database to connect to. Default: database
     */
    @Parameter(names = { "--database", "-d" }, description = "The database to connect to.  Default: database.geopkg")
    public String database = "database.geopkg";

    /**
     * User name. Default: user
     */
    @Parameter(names = {"--user", "-u"}, description = "User name.  Default: user")
    public String username = "user";

}
