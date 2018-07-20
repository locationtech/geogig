/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.test.functional;

import org.locationtech.geogig.test.integration.OnlineTestProperties;

public class IniPGProperties extends OnlineTestProperties {

    private static final String[] DEFAULTS = { //
            "database.host", "localhost", //
            "database.port", "5432", //
            "database.schema", "public", //
            "database.database", "database", //
            "database.user", "postgres", //
            "database.password", "postgres"//
    };

    public IniPGProperties() {
        super(".geogig-pg-tests.properties", DEFAULTS);
    }
}
