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

import org.locationtech.geogig.storage.StorageProvider;
import org.locationtech.geogig.storage.VersionedFormat;
import org.locationtech.geogig.storage.fs.FileRefDatabase;

public class RocksdbStorageProvider extends StorageProvider {

    /**
     * Format name used for configuration.
     */
    public static final String FORMAT_NAME = "rocksdb";

    /**
     * Implementation version.
     */
    public static final String VERSION = "1";

    static final VersionedFormat GRAPH = new VersionedFormat(FORMAT_NAME, VERSION,
            RocksdbGraphDatabase.class);

    static final VersionedFormat REFS = new VersionedFormat("file", "1.0",
            FileRefDatabase.class);

    static final VersionedFormat OBJECTS = new VersionedFormat(FORMAT_NAME, VERSION,
            RocksdbObjectDatabase.class);

    static final VersionedFormat INDEX = new VersionedFormat(FORMAT_NAME, VERSION,
            RocksdbIndexDatabase.class);

    @Override
    public String getName() {
        return FORMAT_NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "RocksDB backend store";
    }

    @Override
    public VersionedFormat getObjectDatabaseFormat() {
        return OBJECTS;
    }

    @Override
    public VersionedFormat getGraphDatabaseFormat() {
        return GRAPH;
    }

    @Override
    public VersionedFormat getRefsDatabaseFormat() {
        return REFS;
    }

    @Override
    public VersionedFormat getIndexDatabaseFormat() {
        return INDEX;
    }

}
