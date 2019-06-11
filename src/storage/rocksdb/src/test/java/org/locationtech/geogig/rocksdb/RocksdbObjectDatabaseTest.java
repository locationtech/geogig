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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.datastream.RevObjectSerializerProxy;

/**
 * Test suite for {@link RocksdbObjectDatabase} methods that are not par of {@link ObjectStore}
 * (i.e. complements {@link RocksdbObjectStoreConformanceTest})
 *
 */
public class RocksdbObjectDatabaseTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    protected RocksdbObjectDatabase db;

    private File dbdir;

    public @Before void setUp() throws Exception {
        this.dbdir = folder.newFolder(".geogig");
        db = new RocksdbObjectDatabase(dbdir, false);
        db.open();
    }

    @After
    public void closeDbs() throws IOException {
        if (db != null) {
            db.close();
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
    public void testSerializer() {
        assertTrue(db.serializer() instanceof RevObjectSerializerProxy);
        db.close();
        db.open();
        assertTrue(db.serializer() instanceof RevObjectSerializerProxy);
    }

}
