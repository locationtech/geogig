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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.locationtech.geogig.storage.BlobStore;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;

class RocksdbBlobStore implements BlobStore {

    private final File dbdir;

    private final boolean readOnly;

    private DBHandle dbhandle;

    RocksdbBlobStore(final File dbdir, final boolean readOnly) {
        this.dbdir = dbdir;
        this.readOnly = readOnly;
    }

    public synchronized void close() {
        if (dbhandle == null) {
            return;
        }
        RocksConnectionManager.INSTANCE.release(dbhandle);
        this.dbhandle = null;
    }

    private RocksDB db() {
        if (dbhandle == null) {
            synchronized (RocksdbBlobStore.class) {
                if (dbhandle == null) {
                    String dbpath = dbdir.getAbsolutePath();
                    boolean readOnly = this.readOnly;
                    DBOptions address = new DBOptions(dbpath, readOnly);
                    this.dbhandle = RocksConnectionManager.INSTANCE.acquire(address);
                }
            }
        }
        return dbhandle.db;
    }

    private byte[] key(String path) {
        return path.getBytes(Charsets.UTF_8);
    }

    @Override
    public Optional<byte[]> getBlob(String path) {
        byte[] bytes;
        try {
            bytes = db().get(key(path));
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
        return Optional.fromNullable(bytes);
    }

    @Override
    public Optional<InputStream> getBlobAsStream(String path) {
        Optional<byte[]> blob = getBlob(path);
        return blob.transform((b) -> new ByteArrayInputStream(b));
    }

    @Override
    public void putBlob(String path, byte[] blob) {
        try {
            db().put(key(path), blob);
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void putBlob(String path, InputStream blob) {
        try {
            putBlob(path, ByteStreams.toByteArray(blob));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void removeBlob(String path) {
        try {
            db().remove(key(path));
        } catch (RocksDBException e) {
            throw Throwables.propagate(e);
        }
    }
}
