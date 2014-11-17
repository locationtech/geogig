/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.cli.porcelain;

import com.beust.jcommander.Parameter;

/**
 * Common arguments for SQL Server porcelain commands.
 * 
 */
public class SQLServerCommonArgs {

    /**
     * Machine name or IP address to connect to. Default: localhost
     */
    @Parameter(names = "--host", description = "Machine name or IP address to connect to. Default: localhost")
    public String host = "localhost";

    /**
     * Port number to connect to. Default: 5432
     */
    @Parameter(names = "--port", description = "Port number to connect to.  Default: 1433")
    public Integer port = 1433;

    /**
     * Integrated security, ignores user / password (Windows only)
     */
    @Parameter(names = "--intsec", description = "Use integrated security.  Default: false")
    public Boolean intsec = false;

    /**
     * Use native paging, improves performance for some types of queries
     */
    @Parameter(names = "--native-paging", description = "Use native paging for queries.  Default: true")
    public Boolean nativePaging = true;

    /**
     * The optional table containing geometry metadata (geometry type and srid). Can be expressed as
     * 'schema.name' or just 'name'
     */
    @Parameter(names = "--geometry_metadata_table", description = "Geometry metadata table")
    public String geometryMetadataTable = "";

    /**
     * Parameter for using WKB or Sql server binary directly. Setting to true will use WKB
     */

    @Parameter(names = "--native-serialization", description = "Use native SQL Server serialization (false), or WKB serialization (true).  Default: false")
    public Boolean nativeSerialization = false;

    /**
     * The database to connect to. Default: database
     */
    @Parameter(names = "--database", description = "The database to connect to.  Default: database")
    public String database = "database";

    /**
     * The database schema to access. Default: public
     */
    @Parameter(names = "--schema", description = "The database schema to access.  Default: public")
    public String schema = "public";

    /**
     * User name. Default: sqlserver
     */
    @Parameter(names = "--user", description = "User name.  Default: sqlserver")
    public String username = "sqlserver";

    /**
     * Password. Default: <no password>
     */
    @Parameter(names = "--password", description = "Password.  Default: <no password>")
    public String password = "";
}