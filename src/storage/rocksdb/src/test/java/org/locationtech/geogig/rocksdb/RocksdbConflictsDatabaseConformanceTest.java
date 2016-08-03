/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.storage.ConflictsDatabaseConformanceTest;

public class RocksdbConflictsDatabaseConformanceTest
        extends ConflictsDatabaseConformanceTest<RocksdbConflictsDatabase> {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Override
    protected RocksdbConflictsDatabase createConflictsDatabase() throws Exception {
        return new RocksdbConflictsDatabase(tmp.getRoot());
    }

    @Override
    protected void dispose(RocksdbConflictsDatabase conflicts) throws Exception {
        conflicts.close();
    }

}
