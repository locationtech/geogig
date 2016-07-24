/* Copyright (c) 2015 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import org.locationtech.geogig.storage.StorageProvider;
import org.locationtech.geogig.storage.VersionedFormat;
import org.locationtech.geogig.storage.fs.FileRefDatabase;

public class JEStorageProviderV02 extends StorageProvider {

    private static final String NAME = "bdbje";

    private static final String VERSION = "0.2";

    private static final VersionedFormat REFS = new VersionedFormat("file", "1.0",
            FileRefDatabase.class);

    private static final VersionedFormat GRAPH = new VersionedFormat(NAME, VERSION,
            JEGraphDatabase_v0_2.class);

    private static final VersionedFormat OBJECT = new VersionedFormat(NAME, VERSION,
            JEObjectDatabase_v0_2.class);

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
        return "Stores revision objects and graph objects in separate BerkeleyDB JE databases with improved binary serialization, refs in regular files.";
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
