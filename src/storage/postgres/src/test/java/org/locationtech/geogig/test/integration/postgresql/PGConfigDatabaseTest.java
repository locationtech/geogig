/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration.postgresql;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.storage.ConfigDatabaseTest;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.postgresql.Environment;
import org.locationtech.geogig.storage.postgresql.PGConfigDatabase;
import org.locationtech.geogig.storage.postgresql.PGStorage;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestProperties;

public class PGConfigDatabaseTest extends ConfigDatabaseTest<PGConfigDatabase> {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    @Override
    protected PGConfigDatabase createDatabase(Platform platform) {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);
        return new PGConfigDatabase(config);
    }

    @Override
    protected void destroy(PGConfigDatabase config) {
        config.close();
    }

    @Override
    @Test
    public void testNoRepository() {

        PGTestProperties props = new PGTestProperties();
        PGConfigDatabase globalOnlydb = new PGConfigDatabase(props.getConfig(null));
        try {
            exception.expect(ConfigException.class);
            globalOnlydb.put("section.int", 1);
        } finally {
            globalOnlydb.close();
        }
    }

    @Override
    @Test
    @Ignore
    public void testNoUserHome() {
        // intentionally empty
    }
}
