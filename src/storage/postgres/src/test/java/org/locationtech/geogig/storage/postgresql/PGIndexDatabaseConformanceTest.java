/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import java.io.IOException;

import org.junit.After;
import org.junit.Rule;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.impl.IndexDatabaseConformanceTest;

import com.google.common.base.Throwables;

public class PGIndexDatabaseConformanceTest extends IndexDatabaseConformanceTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    ConfigDatabase configdb;

    @Override
    protected IndexDatabase createIndexDatabase(Platform platform, Hints hints) {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);

        closeConfigDb();

        configdb = new PGConfigDatabase(config);
        boolean readOnly = hints == null ? false : hints.getBoolean(Hints.OBJECTS_READ_ONLY);
        PGIndexDatabase db = new PGIndexDatabase(configdb, config, readOnly);
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
