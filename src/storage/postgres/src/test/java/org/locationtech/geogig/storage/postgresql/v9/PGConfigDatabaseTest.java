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

import static org.junit.Assert.assertEquals;

import java.net.URISyntaxException;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.impl.ConfigDatabaseTest;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;
import org.locationtech.geogig.storage.postgresql.PGTestProperties;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

public class PGConfigDatabaseTest extends ConfigDatabaseTest<PGConfigDatabase> {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

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
        PGConfigDatabase globalOnlydb = new PGConfigDatabase(props.newConfig(null));
        try {
            exception.expect(ConfigException.class);
            globalOnlydb.put("section.int", 1);
        } finally {
            globalOnlydb.close();
        }
    }

    /**
     * Override as a no-op, since the pg config database's global settings don't depend on the
     * {@code $HOME/.geogigconfig} file.
     */
    @Override
    @Test
    @Ignore
    public void testNoUserHome() {
        // intentionally empty
    }

    @Test
    public void testHintsConstructor() throws URISyntaxException {
        Hints hints = new Hints();
        String repoURI = testConfig.getRepoURL();
        hints.set(Hints.REPOSITORY_URL, repoURI);

        try (PGConfigDatabase db = new PGConfigDatabase(hints)) {
            db.putGlobal("testSection.testKey", "testValue");
            assertEquals("testValue", db.getGlobal("testSection.testKey").get());
        }
    }

    @SuppressWarnings("resource")
    @Test
    public void testHintsConstructorNoRepoURIProvided() throws URISyntaxException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("REPOSITORY_URL was not given");
        new PGConfigDatabase(new Hints());
    }

    @SuppressWarnings("resource")
    @Test
    public void testHintsConstructorBadURI() throws URISyntaxException {
        Hints hints = new Hints();
        String repoURI = "this is not a valid URI";
        hints.set(Hints.REPOSITORY_URL, repoURI);

        exception.expect(URISyntaxException.class);
        new PGConfigDatabase(hints);
    }
}
