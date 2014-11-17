/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration.sqlite;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.GraphDatabaseTest;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.sqlite.XerialGraphDatabase;

public class XerialGraphDatabaseTest extends GraphDatabaseTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Override
    protected GraphDatabase createDatabase(Platform platform) throws Exception {
        ConfigDatabase configdb = new IniFileConfigDatabase(platform);
        return new XerialGraphDatabase(configdb, platform);
    }
}
