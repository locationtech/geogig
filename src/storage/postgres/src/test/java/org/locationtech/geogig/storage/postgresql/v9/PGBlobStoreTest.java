/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import java.io.File;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.locationtech.geogig.storage.impl.TransactionBlobStore;
import org.locationtech.geogig.storage.impl.TransactionBlobStoreTest;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

public class PGBlobStoreTest extends TransactionBlobStoreTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    private DataSource dataSource;

    @Override
    protected TransactionBlobStore createBlobStore(File currentDirectory) {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);

        dataSource = PGStorage.newDataSource(config);
        String blobsTable = config.getTables().blobs();
        int repositoryId = config.getRepositoryId();

        PGBlobStore pgBlobStore = new PGBlobStore(dataSource, blobsTable, repositoryId);
        return pgBlobStore;
    }

    @After
    public void after() {
        if (dataSource != null) {
            PGStorage.closeDataSource(dataSource);
        }
    }
}
