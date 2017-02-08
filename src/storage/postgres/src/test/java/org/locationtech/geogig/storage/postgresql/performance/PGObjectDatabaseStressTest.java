/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.performance;

import org.junit.Rule;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.postgresql.Environment;
import org.locationtech.geogig.storage.postgresql.PGObjectDatabase;
import org.locationtech.geogig.storage.postgresql.PGStorage;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.test.performance.AbstractObjectStoreStressTest;

public class PGObjectDatabaseStressTest extends AbstractObjectStoreStressTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    @Override
    protected ObjectDatabase createDb(Platform platform, ConfigDatabase configDb) {

        Environment config = testConfig.getEnvironment();

        PGStorage.createNewRepo(config);
        PGObjectDatabase db = new PGObjectDatabase(configDb, config, false);
        return db;
    }
}
