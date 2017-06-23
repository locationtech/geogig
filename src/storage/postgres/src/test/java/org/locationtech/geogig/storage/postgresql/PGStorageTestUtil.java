package org.locationtech.geogig.storage.postgresql;

import javax.sql.DataSource;

/**
 * Utility class to give external testing packages access to PGStorage data source functions.
 */
public class PGStorageTestUtil {
    public static DataSource newDataSource(Environment config) {
        return PGStorage.newDataSource(config);
    }

    public static void closeDataSource(DataSource ds) {
        PGStorage.closeDataSource(ds);
    }
}
