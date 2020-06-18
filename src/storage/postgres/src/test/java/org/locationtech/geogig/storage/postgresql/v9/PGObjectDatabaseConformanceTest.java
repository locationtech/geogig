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

import org.junit.ClassRule;
import org.junit.Rule;
import org.locationtech.geogig.storage.impl.ObjectDatabaseConformanceTest;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.config.PGTestDataSourceProvider;

public class PGObjectDatabaseConformanceTest extends ObjectDatabaseConformanceTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    protected @Override PGObjectDatabase createOpen(boolean readOnly) throws IOException {
        Environment env = testConfig.getEnvironment(readOnly);
        PGStorage.createNewRepo(env);
        PGObjectDatabase db = new PGObjectDatabase(new PGConfigDatabase(env), env);
        db.open();
        return db;
    }
}
