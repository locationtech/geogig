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

import org.junit.Rule;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectDatabaseStressTest;
import org.locationtech.geogig.storage.postgresql.Environment;
import org.locationtech.geogig.storage.postgresql.PGObjectDatabase;
import org.locationtech.geogig.storage.postgresql.PGStorage;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;

public class PGObjectDatabaseStressTest extends ObjectDatabaseStressTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    @Override
    protected ObjectDatabase createDb(Platform platform, ConfigDatabase configDb) {

        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);
        PGObjectDatabase db = new PGObjectDatabase(configDb, config, false);
        return db;
    }

    // @Override
    // protected Iterator<RevObject> getAll(Iterable<ObjectId> ids, CountingListener getAllListener)
    // {
    // return ((PGObjectDatabase)db).getAll(ids, RevObject.TYPE.FEATURE, getAllListener);
    // }

    public static void main(String[] args) {
        PGObjectDatabaseStressTest test = new PGObjectDatabaseStressTest();
        try {
            test.tmp.create();
            test.testConfig.before();
            test.setUp();

            test.testPutAll_1M();

        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            test.tearDown();
            test.testConfig.after();
            System.exit(0);
        }
    }

}
