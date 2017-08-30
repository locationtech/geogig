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
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.plumbing.RevParseTest;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.postgresql.PGStorageModule;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class PGRevParseTest extends RevParseTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    @Override
    protected Context createInjector() {

        String repoUrl = testConfig.getRepoURL();

        Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_URL, repoUrl);
        return Guice.createInjector(Modules.override(new GeogigModule())
                .with(new HintsModule(hints), new PGStorageModule())).getInstance(Context.class);
    }
}
