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

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.impl.IndexDatabaseConformanceTest;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.config.PGTestDataSourceProvider;

public class PGIndexDatabaseConformanceTest extends IndexDatabaseConformanceTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    protected @Override IndexDatabase createIndexDatabase(boolean readOnly) throws IOException {
        Environment env = testConfig.getEnvironment(readOnly);
        PGStorage.createNewRepo(env);
        PGIndexDatabase db = new PGIndexDatabase(new PGConfigDatabase(env), env);
        return db;
    }

    public @Test void testCopyIndexesToSameDatabase() {
        Environment newRepoConfig = testConfig.getEnvironment().withRepository("targetRepo");
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
            testConfig.deleteTables(targetEnv);
            targetEnv.close();
        }
    }

    private void testCopyIndexesToSameDatabase(Environment targetRepoEnvironment) {
        assertTrue(PGStorage.createNewRepo(targetRepoEnvironment));
        PGConfigDatabase targetRepoConfigDb = new PGConfigDatabase(targetRepoEnvironment);
        try {
            PGIndexDatabase target = new PGIndexDatabase(targetRepoConfigDb, targetRepoEnvironment);
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
