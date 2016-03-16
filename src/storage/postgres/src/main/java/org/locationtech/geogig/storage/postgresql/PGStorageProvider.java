package org.locationtech.geogig.storage.postgresql;

import org.locationtech.geogig.di.StorageProvider;
import org.locationtech.geogig.di.VersionedFormat;

public class PGStorageProvider extends StorageProvider {

    /**
     * Format name used for configuration.
     */
    public static final String FORMAT_NAME = "postgres";

    /**
     * Implementation version.
     */
    public static final String VERSION = "1";

    static final VersionedFormat GRAPH = new VersionedFormat(FORMAT_NAME, VERSION,
            PGGraphDatabase.class);

    static final VersionedFormat REFS = new VersionedFormat(FORMAT_NAME, VERSION,
            PGRefDatabase.class);;

    static final VersionedFormat OBJECTS = new VersionedFormat(FORMAT_NAME, VERSION,
            PGObjectDatabase.class);

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
        return "PostgreSQL backend store";
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

}
