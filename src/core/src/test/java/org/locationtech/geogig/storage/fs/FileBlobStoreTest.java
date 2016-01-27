/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import java.io.File;

import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.storage.TransactionBlobStore;
import org.locationtech.geogig.storage.TransactionBlobStoreTest;

public class FileBlobStoreTest extends TransactionBlobStoreTest {

    @Override
    protected TransactionBlobStore createBlobStore(File currentDirectory) {
        new File(currentDirectory, ".geogig").mkdir();// fake a repo directory
        FileBlobStore fileBlobStore = new FileBlobStore(new TestPlatform(currentDirectory));
        fileBlobStore.open();
        return fileBlobStore;
    }

}
