/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.impl.ObjectStoreConformanceTest;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

import com.google.common.collect.Lists;

public class PGObjectStoreConformanceTest extends ObjectStoreConformanceTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    ConfigDatabase configdb;

    @Override
    protected PGObjectStore createOpen() throws IOException {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);

        closeConfigDb();

        configdb = new PGConfigDatabase(config);
        PGObjectStore db = new PGObjectStore(configdb, config, false);
        db.open();
        return db;
    }

    @After
    public void closeConfigDb() throws IOException {
        if (configdb != null) {
            configdb.close();
            configdb = null;
        }
    }

    /**
     * This tests the concurrency within the PGObjectStore by attempting to add the same object many
     * times with a small batch size. This causes the same object to be added by multiple threads
     * and should bring to light any concurrency issues with putting duplicate objects.
     */
    @Test
    public void testPutAllConcurrency() {
        RevObject object = RevObjectTestSupport.feature(0, null, "some value");
        List<RevObject> objects = new LinkedList<RevObject>();
        for (int i = 0; i < 100; i++) {
            objects.add(object);
        }

        ((PGObjectStore) db).setPutAllBatchSize(1);
        db.putAll(objects.iterator());
        assertEquals(object, db.get(object.getId()));
    }

    /**
     * Test concurrency by calling getAll within the only thread available to the object database.
     * The subquery should finish, allowing the original query to continue without deadlocking.
     */
    @Test(timeout = 30000)
    public void testGetAllConcurrency() {
        configdb.put(Environment.KEY_THREADPOOL_SIZE, 1);
        db.close();

        Environment config = testConfig.getEnvironment();
        db = new PGObjectStore(configdb, config, false);
        db.open();

        RevObject originalObject = RevObjectTestSupport.feature(0, null, "some value");

        db.put(originalObject);

        Iterator<RevObject> objects = db.getAll(Lists.newArrayList(originalObject.getId()),
                new BulkOpListener() {
                    public void found(ObjectId object, @Nullable Integer storageSizeBytes) {
                        Iterator<RevObject> subQueryObjects = db.getAll(Lists.newArrayList(object));
                        assertTrue(subQueryObjects.hasNext());
                        assertEquals(originalObject, subQueryObjects.next());
                    }
                });

        assertTrue(objects.hasNext());
        assertEquals(originalObject, objects.next());
    }
}
