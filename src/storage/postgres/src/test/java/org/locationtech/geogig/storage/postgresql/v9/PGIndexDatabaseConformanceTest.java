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

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.impl.IndexDatabaseConformanceTest;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

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

    public @Test void testCopyIndexesToSameDatabase() {
        Environment newRepoConfig = testConfig.getEnvironment().asRepository("targetRepo");
        testCopyIndexesToSameDatabase(newRepoConfig);
    }

    public @Test void testCopyIndexesToSameDatabaseDifferentSchema() throws SQLException {
        // instead of creating a new schema, we're using a different table prefix, which will create
        // a new set of geogig tables on the same schema, achieving the same expected result
        Environment targetEnv = testConfig.newEnvironment("targetRepo");
        assertNotEquals(testConfig.getEnvironment().getTables().getPrefix(),
                targetEnv.getTables().getPrefix());
        try {
            testCopyIndexesToSameDatabase(targetEnv);
        } finally {
            testConfig.delete(targetEnv);
        }
    }

    private void testCopyIndexesToSameDatabase(Environment newRepoConfig) {
        assertTrue(PGStorage.createNewRepo(newRepoConfig));
        PGConfigDatabase targetRepoConfigDb = new PGConfigDatabase(newRepoConfig);
        try {
            PGIndexDatabase target = new PGIndexDatabase(targetRepoConfigDb, newRepoConfig, false);
            try {
                target.open();
                super.testCopyIndexesTo(target);
            } finally {
                target.close();
            }
        } finally {
            targetRepoConfigDb.close();
        }
    }

}
