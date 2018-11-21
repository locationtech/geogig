package org.locationtech.geogig.storage.postgresql.v9;

import javax.sql.DataSource;

import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.PGStorage;

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
