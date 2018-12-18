/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * Implementation of a Map that uses a temporary rocksdb database as storage.
 */
public class RocksdbMap implements Map<String, String> {

    private RocksDB db;

    private WriteOptions writeOptions;

    private File dbDir;

    private final AtomicInteger size = new AtomicInteger();

    public RocksdbMap() {
        dbDir = Files.createTempDir();
        try {
            this.db = RocksDB.open(dbDir.getAbsolutePath());
            writeOptions = new WriteOptions();
            writeOptions.setDisableWAL(true);
            writeOptions.setSync(false);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        if (db != null) {
            writeOptions.close();
            writeOptions = null;
            db.close();
            db = null;

            deleteDB();
            dbDir = null;
        }
    }

    private void deleteDB() {
        if (!dbDir.exists()) {
            return;
        }
        File[] files = dbDir.listFiles();
        for (File f : files) {
            if (!f.delete()) {
                throw new RuntimeException("Unable to delete file " + f.getAbsolutePath());
            }
        }
        if (!dbDir.delete()) {
            throw new RuntimeException("Unable to delete directory " + dbDir.getAbsolutePath());
        }
    }

    public @Override int size() {
        return size.get();
    }

    public @Override boolean isEmpty() {
        return size() == 0;
    }

    public @Override boolean containsKey(Object key) {
        return get(key) != null;
    }

    public @Override boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    public @Override String get(Object key) {
        byte[] val;
        try {
            val = db.get(((String) key).getBytes(Charsets.UTF_8));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return val == null ? null : new String(val, Charsets.UTF_8);
    }

    public @Override String put(String key, String value) {
        byte[] keybytes = key.getBytes(Charsets.UTF_8);
        byte[] valuebytes = value.getBytes(Charsets.UTF_8);
        try {
            db.put(writeOptions, keybytes, valuebytes);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public @Override String remove(Object key) {
        final String value = get(key);
        if (value != null) {
            try {
                db.delete(writeOptions, ((String) key).getBytes(Charsets.UTF_8));
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
            }
        }
        return value;
    }

    public @Override void putAll(Map<? extends String, ? extends String> m) {
        m.forEach(this::put);
    }

    public @Override void clear() {
        try (WriteBatch batch = new WriteBatch()) {
            try (RocksIterator it = db.newIterator()) {
                it.seekToFirst();
                while (it.isValid()) {
                    byte[] key = it.key();
                    batch.delete(key);
                    it.next();
                }
            }
            try (WriteOptions opts = new WriteOptions()) {
                db.write(opts, batch);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        } finally {
            size.set(0);
        }
    }

    /**
     * Provides an iterator for each key/value entry in the map.
     * 
     * @return the iterator
     */
    public AutoCloseableIterator<Entry<String, String>> entryIterator() {
        final RocksIterator it = db.newIterator();
        it.seekToFirst();

        return new AutoCloseableIterator<Entry<String, String>>() {

            @Override
            public boolean hasNext() {
                return it.isValid();
            }

            @Override
            public java.util.Map.Entry<String, String> next() {

                byte[] keybytes = it.key();
                byte[] valuebytes = it.value();
                final String key = new String(keybytes, Charsets.UTF_8);
                final String value = new String(valuebytes, Charsets.UTF_8);
                it.next();

                return new Entry<String, String>() {

                    public @Override String getKey() {
                        return key;
                    }

                    public @Override String getValue() {
                        return value;
                    }

                    public @Override String setValue(String value) {
                        throw new UnsupportedOperationException();
                    }

                };

            }

            @Override
            public void close() {
                it.close();
            }
        };
    }

    public @Override Set<String> keySet() {
        throw new UnsupportedOperationException();
    }

    public @Override Collection<String> values() {
        throw new UnsupportedOperationException();
    }

    public @Override Set<java.util.Map.Entry<String, String>> entrySet() {
        throw new UnsupportedOperationException();
    }

}
