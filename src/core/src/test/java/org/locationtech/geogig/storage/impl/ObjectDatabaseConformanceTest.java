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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Test;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

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

}
