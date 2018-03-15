/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

/**
 * Implementation of a Map that uses a temporary rocksdb database as storage.
 */
public class RocksdbMap<K extends Serializable, V extends Serializable> implements Map<K, V> {

    private static final int PUT_THRESHOLD = 1_000;

    private Map<K, V> putBuffer = new HashMap<K, V>();

    private RocksDB db = null;

    private File dbDir = null;

    public RocksdbMap() {
        dbDir = Files.createTempDir();
        try {
            this.db = RocksDB.open(dbDir.getAbsolutePath());
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        if (db != null) {
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

    private void putAll() {
        if (putBuffer.size() > 0) {
            Iterator<Entry<K, V>> iterator = putBuffer.entrySet().iterator();
            try (WriteOptions wo = new WriteOptions()) {
                wo.setDisableWAL(true);
                wo.setSync(false);
                try (WriteBatch batch = new WriteBatch()) {
                    while (iterator.hasNext()) {
                        try (ByteArrayOutputStream keyBytes = new ByteArrayOutputStream();
                                ObjectOutput keyOut = new ObjectOutputStream(keyBytes);
                                ByteArrayOutputStream valueBytes = new ByteArrayOutputStream();
                                ObjectOutput valueOut = new ObjectOutputStream(valueBytes);) {
                            Entry<K, V> object = iterator.next();
                            keyOut.writeObject(object.getKey());
                            valueOut.writeObject(object.getValue());
                            keyOut.flush();
                            valueOut.flush();
                            batch.put(keyBytes.toByteArray(), valueBytes.toByteArray());
                        }

                    }
                    db.write(wo, batch);
                }
                wo.sync();
            } catch (IOException | RocksDBException e) {
                throw new RuntimeException(e);
            }
            putBuffer.clear();
        }
    }

    @Override
    public int size() {
        putAll();
        if (db == null) {
            return 0;
        }
        int count = 0;
        try (RocksIterator it = db.newIterator()) {
            while (it.isValid()) {
                count++;
                it.next();
            }
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        putAll();
        boolean empty = true;
        try (RocksIterator it = db.newIterator()) {
            empty = it.isValid();
        }
        return empty;
    }

    @Override
    public boolean containsKey(Object key) {
        putAll();
        Preconditions.checkState(key instanceof Serializable, "key is not serializable");

        try {
            ByteArrayOutputStream keyBytes = new ByteArrayOutputStream();
            ObjectOutput keyOut = new ObjectOutputStream(keyBytes);
            keyOut.writeObject(key);
            keyOut.flush();
            byte[] valueBytes = db.get(keyBytes.toByteArray());
            return valueBytes != null;
        } catch (IOException | RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V get(Object key) {
        putAll();
        Preconditions.checkState(key instanceof Serializable, "key is not serializable");

        try {
            ByteArrayOutputStream keyBytes = new ByteArrayOutputStream();
            ObjectOutput keyOut = new ObjectOutputStream(keyBytes);
            keyOut.writeObject(key);
            keyOut.flush();
            byte[] valueBytes = db.get(keyBytes.toByteArray());
            ObjectInputStream obj = new ObjectInputStream(new ByteArrayInputStream(valueBytes));
            @SuppressWarnings("unchecked")
            V value = (V) obj.readObject();
            return value;
        } catch (IOException | RocksDBException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public V put(K key, V value) {
        putBuffer.put(key, value);
        if (putBuffer.size() > PUT_THRESHOLD) {
            putAll();
        }
        return value;
    }

    @Override
    public V remove(Object key) {
        putAll();
        V value = get(key);

        try {
            ByteArrayOutputStream keyBytes = new ByteArrayOutputStream();
            ObjectOutput keyOut = new ObjectOutputStream(keyBytes);
            keyOut.writeObject(key);
            keyOut.flush();
            db.remove(keyBytes.toByteArray());
        } catch (IOException | RocksDBException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        putBuffer.clear();
        try (WriteBatch batch = new WriteBatch()) {
            try (RocksIterator it = db.newIterator()) {
                it.seekToFirst();

                while (it.isValid()) {
                    byte[] key = it.key();
                    batch.remove(key);
                    it.next();
                }
            }
            try (WriteOptions opts = new WriteOptions()) {
                db.write(opts, batch);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Provides an iterator for each key/value entry in the map.
     * 
     * @return the iterator
     */
    public AutoCloseableIterator<Entry<K, V>> entryIterator() {
        
        putAll();

        final RocksIterator it = db.newIterator();
        it.seekToFirst();
        
        return new AutoCloseableIterator<Entry<K, V>>() {

            @Override
            public boolean hasNext() {
                return it.isValid();
            }

            @SuppressWarnings("unchecked")
            @Override
            public java.util.Map.Entry<K, V> next() {
                try {
                    ObjectInputStream keyBytes = new ObjectInputStream(new ByteArrayInputStream(it.key()));
                    final K key = (K) keyBytes.readObject();
                    
                    ObjectInputStream valueBytes = new ObjectInputStream(new ByteArrayInputStream(it.value()));
                    final V value = (V) valueBytes.readObject();
                    it.next();
                    return new Entry<K, V>() {

                        @Override
                        public K getKey() {
                            return key;
                        }

                        @Override
                        public V getValue() {
                            return value;
                        }

                        @Override
                        public V setValue(V value) {
                            throw new UnsupportedOperationException();
                        }
                        
                    };
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void close() {
                it.close();
            }
        };
    }

    @Override
    public Set<K> keySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }

}
