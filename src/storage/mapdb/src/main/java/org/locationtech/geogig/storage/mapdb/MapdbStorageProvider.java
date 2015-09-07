/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.mapdb;

import org.locationtech.geogig.di.StorageProvider;
import org.locationtech.geogig.di.VersionedFormat;
import org.locationtech.geogig.storage.fs.FileRefDatabase;

public class MapdbStorageProvider extends StorageProvider {

    private static final String NAME = "mapdb";

    private static final String VERSION = "0.1";

    private static final VersionedFormat REFS = new VersionedFormat("file", "1.0",
            FileRefDatabase.class);

    private static final VersionedFormat GRAPH = new VersionedFormat(NAME, VERSION,
            MapdbGraphDatabase.class);

    private static final VersionedFormat OBJECT = new VersionedFormat(NAME, VERSION,
            MapdbObjectDatabase.class);

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
        return "Stores revision and graph objects in Mapdb, refs in regular files";
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
