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

import org.junit.ClassRule;
import org.junit.Rule;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.impl.GraphDatabaseTest;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.v9.PGGraphDatabase;

public class PGGraphDatabaseTest extends GraphDatabaseTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    @Override
    protected GraphDatabase createDatabase(Platform platform) throws Exception {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);
        return new PGGraphDatabase(config);
    }

}
