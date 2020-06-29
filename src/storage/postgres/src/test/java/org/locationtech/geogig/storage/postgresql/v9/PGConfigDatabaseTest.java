/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import static org.junit.Assert.assertThrows;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.impl.ConfigDatabaseTest;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.config.PGTestDataSourceProvider;

public class PGConfigDatabaseTest extends ConfigDatabaseTest<PGConfigDatabase> {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    protected @Override PGConfigDatabase createDatabase(Platform platform) {
        Environment env = testConfig.getEnvironment();
        PGStorage.createNewRepo(env);
        return new PGConfigDatabase(env);
    }

    protected @Override void destroy(PGConfigDatabase config) {
        config.close();
    }

    @Test
    public @Override void testNoRepository() {
        Environment env = testConfig.newEnvironment(null);
        PGConfigDatabase globalOnlydb = new PGConfigDatabase(env);
        try {
            assertThrows(ConfigException.class, () -> globalOnlydb.put("section.int", 1));
        } finally {
            globalOnlydb.close();
        }
    }

    /**
     * Override as a no-op, since the pg config database's global settings don't depend on the
     * {@code $HOME/.geogigconfig} file.
     */
    @Test
    @Ignore
    public @Override void testNoUserHome() {
        // intentionally empty
    }
}
