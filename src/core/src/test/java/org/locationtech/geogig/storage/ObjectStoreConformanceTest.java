/* Copyright (c) 2015 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import static com.google.common.collect.Iterators.concat;
import static com.google.common.collect.Iterators.emptyIterator;
import static com.google.common.collect.Iterators.singletonIterator;
import static com.google.common.collect.Iterators.transform;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.plumbing.diff.RevObjectTestSupport;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.BulkOpListener.CountingListener;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Base class to check an {@link ObjectStore}'s implementation conformance to the interface contract
 */
public abstract class ObjectStoreConformanceTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private TestPlatform platform;

    private Hints hints;

    protected ObjectStore db;

    protected RevObjectTestSupport objects = new RevObjectTestSupport();

    @Before
    public void setUp() throws IOException {
        File root = folder.getRoot();
        folder.newFolder(".geogig");
        File home = folder.newFolder("home");
        platform = new TestPlatform(root);
        platform.setUserHome(home);
        hints = new Hints();

        this.db = createOpen(platform, hints);
        //this.db.open();
    }

    @After
    public void after() {
        if (db != null) {
            db.close();
        }
    }

    protected ObjectStore closeAndCreate(ObjectStore db, Platform platform, Hints hints) {
        if (db != null) {
            db.close();
        }
        return createOpen(platform, hints);
    }

    protected abstract ObjectStore createOpen(Platform platform, Hints hints);

    @Test
    public void testReadOnlyHint() {
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        db = closeAndCreate(db, platform, hints);
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("read only"));
        }
    }

    @Test
    public void testReadOnlyHintPreservedOnReopen() {
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        db = closeAndCreate(db, platform, hints);
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

    @Test
    public void testReadOnlyHint2() {
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        db = closeAndCreate(db, platform, hints);
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("read only"));
        }

        db.close();
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.FALSE);
        db = createOpen(platform, hints);

        assertTrue(db.put(obj));
    }

    @Test
    public void testReadOnlyHint3() {
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        db = closeAndCreate(db, platform, hints);
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("read only"));
        }

        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.FALSE);
        ObjectStore db2 = createOpen(platform, hints);

        assertTrue(db2.put(obj));
        db.close();
        db2.close();
    }

    @Test
    public void testMultipleInstances() {
        try (ObjectStore db2 = createOpen(platform, hints)) {
            assertNotSame(db, db2);

            RevObject obj = RevTree.EMPTY;

            assertTrue(db.put(obj));

            assertFalse(db2.put(obj));

            RevObject revObject = db2.get(obj.getId());
            assertEquals(obj, revObject);
        }
    }

    @Test
    public void testChecksClosed() {
        db.close();

        checkClosed(() -> db.delete(ObjectId.NULL));
        checkClosed(() -> db.deleteAll(emptyIterator()));
        checkClosed(() -> db.deleteAll(emptyIterator(), BulkOpListener.NOOP_LISTENER));
        checkClosed(() -> db.exists(RevTree.EMPTY_TREE_ID));
        checkClosed(() -> db.get(RevTree.EMPTY_TREE_ID));
        checkClosed(() -> db.get(RevTree.EMPTY_TREE_ID, RevTree.class));
        checkClosed(() -> db.getAll(ImmutableList.of()));
        checkClosed(() -> db.getAll(ImmutableList.of(), BulkOpListener.NOOP_LISTENER));
        checkClosed(() -> db.getIfPresent(ObjectId.NULL));
        checkClosed(() -> db.getIfPresent(RevTree.EMPTY_TREE_ID, RevTree.class));
        checkClosed(() -> db.lookUp("abcd1234"));
        checkClosed(() -> db.put(RevTree.EMPTY));
        checkClosed(() -> db.putAll(emptyIterator()));
        checkClosed(() -> db.putAll(emptyIterator(), BulkOpListener.NOOP_LISTENER));
    }

    @Test
    public void testChecksNullArgs() {
        checkNullArgument(() -> db.delete(null));
        checkNullArgument(() -> db.deleteAll(null));
        checkNullArgument(() -> db.deleteAll(null, BulkOpListener.NOOP_LISTENER));
        checkNullArgument(() -> db.deleteAll(emptyIterator(), null));
        checkNullArgument(() -> db.exists(null));
        checkNullArgument(() -> db.get(null));
        checkNullArgument(() -> db.get(null, RevTree.class));
        checkNullArgument(() -> db.get(RevTree.EMPTY_TREE_ID, null));
        checkNullArgument(() -> db.getAll(null));
        checkNullArgument(() -> db.getAll(null, BulkOpListener.NOOP_LISTENER));
        checkNullArgument(() -> db.getAll(ImmutableList.of(), null));
        checkNullArgument(() -> db.getIfPresent(null));
        checkNullArgument(() -> db.getIfPresent(null, RevTree.class));
        checkNullArgument(() -> db.getIfPresent(RevTree.EMPTY_TREE_ID, null));
        checkNullArgument(() -> db.lookUp(null));
        checkNullArgument(() -> db.put(null));
        checkNullArgument(() -> db.putAll(null));
        checkNullArgument(() -> db.putAll(null, BulkOpListener.NOOP_LISTENER));
        checkNullArgument(() -> db.putAll(emptyIterator(), null));
    }

    private void checkClosed(Runnable op) {
        try {
            op.run();
            fail("Expected IAE on closed database");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("closed"));
        }
    }

    private void checkNullArgument(Runnable op) {
        try {
            op.run();
            fail("Expected NPE on null argument");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("is null"));
        }
    }

    @Test
    public void testDelete() {
        assertTrue(db.put(RevTree.EMPTY));
        assertTrue(db.delete(RevTree.EMPTY_TREE_ID));
        assertFalse(db.delete(RevTree.EMPTY_TREE_ID));
    }

    @Test
    public void testDeleteAll() {
        ImmutableList<RevObject> objs = ImmutableList.of(objects.feature(0, null, "some value"),
                objects.feature(1, "value", new Integer(111)), objects.feature(2, (Object) null));

        for (RevObject o : objs) {
            assertTrue(db.put(o));
        }

        ObjectId notInDb1 = ObjectId.forString("fake1");
        ObjectId notInDb2 = ObjectId.forString("fake2");

        Function<RevObject, ObjectId> toId = p -> p.getId();
        Iterator<ObjectId> ids = concat(singletonIterator(notInDb1),
                transform(objs.iterator(), toId), singletonIterator(notInDb2));

        assertEquals(3, db.deleteAll(ids));
    }

    @Test
    public void testDeleteAllWithListener() {
        ImmutableList<RevObject> objs = ImmutableList.of(objects.feature(0, null, "some value"),
                objects.feature(1, "value", new Integer(111)), objects.feature(2, (Object) null));

        for (RevObject o : objs) {
            assertTrue(db.put(o));
        }

        ObjectId notInDb1 = ObjectId.forString("fake1");
        ObjectId notInDb2 = ObjectId.forString("fake2");

        Function<RevObject, ObjectId> toId = p -> p.getId();
        Iterator<ObjectId> ids = concat(singletonIterator(notInDb1),
                transform(objs.iterator(), toId), singletonIterator(notInDb2));

        CountingListener listener = BulkOpListener.newCountingListener();
        assertEquals(3, db.deleteAll(ids, listener));
        assertEquals(3, listener.deleted());
        assertEquals(2, listener.notFound());
    }

    @Test
    public void testExists() {

        RevFeature o = objects.feature(0, null, "some value");
        assertFalse(db.exists(o.getId()));
        assertTrue(db.put(o));
        assertTrue(db.exists(o.getId()));
        assertTrue(db.exists(o.getId()));
    }

    @Test
    public void testGet() {
        RevFeature o = objects.feature(0, null, "some value");

        try {
            db.get(o.getId());
            fail("expected IAE on non existent object");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }

        assertTrue(db.put(o));

        RevObject read = db.get(o.getId());
        assertNotNull(read);
        assertEquals(o, read);
    }

    @Test
    public void testGetWithCast() {
        RevFeature o = objects.feature(0, null, "some value");

        try {
            db.get(o.getId(), RevFeature.class);
            fail("expected IAE on non existent object");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }

        assertTrue(db.put(o));

        RevObject read = db.get(o.getId(), RevFeature.class);
        assertNotNull(read);
        assertEquals(o, read);

        try {
            db.get(o.getId(), RevTree.class);
            fail("expected IAE on wrong type expectation");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }
    }

    @Test
    public void testGetAll() {

        ImmutableList<RevObject> expected = ImmutableList.of(
                objects.feature(0, null, "some value"),
                objects.feature(1, "value", new Integer(111)), objects.feature(2, (Object) null),
                RevTree.EMPTY);

        for (RevObject o : expected) {
            assertTrue(db.put(o));
        }
        assertTrue(db.put(objects.feature(5, "not queried 1")));
        assertTrue(db.put(objects.feature(6, "not queried 2")));

        Function<RevObject, ObjectId> toId = p -> p.getId();
        Iterable<ObjectId> ids = Iterables.transform(expected, toId);

        Iterator<RevObject> iterator = db.getAll(ids);
        List<RevObject> actual = ImmutableList.copyOf(iterator);

        assertEquals(Sets.newHashSet(ids), Sets.newHashSet(Iterables.transform(actual, toId)));

    }

    @Test
    public void testGetAllWithListener() {

        ImmutableList<RevObject> expected = ImmutableList.of(
                objects.feature(0, null, "some value"),
                objects.feature(1, "value", new Integer(111)), objects.feature(2, (Object) null),
                RevTree.EMPTY);

        for (RevObject o : expected) {
            assertTrue(db.put(o));
        }
        assertTrue(db.put(objects.feature(5, "not queried 1")));
        assertTrue(db.put(objects.feature(6, "not queried 2")));

        Function<RevObject, ObjectId> toId = p -> p.getId();
        Iterable<ObjectId> ids = Iterables.transform(expected, toId);

        CountingListener listener = BulkOpListener.newCountingListener();

        Iterable<ObjectId> notFound = ImmutableList.of(ObjectId.forString("notfound1"),
                ObjectId.forString("notfound2"));

        Iterator<RevObject> result = db.getAll(Iterables.concat(notFound, ids), listener);

        List<RevObject> actual = ImmutableList.copyOf(result);

        assertEquals(Sets.newHashSet(ids), Sets.newHashSet(Iterables.transform(actual, toId)));

        assertEquals(expected.size(), listener.found());
        assertEquals(2, listener.notFound());
    }

    @Test
    public void testGetIfPresent() {
        ImmutableList<RevObject> expected = ImmutableList.of(
                objects.feature(0, null, "some value"),
                objects.feature(1, "value", new Integer(111)), objects.feature(2, (Object) null),
                RevTree.EMPTY);

        for (RevObject o : expected) {
            assertTrue(db.put(o));
        }
        for (RevObject o : expected) {
            assertEquals(o, db.getIfPresent(o.getId()));
        }
        assertNull(db.getIfPresent(ObjectId.forString("notfound")));
    }

    @Test
    public void testGetIfPresentWithCasting() {
        assertTrue(db.put(RevTree.EMPTY));

        assertEquals(RevTree.EMPTY, db.getIfPresent(RevTree.EMPTY_TREE_ID, RevTree.class));

        assertNull(db.getIfPresent(RevTree.EMPTY_TREE_ID, RevTag.class));
    }

    @Test
    public void testLookUpPrecondition() {

        final ObjectId id1 = ObjectId.valueOf("0123456700000000000000000000000000000000");
        final ObjectId id2 = ObjectId.valueOf("01234567abc00000000000000000000000000000");

        assertTrue(db.put(objects.feature(id1, "f1")));
        assertTrue(db.put(objects.feature(id2, "f2")));

        final String fullId = id1.toString();

        for (int i = 0; i < 7; i++) {
            try {
                String partialId = fullId.substring(0, i);
                db.lookUp(partialId);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(),
                        e.getMessage().contains("must be at least 8 characters long"));
            }
        }
        List<ObjectId> found = db.lookUp(fullId.substring(0, 9));
        assertNotNull(found);
        assertEquals(found.toString(), 1, found.size());
        assertEquals(found.toString(), id1, found.get(0));
    }

    @Test
    public void testLookUp() {

        final ObjectId id1 = ObjectId.valueOf("0123456700000000000000000000000000000000");
        final ObjectId id2 = ObjectId.valueOf("0123456780000000000000000000000000000000");
        final ObjectId id3 = ObjectId.valueOf("0123456789000000000000000000000000000000");
        final ObjectId id4 = ObjectId.valueOf("0123456789a00000000000000000000000000000");
        final ObjectId id5 = ObjectId.valueOf("0123456789ab0000000000000000000000000000");

        assertTrue(db.put(objects.feature(id1, "f1")));
        assertTrue(db.put(objects.feature(id2, "f2")));
        assertTrue(db.put(objects.feature(id3, "f3")));
        assertTrue(db.put(objects.feature(id4, "f4")));
        assertTrue(db.put(objects.feature(id5, "f5")));

        HashSet<ObjectId> matches;

        matches = Sets.newHashSet(db.lookUp("01234567"));
        assertEquals(Sets.newHashSet(id1, id2, id3, id4, id5), matches);

        matches = Sets.newHashSet(db.lookUp("012345678"));
        assertEquals(Sets.newHashSet(id2, id3, id4, id5), matches);

        matches = Sets.newHashSet(db.lookUp("0123456789"));
        assertEquals(Sets.newHashSet(id3, id4, id5), matches);

        matches = Sets.newHashSet(db.lookUp("0123456789a"));
        assertEquals(Sets.newHashSet(id4, id5), matches);

        matches = Sets.newHashSet(db.lookUp("0123456789ab"));
        assertEquals(Sets.newHashSet(id5), matches);

        matches = Sets.newHashSet(db.lookUp("0123456789abc"));
        assertEquals(Sets.newHashSet(), matches);
    }

    @Test
    public void testPut() {
        assertTrue(db.put(RevTree.EMPTY));
        assertFalse(db.put(RevTree.EMPTY));
    }

    @Test
    public void testPutAll() {

        ImmutableList<RevObject> expected = ImmutableList.of(
                objects.feature(0, null, "some value"),
                objects.feature(1, "value", new Integer(111)), objects.feature(2, (Object) null),
                RevTree.EMPTY);

        db.putAll(expected.iterator());
        for (RevObject o : expected) {
            assertEquals(o, db.get(o.getId()));
        }
    }

    @Test
    public void testPutAllWithListener() {

        ImmutableList<RevObject> expected = ImmutableList.of(
                objects.feature(0, null, "some value"),
                objects.feature(1, "value", new Integer(111)), objects.feature(2, (Object) null),
                RevTree.EMPTY);

        Function<RevObject, ObjectId> toId = p -> p.getId();
        final Iterable<ObjectId> ids = Iterables.transform(expected, toId);

        final List<ObjectId> found = new CopyOnWriteArrayList<>();
        final List<ObjectId> inserted = new CopyOnWriteArrayList<>();

        BulkOpListener listener = new BulkOpListener() {
            @Override
            public void found(ObjectId object, @Nullable Integer storageSizeBytes) {
                found.add(object);
            }

            @Override
            public void inserted(ObjectId object, @Nullable Integer storageSizeBytes) {
                inserted.add(object);
            }

        };

        db.putAll(expected.iterator(), listener);

        assertTrue(found.toString(), found.isEmpty());
        assertEquals(Sets.newHashSet(ids), Sets.newHashSet(inserted));

        found.clear();
        inserted.clear();

        db.putAll(expected.iterator(), listener);

        assertTrue(inserted.toString(), inserted.isEmpty());
        assertEquals(Sets.newHashSet(ids), Sets.newHashSet(found));
    }

}
