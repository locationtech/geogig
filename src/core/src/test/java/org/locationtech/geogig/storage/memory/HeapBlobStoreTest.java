/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import java.io.File;

import org.locationtech.geogig.transaction.TransactionBlobStore;
import org.locationtech.geogig.transaction.TransactionBlobStoreTest;

public class HeapBlobStoreTest extends TransactionBlobStoreTest {

    protected @Override TransactionBlobStore createBlobStore(File currentDirectory) {
        return new HeapBlobStore();
    }
}
