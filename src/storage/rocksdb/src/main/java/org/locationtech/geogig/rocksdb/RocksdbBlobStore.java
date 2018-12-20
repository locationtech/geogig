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
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.locationtech.geogig.rocksdb.DBHandle.RocksDBReference;
import org.locationtech.geogig.storage.impl.TransactionBlobStore;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;

class RocksdbBlobStore implements TransactionBlobStore, Closeable {

    private final File dbdir;

    private final boolean readOnly;

    private DBHandle dbhandle;

    private boolean closed = false;

    private static final String NO_TRANSACTION = "DEFAULT";

    RocksdbBlobStore(final File dbdir, final boolean readOnly) {
        this.dbdir = dbdir;
        this.readOnly = readOnly;
    }

    public synchronized void close() {
        if (dbhandle == null) {
            return;
        }
        closed = true;
        RocksConnectionManager.INSTANCE.release(dbhandle);
        this.dbhandle = null;
    }

    private RocksDBReference db() {
        Preconditions.checkState(!closed, "db was closed");
        if (dbhandle == null) {
            synchronized (RocksdbBlobStore.class) {
                if (dbhandle == null) {
                    String dbpath = dbdir.getAbsolutePath();
                    boolean readOnly = this.readOnly;
                    DBConfig address = new DBConfig(dbpath, readOnly);
                    this.dbhandle = RocksConnectionManager.INSTANCE.acquire(address);
                }
            }
        }
        return dbhandle.getReference();
    }

    private byte[] key(String namespace, String path) {
        String key = namespace + "." + path;
        return key.getBytes(Charsets.UTF_8);
    }

    @Override
    public Optional<byte[]> getBlob(String path) {
        return getBlob(NO_TRANSACTION, path);
    }

    @Override
    public Optional<InputStream> getBlobAsStream(String path) {
        return getBlobAsStream(NO_TRANSACTION, path);
    }

    @Override
    public void putBlob(String path, byte[] blob) {
        putBlob(NO_TRANSACTION, path, blob);
    }

    @Override
    public void putBlob(String path, InputStream blob) {
        putBlob(NO_TRANSACTION, path, blob);
    }

    @Override
    public void removeBlob(String path) {
        removeBlob(NO_TRANSACTION, path);
    }

    @Override
    public Optional<byte[]> getBlob(String namespace, String path) {
        byte[] bytes;
        try (RocksDBReference dbRef = db()) {
            bytes = dbRef.db().get(key(namespace, path));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return Optional.fromNullable(bytes);
    }

    @Override
    public Optional<InputStream> getBlobAsStream(String namespace, String path) {
        Optional<byte[]> blob = getBlob(namespace, path);

        // (b) -> new ByteArrayInputStream(b)
        Function<byte[], InputStream> fn =  new Function<byte[], InputStream>() {
            @Override
            public InputStream apply(byte[] b) {
                return new ByteArrayInputStream(b);
            }};

        return blob.transform(fn);
    }

    @Override
    public void putBlob(String namespace, String path, byte[] blob) {
        try (RocksDBReference dbRef = db()) {
            dbRef.db().put(key(namespace, path), blob);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void putBlob(String namespace, String path, InputStream blob) {
        try {
            putBlob(namespace, path, ByteStreams.toByteArray(blob));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeBlob(String namespace, String path) {
        try (RocksDBReference dbRef = db()) {
            dbRef.db().delete(key(namespace, path));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeBlobs(String namespace) {
        byte[] namespacePrefix = (namespace + ".").getBytes();
        try (RocksDBReference dbRef = db()) {
            try (RocksIterator it = dbRef.db().newIterator()) {
                it.seek(namespacePrefix);
                while (it.isValid()) {
                    byte[] key = it.key();
                    for (int i = 0; i < namespacePrefix.length; i++) {
                        if (namespacePrefix[i] != key[i]) {
                            return;
                        }
                    }
                    try {
                        dbRef.db().delete(key);
                    } catch (RocksDBException e) {
                        throw new RuntimeException(e);
                    }
                    it.next();
                }
            }
        }
    }
}
