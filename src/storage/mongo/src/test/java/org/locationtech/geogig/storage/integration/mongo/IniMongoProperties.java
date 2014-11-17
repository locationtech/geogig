/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.integration.mongo;

import org.locationtech.geogig.test.integration.OnlineTestProperties;

public class IniMongoProperties extends OnlineTestProperties {

    public IniMongoProperties() {
        super(".geogig-mongo-tests.properties", "mongodb.uri", "mongodb://localhost:27017/",
                "mongodb.database", "geogig");
    }
}
