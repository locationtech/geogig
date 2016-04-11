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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.ObjectStoreConformanceTest;

import com.google.common.base.Throwables;

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
        RevObject object = objects.feature(0, null, "some value");
        List<RevObject> objects = new LinkedList<RevObject>();
        for (int i = 0; i < 100; i++) {
            objects.add(object);
        }

        ((PGObjectDatabase) db).setPutAllBatchSize(1);
        db.putAll(objects.iterator());
        assertEquals(object, db.get(object.getId()));
    }
}
