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

import org.rocksdb.ColumnFamilyHandle;

import com.google.common.collect.ImmutableMap;

class DBConfig {

    private final String dbpath;

    private final boolean readOnly;

    private ImmutableMap<String, String> defaultMetadata;

    public DBConfig(String dbpath, boolean readOnly) {
        this(dbpath, readOnly, ImmutableMap.of());
    }

    public DBConfig(String dbpath, boolean readOnly, Map<String, String> defaultMetadata) {
        this.dbpath = dbpath;
        this.readOnly = readOnly;
        this.defaultMetadata = ImmutableMap.copyOf(defaultMetadata);
    }

    /**
     * If not empty, a {@link ColumnFamilyHandle} named "metadata" will be created with the provided
     * key/value pairs, as long as {@code readOnly == false} and the db is being created for the
     * first time.
     */
    public ImmutableMap<String, String> getDefaultMetadata() {
        return defaultMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DBConfig)) {
            return false;
        }
        DBConfig other = (DBConfig) o;
        return dbpath.equals(other.dbpath) && readOnly == other.readOnly;
    }

    @Override
    public int hashCode() {
        return 31 * dbpath.hashCode() + (readOnly ? 1 : 0);
    }

    @Override
    public String toString() {
        return "rocksdb[path: " + dbpath + ", readonly: " + readOnly + "]";
    }

    public String getDbPath() {
        return dbpath;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
