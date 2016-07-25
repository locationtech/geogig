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

class DBOptions {

    private final String dbpath;

    private final boolean readOnly;

    public DBOptions(String dbpath, boolean readOnly) {
        this.dbpath = dbpath;
        this.readOnly = readOnly;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DBOptions)) {
            return false;
        }
        DBOptions other = (DBOptions) o;
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
