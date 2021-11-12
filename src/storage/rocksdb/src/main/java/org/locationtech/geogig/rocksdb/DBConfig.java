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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.rocksdb.ColumnFamilyHandle;

class DBConfig {

    private final String dbpath;

    private final boolean readOnly;

    private Map<String, String> defaultMetadata;

    private Set<String> columnFamilyNames;

    public DBConfig(String dbpath, boolean readOnly) {
        this(dbpath, readOnly, Collections.emptyMap(), Collections.emptySet());
    }

    public DBConfig(String dbpath, boolean readOnly, Map<String, String> defaultMetadata,
            Set<String> columnFamilyNames) {
        this.dbpath = dbpath;
        this.readOnly = readOnly;
        this.columnFamilyNames = columnFamilyNames;
        this.defaultMetadata = Map.copyOf(defaultMetadata);
    }

    /**
     * @return the names of extra columns to create when the database is created
     */
    public Set<String> getColumnFamilyNames() {
        return columnFamilyNames;
    }

    /**
     * If not empty, a {@link ColumnFamilyHandle} named "metadata" will be created with the provided
     * key/value pairs, as long as {@code readOnly == false} and the db is being created for the
     * first time.
     */
    public Map<String, String> getDefaultMetadata() {
        return defaultMetadata;
    }

    public @Override boolean equals(Object o) {
        if (!(o instanceof DBConfig)) {
            return false;
        }
        DBConfig other = (DBConfig) o;
        return dbpath.equals(other.dbpath) && readOnly == other.readOnly;
    }

    public @Override int hashCode() {
        return 31 * dbpath.hashCode() + (readOnly ? 1 : 0);
    }

    public @Override String toString() {
        return "rocksdb[path: " + dbpath + ", readonly: " + readOnly + "]";
    }

    public String getDbPath() {
        return dbpath;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
