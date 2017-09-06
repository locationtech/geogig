/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.impl.ObjectDatabaseConformanceTest;
import org.locationtech.geogig.storage.memory.HeapConfigDatabase;
import org.locationtech.geogig.test.TestPlatform;

public class RocksdbObjectDatabaseConformanceTest extends ObjectDatabaseConformanceTest {

    private ConfigDatabase configdb;

    public @Rule TemporaryFolder folder = new TemporaryFolder();

    private TestPlatform platform;

    public @Before @Override void setUp() throws Exception {
        File root = folder.getRoot();
        folder.newFolder(".geogig");
        File home = folder.newFolder("home");
        platform = new TestPlatform(root);
        platform.setUserHome(home);
        super.setUp();
    }

    @Override
    protected RocksdbObjectDatabase createOpen(boolean readOnly) {
        Hints hints = new Hints();
        hints.set(Hints.OBJECTS_READ_ONLY, readOnly);
        configdb = new HeapConfigDatabase();
        RocksdbObjectDatabase database = new RocksdbObjectDatabase(platform, hints, configdb);
        database.open();
        return database;
    }

    public @After void afterTest() throws IOException {
        if (null != configdb) {
            configdb.close();
        }
    }
}
