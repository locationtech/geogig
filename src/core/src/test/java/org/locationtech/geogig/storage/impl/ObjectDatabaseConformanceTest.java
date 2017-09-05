/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.createCommits;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Throwables;

/**
 * Base class to check an {@link ObjectDatabase}'s implementation conformance to the interface
 * contract
 */
public abstract class ObjectDatabaseConformanceTest extends ObjectStoreConformanceTest {

    protected final @Override ObjectDatabase createOpen() throws IOException {
        return createOpen(false);
    }

    protected abstract ObjectDatabase createOpen(boolean readOnly) throws IOException;

    private ObjectDatabase db() {
        return (ObjectDatabase) db;
    }

    private ObjectStore closeAndCreate(ObjectStore db, boolean readOnly) throws IOException {
        if (db != null) {
            db.close();
        }
        return createOpen(readOnly);
    }

    public @Test void testMultipleInstances() throws IOException {
        try (ObjectStore db2 = createOpen(false)) {
            assertNotSame(db, db2);

            RevObject obj = RevTree.EMPTY;

            assertTrue(db.put(obj));

            assertFalse(db2.put(obj));

            RevObject revObject = db2.get(obj.getId());
            assertEquals(obj, revObject);
        }
    }

    public @Test void testReadOnlyHint() throws IOException {
        db = closeAndCreate(db, true);
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("read only"));
        }
    }

    public @Test void testReadOnlyHintPreservedOnReopen() throws IOException {
        db = closeAndCreate(db, true);
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("read only"));
        }

        db.close();
        db.open();
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("read only"));
        }
    }

    public @Test void testReadOnlyHint2() throws IOException {
        db = closeAndCreate(db, true);
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected ISE on read only hint");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("read only"));
        }

        db.close();
        db = createOpen(false);

        assertTrue(db.put(obj));
    }

    public @Test void testReadOnlyHint3() throws IOException {
        db = closeAndCreate(db, true);
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("read only"));
        }
        ObjectStore db2 = createOpen(false);

        assertTrue(db2.put(obj));
        db.close();
        db2.close();
    }

    public @Test void testPutUpdatesGraphDatabase() {
        ObjectDatabase db = db();
        GraphDatabase graph = db.getGraphDatabase();
        assertNotNull(graph);

        List<RevCommit> commits = createCommits(2);
        RevCommit c1 = commits.get(0);
        RevCommit c2 = commits.get(1);

        assertFalse(graph.exists(c1.getId()));
        assertFalse(graph.exists(c2.getId()));
        db.put(c1);
        db.put(c2);

        assertTrue(graph.exists(c1.getId()));
        assertTrue(graph.exists(c2.getId()));
    }

    public @Test void testPutAllUpdatesGraphDatabase() {
        ObjectDatabase db = db();
        GraphDatabase graph = db.getGraphDatabase();
        assertNotNull(graph);

        List<RevCommit> commits = createCommits(100);
        db.putAll(commits.iterator());
        commits.forEach((c) -> assertTrue(c.getId().toString(), graph.exists(c.getId())));
    }

    /**
     * Make sure graph entries are only added for commits that have actually been added to the
     * {@link ObjectDatabase}, so that the actual commits in the repository are in sync with the
     * {@link GraphDatabase}
     */
    public @Test void testPutAllUpdatesGraphDatabaseConsistencyUnderFailure() {
        ObjectDatabase db = db();
        GraphDatabase graph = db.getGraphDatabase();
        assertNotNull(graph);

        // hack: use a large number of objects cause backend implementations usually perform inserts
        // in batches of 10K objects
        List<RevCommit> commits = createCommits(11_000);

        Set<ObjectId> inserted = new HashSet<>();
        // listener that throws an exception when it reaches the 100 inserted commits, hence forcing
        // the operation to abort, but the commits that have been inserted shall remain, since
        // listener.inserted() shall be called once it's reached the storage backend for sure, as
        // per ObjectStore.putAll() contract
        BulkOpListener failinglistener = new BulkOpListener() {
            volatile int count = 0;

            public @Override void inserted(ObjectId id, @Nullable Integer size) {
                inserted.add(id);
                count++;
                if (count == 100) {
                    throw new RuntimeException("meant to abort putAll()");
                }
            }
        };

        try {
            db.putAll(commits.iterator(), failinglistener);
            fail("expected failure");
        } catch (RuntimeException e) {
            Throwable rootCause = Throwables.getRootCause(e);
            assertTrue(rootCause.getMessage().contains("meant to abort"));
        }

        assertTrue(inserted.size() >= 100 && inserted.size() < commits.size());

        for (RevCommit c : commits) {
            ObjectId id = c.getId();
            if (inserted.contains(id)) {
                assertTrue(id.toString(), graph.exists(id));
            }
        }

    }
}
