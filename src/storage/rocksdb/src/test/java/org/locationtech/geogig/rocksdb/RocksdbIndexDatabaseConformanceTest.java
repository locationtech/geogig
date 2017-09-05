/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.impl.IndexDatabaseConformanceTest;
import org.locationtech.geogig.test.TestPlatform;

public class RocksdbIndexDatabaseConformanceTest extends IndexDatabaseConformanceTest {

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
    protected IndexDatabase createIndexDatabase(boolean readOnly) {
        Hints hints = new Hints();
        hints.set(Hints.OBJECTS_READ_ONLY, readOnly);
        ConfigDatabase configDB = new IniFileConfigDatabase(platform);
        IndexDatabase database = new RocksdbIndexDatabase(platform, hints, configDB);
        return database;
    }
}
