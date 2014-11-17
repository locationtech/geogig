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
 * Common arguments for Oracle porcelain commands.
 * 
 */
public class OracleCommonArgs {

    /**
     * Machine name or IP address to connect to. Default: localhost
     */
    @Parameter(names = "--host", description = "Machine name or IP address to connect to. Default: localhost")
    public String host = "localhost";

    /**
     * Port number to connect to. Default: 1521
     */
    @Parameter(names = "--port", description = "Port number to connect to.  Default: 1521")
    public Integer port = 1521;

    /**
     * The database schema to access. Default: public
     */
    @Parameter(names = "--schema", description = "The database schema to access.  Default: public")
    public String schema = "public";

    /**
     * The database to connect to. Default: database
     */
    @Parameter(names = "--database", description = "The database to connect to. Default: database")
    public String database = "database";

    /**
     * Parameter that enables estimated extents instead of exact ones
     */
    @Parameter(names = "--estimated_extents", description = "Use spatial index information to quickly get an estimate of the data bounds. Default: true")
    public Boolean estimatedExtent = true;

    /**
     * Flag controlling loose bbox comparisons
     */
    @Parameter(names = "--loose_bbox", description = "Perform only primary filter on bbox. Default: true")
    public Boolean looseBbox = true;

    /**
     * An alternative table where geometry metadata information can be looked up
     */
    @Parameter(names = "--geometry_metadata_table", description = "Geometry metadata table")
    public String geometryMetadataTable = "";

    /**
     * User name. Default: oracle
     */
    @Parameter(names = "--user", description = "User name.  Default: oracle")
    public String username = "oracle";

    /**
     * Password. Default: <no password>
     */
    @Parameter(names = "--password", description = "Password.  Default: <no password>")
    public String password = "";

}
