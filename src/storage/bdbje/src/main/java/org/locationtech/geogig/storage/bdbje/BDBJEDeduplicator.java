/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.repository.Deduplicator;

import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

/**
 * A {@link Deduplicator} that utilizes a BDB JE {@link Database} as an index of object ids.
 * 
 * @see BDBJEDeduplicationService
 */
public class BDBJEDeduplicator implements Deduplicator {
    private static final DatabaseEntry DUMMY_DATA = new DatabaseEntry(new byte[0]);

    private static final DatabaseConfig dbConfig;
    static {
        dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(false);
        dbConfig.setTransactional(false);
        dbConfig.setTemporary(true);
    }

    final File dbdir;

    /**
     * Lazily created, to be accessed only through {@link #getDatabase()}
     */
    private Database database;

    private final Environment environment;

    private static final String databaseName = "seen";

    public BDBJEDeduplicator(File dbdir) {
        this.dbdir = dbdir;

        EnvironmentConfig ec = new EnvironmentConfig();
        ec.setAllowCreate(true);
        ec.setCacheMode(CacheMode.DEFAULT);
        ec.setSharedCache(true);
        ec.setDurability(Durability.COMMIT_NO_SYNC);
        ec.setLockTimeout(30, TimeUnit.SECONDS);
        ec.setTransactional(false);

        this.environment = new Environment(dbdir, ec);
    }

    private Database getDatabase() {
        if (this.database == null) {
            this.database = environment.openDatabase(null, databaseName, dbConfig);
        }
        return database;
    }

    @Override
    public boolean isDuplicate(ObjectId id) {
        return safeTest(id);
    }

    @Override
    public void removeDuplicates(List<ObjectId> ids) {
        Iterator<ObjectId> iterator = ids.iterator();
        while (iterator.hasNext()) {
            if (safeTest(iterator.next())) {
                iterator.remove();
            }
        }
    }

    @Override
    public void reset() {
        if (this.database != null) {
            this.database.close();// being a temporary database, there's no need to call truncate
            this.database = null;
        }
    }

    @Override
    public void release() {
        try {
            try {
                if (database != null) {
                    database.close();
                }
            } finally {
                try {
                    this.environment.close();
                } finally {
                    deleteRecursive(this.dbdir);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File each : children) {
                deleteRecursive(each);
            }
        }
        file.delete();
    }

    private boolean destructiveTest(final ObjectId id) {
        OperationStatus status = getDatabase().putNoOverwrite(null, asDatabaseEntry(id),
                DUMMY_DATA);
        return status == OperationStatus.KEYEXIST;
    }

    private boolean safeTest(final ObjectId id) {
        OperationStatus status = getDatabase().getSearchBoth(null, asDatabaseEntry(id), DUMMY_DATA,
                LockMode.DEFAULT);
        return status == OperationStatus.SUCCESS;
    }

    private DatabaseEntry asDatabaseEntry(final ObjectId id) {
        return new DatabaseEntry(id.getRawValue());
    }

    @Override
    public boolean visit(ObjectId id) {
        return destructiveTest(id);
    }
}
