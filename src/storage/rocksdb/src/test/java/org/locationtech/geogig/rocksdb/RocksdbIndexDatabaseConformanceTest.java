/* Copyright (c) 2017 Boundless and others.
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.impl.IndexDatabaseConformanceTest;

public class RocksdbIndexDatabaseConformanceTest extends IndexDatabaseConformanceTest {

    public @Rule TemporaryFolder folder = new TemporaryFolder();

    private File dbdir;

    public @Before @Override void setUp() throws Exception {
        this.dbdir = folder.newFolder(".geogig");
        super.setUp();
    }

    @Override
    protected IndexDatabase createIndexDatabase(boolean readOnly) {
        return new RocksdbIndexDatabase(dbdir, readOnly);
    }
}
