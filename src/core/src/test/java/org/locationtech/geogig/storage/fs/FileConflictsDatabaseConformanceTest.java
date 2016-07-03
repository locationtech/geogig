/* Copyright (c) 2016 Boundless.
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

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.storage.ConflictsDatabaseConformanceTest;

public class FileConflictsDatabaseConformanceTest
        extends ConflictsDatabaseConformanceTest<FileConflictsDatabase> {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    final int batchSizeOverride = 100;

    @Override
    protected FileConflictsDatabase createConflictsDatabase() throws Exception {
        File repositoryDirectory = tmp.newFolder(".geogig");
        FileConflictsDatabase db;
        db = new FileConflictsDatabase(repositoryDirectory, batchSizeOverride);
        return db;
    }

    @Override
    protected void dispose(FileConflictsDatabase conflicts) throws Exception {
        // not needed, TemporaryFolder takes care
    }

}
