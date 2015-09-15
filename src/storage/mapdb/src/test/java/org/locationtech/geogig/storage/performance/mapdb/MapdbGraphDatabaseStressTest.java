/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.performance.mapdb;

import java.io.File;

import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.GraphDatabaseStressTest;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.mapdb.MapdbGraphDatabase;

import com.google.common.base.Preconditions;

public class MapdbGraphDatabaseStressTest extends GraphDatabaseStressTest {

    @Override
    protected GraphDatabase createDatabase(TestPlatform platform) {
        File root = platform.pwd();
        Preconditions.checkState(new File(root, ".geogig").exists());
        ConfigDatabase configDB = new IniFileConfigDatabase(platform);
        return new MapdbGraphDatabase(configDB, platform);
    }

}
