/* Copyright (c) 2015 Boundless.
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
import org.junit.Rule;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.experimental.CanonicalTreeBuilderTest;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.postgresql.Environment;
import org.locationtech.geogig.storage.postgresql.PGConfigDatabase;
import org.locationtech.geogig.storage.postgresql.PGObjectDatabase;
import org.locationtech.geogig.storage.postgresql.PGStorage;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;

public class PGRevTreeBuilderTest extends CanonicalTreeBuilderTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

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

    @Override
    protected RevTreeBuilder createBuiler() {
        return RevTreeBuilder.canonical(objectStore);
    }

    @Override
    protected RevTreeBuilder createBuiler(RevTree original) {
        return RevTreeBuilder.canonical(objectStore, original);
    }

}
