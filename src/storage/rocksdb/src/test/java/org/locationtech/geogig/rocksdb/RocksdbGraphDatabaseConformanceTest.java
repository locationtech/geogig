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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.impl.GraphDatabaseTest;

public class RocksdbGraphDatabaseConformanceTest extends GraphDatabaseTest {

    private ConfigDatabase configDB = null;

    @Override
    protected RocksdbGraphDatabase createDatabase(Platform platform) throws Exception {
        configDB = new IniFileConfigDatabase(platform);
        File dbdir = new File(platform.getUserHome(), "graph.rocksdb");
        boolean readOnly = false;
        RocksdbGraphDatabase db = new RocksdbGraphDatabase(configDB, dbdir, readOnly);

        return db;
    }

    @Test
    public void testConfigure() throws Exception {
        database.configure();
        assertEquals(RocksdbStorageProvider.VERSION,
                configDB.get(RocksdbStorageProvider.FORMAT_NAME + ".version").get());
        assertEquals(RocksdbStorageProvider.FORMAT_NAME,
                configDB.get("storage." + StorageType.GRAPH.key).get());

        assertTrue(database.checkConfig());

    }

}
