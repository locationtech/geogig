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

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.impl.ObjectStoreConformanceTest;

public class RocksdbObjectStoreConformanceTest extends ObjectStoreConformanceTest {

    @Override
    protected RocksdbObjectStore createOpen(Platform platform, Hints hints) {
        RocksdbObjectStore database = new RocksdbObjectStore(platform, hints);
        database.open();
        return database;
    }
}
