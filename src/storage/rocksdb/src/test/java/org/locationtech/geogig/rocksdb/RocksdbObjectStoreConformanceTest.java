/* Copyright (c) 2016 Boundless and others.
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.impl.ObjectStoreConformanceTest;
import org.locationtech.geogig.test.TestPlatform;

public class RocksdbObjectStoreConformanceTest extends ObjectStoreConformanceTest {

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

    protected @Override RocksdbObjectStore createOpen() {
        Hints hints = new Hints();
        RocksdbObjectStore database = new RocksdbObjectStore(platform, hints);
        database.open();
        return database;
    }
}
