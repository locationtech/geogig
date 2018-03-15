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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

class DBHandle {

    final org.rocksdb.DBOptions options;

    private final RocksDB db;

    final DBConfig config;

    private volatile boolean closed;

    private @Nullable ColumnFamilyHandle metadata;

    /**
     * Keeps track of how many references are currently out for the database. When an function needs
     * to use the database, it will get a reference and then close it when it is finished.
     */
    private AtomicInteger references = new AtomicInteger();

    private Map<String, ColumnFamilyHandle> extraColumns;

    /**
     * A reference to the RocksDB instance. This needs to be closed after it's used to free up the
     * reference.
     */
    class RocksDBReference implements AutoCloseable {

        RocksDBReference() {
            references.incrementAndGet();
        }

        @Override
        public void close() {
            references.decrementAndGet();
        }

        /**
         * @return the {@link RocksDB} instance associated with the handle.
         */
        public RocksDB db() {
            return db;
        }

    }

    public DBHandle(final DBConfig config, final org.rocksdb.DBOptions options, final RocksDB db,
            @Nullable ColumnFamilyHandle metadata, Map<String, ColumnFamilyHandle> extraColumns) {
        this.config = config;
        this.options = options;
        this.db = db;
        this.metadata = metadata;
        this.extraColumns = extraColumns;
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        while (references.get() != 0) {
            // Wait for references to be closed.
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }
        close(metadata);
        extraColumns.values().forEach((c) -> close(c));
        close(options);
        close(db);
    }

    /**
     * Gets a reference to the database for this handle. This reference must be closed when the
     * calling function is finished with it.
     * 
     * @return the reference to the database
     */
    public RocksDBReference getReference() {
        Preconditions.checkState(!closed, "db is closed");
        return new RocksDBReference();
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
        try (RocksDBReference dbRef = getReference()) {
            dbRef.db().put(metadata, k, v);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<String> getMetadata(final String key) {
        Preconditions.checkNotNull(key);
        String value = null;
        if (metadata != null) {
            try (RocksDBReference dbRef = getReference()) {
                byte[] val = dbRef.db().get(metadata, key.getBytes(Charsets.UTF_8));
                if (val != null) {
                    value = new String(val, Charsets.UTF_8);
                }
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
            }
        }
        return Optional.fromNullable(value);
    }

    public @Nullable ColumnFamilyHandle getColumnFamily(final String columnFamilyName) {
        return extraColumns.get(columnFamilyName);
    }

}
