/* Copyright (c) 2015 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.sqlite;

import org.locationtech.geogig.di.VersionedFormat;
import org.locationtech.geogig.storage.StorageProvider;
import org.locationtech.geogig.storage.fs.FileRefDatabase;

public class XerialStorageProvider extends StorageProvider {

    private static final String NAME = SQLiteStorage.FORMAT_NAME;

    private static final String VERSION = SQLiteStorage.VERSION;

    private static final VersionedFormat REFS = new VersionedFormat("file", "1.0",
            FileRefDatabase.class);

    private static final VersionedFormat GRAPH = new VersionedFormat(NAME, VERSION,
            XerialGraphDatabase.class);

    private static final VersionedFormat OBJECT = new VersionedFormat(NAME, VERSION,
            XerialObjectDatabase.class);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Stores revision and graph objects in SQLite, refs in regular files";
    }

    @Override
    public VersionedFormat getObjectDatabaseFormat() {
        return OBJECT;
    }

    @Override
    public VersionedFormat getGraphDatabaseFormat() {
        return GRAPH;
    }

    @Override
    public VersionedFormat getRefsDatabaseFormat() {
        return REFS;
    }

}
