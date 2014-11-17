/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.bdbje;

import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.storage.Deduplicator;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

/**
 * A {@link Deduplicator} that utilizes a BDB JE {@link Database} as an index of object ids.
 * 
 * @see BDBJEDeduplicationService
 */
public class BDBJEDeduplicator implements Deduplicator {
    private static final DatabaseEntry DUMMY_DATA = new DatabaseEntry(new byte[0]);

    private Database objectDb;

    private final BDBJEDeduplicationService service;

    /**
     * @param database the database that represents the index of object ids
     * @param service used by {@link #reset()} and {@link #release()}
     */
    public BDBJEDeduplicator(Database database, BDBJEDeduplicationService service) {
        this.objectDb = database;
        this.service = service;
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
        objectDb.close();
        service.reset(this);
    }

    @Override
    public void release() {
        objectDb.close();
        service.deregister(this);
    }

    /**
     * Assigns a new database to this deduplicator, only to be called by
     * {@link BDBJEDeduplicationService#reset(BDBJEDeduplicator)}.
     * 
     * @param db the database that replaces the current one.
     */
    protected void setDatabase(Database db) {
        this.objectDb = db;
    }

    private boolean destructiveTest(final ObjectId id) {
        OperationStatus status = objectDb.putNoOverwrite(null, asDatabaseEntry(id), DUMMY_DATA);
        return status == OperationStatus.KEYEXIST;
    }

    private boolean safeTest(final ObjectId id) {
        OperationStatus status = objectDb.getSearchBoth(null, asDatabaseEntry(id), DUMMY_DATA,
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
