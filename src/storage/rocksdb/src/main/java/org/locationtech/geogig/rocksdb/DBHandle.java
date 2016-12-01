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

import org.rocksdb.Options;
import org.rocksdb.RocksDB;

class DBHandle {

    final Options options;

    final RocksDB db;

    final DBOptions config;

    private volatile boolean closed;

    public DBHandle(final DBOptions config, final Options options, final RocksDB db) {
        this.config = config;
        this.options = options;
        this.db = db;
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        close(db);
        close(options);
    }

    private void close(AutoCloseable nativeObject) {
        try {
            nativeObject.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
