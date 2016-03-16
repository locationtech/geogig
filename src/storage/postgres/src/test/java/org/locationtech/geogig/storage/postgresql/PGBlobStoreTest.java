/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import java.io.File;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Rule;
import org.locationtech.geogig.storage.TransactionBlobStore;
import org.locationtech.geogig.storage.TransactionBlobStoreTest;

public class PGBlobStoreTest extends TransactionBlobStoreTest {

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    private DataSource dataSource;

    @Override
    protected TransactionBlobStore createBlobStore(File currentDirectory) {
        Environment config = testConfig.getEnvironment();
        PGStorage.createNewRepo(config);

        dataSource = PGStorage.newDataSource(config);
        String blobsTable = config.getTables().blobs();
        String repositoryId = config.repositoryId;

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
