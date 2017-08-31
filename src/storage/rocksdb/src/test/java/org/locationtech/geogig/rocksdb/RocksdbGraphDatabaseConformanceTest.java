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

import java.io.File;

import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.impl.GraphDatabaseTest;

public class RocksdbGraphDatabaseConformanceTest extends GraphDatabaseTest {

    @Override
    protected RocksdbGraphDatabase createDatabase(Platform platform) throws Exception {
        File dbdir = new File(platform.getUserHome(), "graph.rocksdb");
        boolean readOnly = false;
        RocksdbGraphDatabase db = new RocksdbGraphDatabase(dbdir, readOnly);
        return db;
    }

}
