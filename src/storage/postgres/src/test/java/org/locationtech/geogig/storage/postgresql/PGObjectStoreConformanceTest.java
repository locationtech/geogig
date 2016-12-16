/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.impl.ObjectStoreConformanceTest;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

public class PGObjectStoreConformanceTest extends ObjectStoreConformanceTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    ConfigDatabase configdb;

    @Override
    protected ObjectStore createOpen(Platform platform, Hints hints) {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);

        closeConfigDb();

        configdb = new PGConfigDatabase(config);
        boolean readOnly = hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
        PGObjectDatabase db = new PGObjectDatabase(configdb, config, readOnly);
        db.open();
        return db;
    }

    @After
    public void closeConfigDb() {
        if (configdb != null) {
            try {
                configdb.close();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            configdb = null;
        }
    }

    /**
     * This tests the concurrency within the PGObjectDatabase by attempting to add the same object
     * many times with a small batch size. This causes the same object to be added by multiple
     * threads and should bring to light any concurrency issues with putting duplicate objects.
     */
    @Test
    public void testPutAllConcurrency() {
        RevObject object = RevObjectTestSupport.feature(0, null, "some value");
        List<RevObject> objects = new LinkedList<RevObject>();
        for (int i = 0; i < 100; i++) {
            objects.add(object);
        }

        ((PGObjectDatabase) db).setPutAllBatchSize(1);
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
        db = new PGObjectDatabase(configdb, config, false);
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
