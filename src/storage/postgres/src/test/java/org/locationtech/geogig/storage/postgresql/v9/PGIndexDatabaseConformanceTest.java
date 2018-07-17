/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import java.io.IOException;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.impl.IndexDatabaseConformanceTest;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.v9.PGConfigDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGIndexDatabase;

public class PGIndexDatabaseConformanceTest extends IndexDatabaseConformanceTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    ConfigDatabase configdb;

    @Override
    protected IndexDatabase createIndexDatabase(boolean readOnly) throws IOException {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);

        closeConfigDb();

        configdb = new PGConfigDatabase(config);
        PGIndexDatabase db = new PGIndexDatabase(configdb, config, readOnly);
        return db;
    }

    @After
    public void closeConfigDb() throws IOException {
        if (configdb != null) {
            configdb.close();
            configdb = null;
        }
    }
}
