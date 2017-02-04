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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.datastream.SerializationFactoryProxy;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.impl.ObjectStoreConformanceTest;

public class RocksdbObjectStoreConformanceTest extends ObjectStoreConformanceTest {

    private Platform platform = null;

    private ConfigDatabase configDB = null;

    private RocksdbObjectDatabase database = null;

    @Override
    protected RocksdbObjectDatabase createOpen(Platform platform, Hints hints) {
        this.platform = platform;
        configDB = new IniFileConfigDatabase(platform);
        database = new RocksdbObjectDatabase(platform, hints, configDB);
        database.open();
        return database;
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
        db = closeAndCreate(db, platform, null);
        ((RocksdbObjectStore) db).checkWritable();
    }

    @Test
    public void testAccessors() {
        assertFalse(database.isReadOnly());
        ConflictsDatabase conflicts = database.getConflictsDatabase();
        assertNotNull(conflicts);
        BlobStore blobStore = database.getBlobStore();
        assertNotNull(blobStore);
    }

    @Test
    public void testConfigure() throws Exception {
        database.configure();
        assertEquals(RocksdbStorageProvider.VERSION,
                configDB.get(RocksdbStorageProvider.FORMAT_NAME + ".version").get());
        assertEquals(RocksdbStorageProvider.FORMAT_NAME,
                configDB.get("storage." + StorageType.OBJECT.key).get());

        assertTrue(database.checkConfig());

    }

    @Test
    public void testSerializer() {
        assertTrue(database.serializer() instanceof SerializationFactoryProxy);
        database.close();
        database.open();
        assertTrue(database.serializer() instanceof SerializationFactoryProxy);
    }

}
