/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.postgis;

import com.beust.jcommander.Parameter;

/**
 * Common arguments for PostGIS porcelain commands.
 * 
 */
public class PGCommonArgs {

    /**
     * Machine name or IP address to connect to. Default: localhost
     */
    @Parameter(names = { "--host",
            "-H" }, description = "Machine name or IP address to connect to. Default: localhost")
    public String host = "localhost";

    /**
     * Port number to connect to. Default: 5432
     */
    @Parameter(names = { "--port",
            "-P" }, description = "Port number to connect to.  Default: 5432")
    public Integer port = 5432;

    /**
     * The database schema to access. Default: public
     */
    @Parameter(names = { "--schema",
            "-S" }, description = "The database schema to access.  Default: public")
    public String schema = "public";

    /**
     * The database to connect to. Default: database
     */
    @Parameter(names = { "--database",
            "-D" }, description = "The database to connect to.  Default: database")
    public String database = "database";

    /**
     * User name. Default: postgres
     */
    @Parameter(names = { "--user", "-U" }, description = "User name.  Default: postgres")
    public String username = "postgres";

    /**
     * Password. Default: <no password>
     */
    @Parameter(names = { "--password", "-W" }, description = "Password.  Default: <no password>")
    public String password = "";

}
