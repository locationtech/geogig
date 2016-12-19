/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import java.sql.Connection;

import javax.sql.DataSource;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Rule;
import org.locationtech.geogig.storage.impl.ConflictsDatabaseConformanceTest;
import org.mockito.Answers;
import org.mockito.Mock;

public class PGConflictsDatabaseConformanceTest
        extends ConflictsDatabaseConformanceTest<PGConflictsDatabase> {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Connection mockConnection;

    @Override
    protected PGConflictsDatabase createConflictsDatabase() throws Exception {
        Environment environment = testConfig.getEnvironment();
        PGStorage.createNewRepo(environment);
        DataSource dataSource = PGStorage.newDataSource(environment);

        String conflictsTable = environment.getTables().conflicts();
        int repositoryId = environment.getRepositoryId();
        PGConflictsDatabase conflicts = new PGConflictsDatabase(dataSource, conflictsTable,
                repositoryId);
        return conflicts;
    }

    @Override
    protected void dispose(@Nullable PGConflictsDatabase conflicts) throws Exception {
        if (conflicts != null) {
            PGStorage.closeDataSource(conflicts.dataSource);
        }
    }
}
