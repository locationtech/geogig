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

import java.io.IOException;

import org.junit.After;
import org.junit.Rule;
import org.locationtech.geogig.api.Platform;
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
}
