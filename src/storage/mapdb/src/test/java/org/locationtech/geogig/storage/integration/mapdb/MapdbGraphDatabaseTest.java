/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.integration.mapdb;

import java.io.File;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.GraphDatabaseTest;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.mapdb.MapdbGraphDatabase;

import com.google.common.base.Preconditions;

public class MapdbGraphDatabaseTest extends GraphDatabaseTest {

    @Override
    protected GraphDatabase createDatabase(Platform platform) throws Exception {
        File root = platform.pwd();
        Preconditions.checkState(new File(root, ".geogig").exists());
        ConfigDatabase configDB = new IniFileConfigDatabase(platform);
        return new MapdbGraphDatabase(configDB, platform);
    }
}
