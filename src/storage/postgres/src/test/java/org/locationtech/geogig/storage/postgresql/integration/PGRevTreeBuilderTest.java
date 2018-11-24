/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.integration;

import java.io.IOException;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilderTest;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.v9.PGConfigDatabase;
import org.locationtech.geogig.storage.postgresql.v9.PGObjectDatabase;

public class PGRevTreeBuilderTest extends CanonicalTreeBuilderTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    ConfigDatabase configDb;

    PGObjectDatabase pgObjectDatabase;

    @After
    public void dispose() throws IOException {
        if (configDb != null) {
            configDb.close();
        }
        if (pgObjectDatabase != null) {
            pgObjectDatabase.close();
        }
    }

    @Override
    protected ObjectStore createObjectStore() {
        Environment environment = testConfig.getEnvironment();
        PGStorage.createNewRepo(environment);

        configDb = new PGConfigDatabase(environment);
        pgObjectDatabase = new PGObjectDatabase(configDb, environment, false);
        return pgObjectDatabase;
    }
}
