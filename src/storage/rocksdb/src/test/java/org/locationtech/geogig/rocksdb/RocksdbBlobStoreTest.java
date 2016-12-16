/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import java.io.File;

import org.locationtech.geogig.storage.impl.TransactionBlobStore;
import org.locationtech.geogig.storage.impl.TransactionBlobStoreTest;

public class RocksdbBlobStoreTest extends TransactionBlobStoreTest {

    @Override
    protected TransactionBlobStore createBlobStore(File currentDirectory) {
        RocksdbBlobStore blobStore = new RocksdbBlobStore(currentDirectory, false);
        return blobStore;
    }
}
