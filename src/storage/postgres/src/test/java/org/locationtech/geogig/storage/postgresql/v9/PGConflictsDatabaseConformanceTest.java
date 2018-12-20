/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import java.net.URI;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.ClassRule;
import org.junit.Rule;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.impl.ConflictsDatabaseConformanceTest;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

public class PGConflictsDatabaseConformanceTest
        extends ConflictsDatabaseConformanceTest<PGConflictsDatabase> {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    @Override
    protected PGConflictsDatabase createConflictsDatabase() throws Exception {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);

        String repoURL = testConfig.getRepoURL();
        Hints hints = new Hints().uri(URI.create(repoURL));
        PGConflictsDatabase conflicts = new PGConflictsDatabase(hints);
        conflicts.open();
        return conflicts;
    }

    @Override
    protected void dispose(@Nullable PGConflictsDatabase conflicts) throws Exception {
        if (conflicts != null) {
            PGStorage.closeDataSource(conflicts.dataSource);
        }
    }
}
