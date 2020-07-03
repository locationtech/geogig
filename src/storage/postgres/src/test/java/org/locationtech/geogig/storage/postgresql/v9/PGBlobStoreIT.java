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

import org.junit.ClassRule;
import org.junit.Rule;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;
import org.locationtech.geogig.storage.postgresql.config.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.config.PGTestDataSourceProvider;
import org.locationtech.geogig.transaction.TransactionBlobStore;
import org.locationtech.geogig.transaction.TransactionBlobStoreTest;

public class PGBlobStoreIT extends TransactionBlobStoreTest {

    public static @ClassRule PGTestDataSourceProvider ds = new PGTestDataSourceProvider();

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName(), ds);

    protected @Override TransactionBlobStore createBlobStore(File currentDirectory) {
        Environment env = testConfig.getEnvironment();
        PGStorage.createNewRepo(env);
        PGBlobStore pgBlobStore = new PGBlobStore(env);
        return pgBlobStore;
    }

}
