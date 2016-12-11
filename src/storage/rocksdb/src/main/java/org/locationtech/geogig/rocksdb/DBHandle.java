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

import org.eclipse.jdt.annotation.Nullable;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

class DBHandle {

    final org.rocksdb.DBOptions options;

    final RocksDB db;

    final DBConfig config;

    private volatile boolean closed;

    private @Nullable ColumnFamilyHandle metadata;

    public DBHandle(final DBConfig config, final org.rocksdb.DBOptions options, final RocksDB db,
            @Nullable ColumnFamilyHandle metadata) {
        this.config = config;
        this.options = options;
        this.db = db;
        this.metadata = metadata;
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        close(metadata);
        close(options);
        close(db);
    }

    private void close(@Nullable AutoCloseable nativeObject) {
        if (nativeObject == null) {
            return;
        }
        try {
            nativeObject.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setMetadata(String key, String value) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);
        Preconditions.checkState(!closed, "db is closed");
        Preconditions.checkState(!config.isReadOnly(), "db is read only");
        Preconditions.checkNotNull(metadata);

        byte[] k = key.getBytes(Charsets.UTF_8);
        byte[] v = value.getBytes(Charsets.UTF_8);
        try {
            db.put(metadata, k, v);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    public Optional<String> getMetadata(final String key) {
        Preconditions.checkNotNull(key);
        String value = null;
        if (metadata != null) {
            try {
                byte[] val = db.get(metadata, key.getBytes(Charsets.UTF_8));
                if (val != null) {
                    value = new String(val, Charsets.UTF_8);
                }
            } catch (RocksDBException e) {
                throw Throwables.propagate(e);
            }
        }
        return Optional.fromNullable(value);
    }
}
