/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.cli.test.sqlserver.functional;

import org.locationtech.geogig.test.integration.OnlineTestProperties;


public class IniSQLServerProperties extends OnlineTestProperties{

    private static final String []DEFAULTS = {//
        "database.host", "localhost",//
        "database.port", "1433",//
        "database.schema", "dbo",//
        "database.database", "database",//
        "database.user", "sa",//
        "database.password", "sa"//
    };

    public IniSQLServerProperties(){
        super(".geogig-sqlserver-tests.properties", DEFAULTS);
    }
}
