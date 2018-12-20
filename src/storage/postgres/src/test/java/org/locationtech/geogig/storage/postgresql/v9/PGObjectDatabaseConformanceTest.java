/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import java.io.IOException;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.impl.ObjectDatabaseConformanceTest;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

public class PGObjectDatabaseConformanceTest extends ObjectDatabaseConformanceTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    ConfigDatabase configdb;

    @Override
    protected PGObjectDatabase createOpen(boolean readOnly) throws IOException {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);

        closeConfigDb();

        configdb = new PGConfigDatabase(config);
        PGObjectDatabase db = new PGObjectDatabase(configdb, config, readOnly);
        db.open();
        return db;
    }

    public @After void closeConfigDb() throws IOException {
        if (configdb != null) {
            configdb.close();
            configdb = null;
        }
    }
}
