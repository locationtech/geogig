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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.datastream.RevObjectSerializerProxy;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.test.TestPlatform;

/**
 * Test suite for {@link RocksdbObjectDatabase} methods that are not par of {@link ObjectStore}
 * (i.e. complements {@link RocksdbObjectStoreConformanceTest})
 *
 */
public class RocksdbObjectDatabaseTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private TestPlatform platform;

    private IniFileConfigDatabase configDB;

    private Hints hints;

    protected RocksdbObjectDatabase db;

    @Before
    public void setUp() throws IOException {
        File root = folder.getRoot();
        folder.newFolder(".geogig");
        File home = folder.newFolder("home");
        platform = new TestPlatform(root);
        platform.setUserHome(home);
        hints = new Hints();
        configDB = new IniFileConfigDatabase(platform);
        db = new RocksdbObjectDatabase(platform, hints, configDB);
        db.open();
    }

    @After
    public void closeDbs() throws IOException {
        if (db != null) {
            db.close();
        }
        if (configDB != null) {
            configDB.close();
        }
    }

    @Test
    public void testOpenTwice() {
        assertTrue(db.isOpen());
        // database already opened, try calling open again.
        db.open();
        assertTrue(db.isOpen());
    }

    @Test
    public void testNoHints() {
        db.close();
        db = new RocksdbObjectDatabase(platform, null, configDB);
        db.open();
        db.checkWritable();
    }

    @Test
    public void testAccessors() {
        assertFalse(db.isReadOnly());
        BlobStore blobStore = db.getBlobStore();
        assertTrue(blobStore instanceof RocksdbBlobStore);
    }

    @Test
    public void testConfigure() throws Exception {
        db.configure();
        assertEquals(RocksdbStorageProvider.VERSION,
                configDB.get(RocksdbStorageProvider.FORMAT_NAME + ".version").get());
        assertEquals(RocksdbStorageProvider.FORMAT_NAME,
                configDB.get("storage." + StorageType.OBJECT.key).get());

        assertTrue(db.checkConfig());

    }

    @Test
    public void testSerializer() {
        assertTrue(db.serializer() instanceof RevObjectSerializerProxy);
        db.close();
        db.open();
        assertTrue(db.serializer() instanceof RevObjectSerializerProxy);
    }

}
