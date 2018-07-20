/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.test.functional;

import org.locationtech.geogig.test.integration.OnlineTestProperties;

public class IniOracleProperties extends OnlineTestProperties {

    private static final String[] DEFAULTS = { //
            "database.host", "192.168.1.99", //
            "database.port", "1521", //
            "database.schema", "ORACLE", //
            "database.database", "ORCL", //
            "database.user", "oracle", //
            "database.password", "oracle"//
    };

    public IniOracleProperties() {
        super(".geogig-oracle-tests.properties", DEFAULTS);
    }
}
