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

import static com.google.common.collect.Iterators.concat;
import static com.google.common.collect.Iterators.singletonIterator;
import static com.google.common.collect.Iterators.transform;
import static java.util.Collections.emptyIterator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.feature;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.featureForceId;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.hashString;
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator;
import org.locationtech.geogig.plumbing.diff.DepthTreeIterator.Strategy;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.BulkOpListener.CountingListener;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Base class to check an {@link ObjectStore}'s implementation conformance to the interface contract
 */
public abstract class ObjectStoreConformanceTest {

    protected ObjectStore db;

    @Before
    public void setUp() throws Exception {
        this.db = createOpen();
    }

    @After
    public void after() {
        if (db != null) {
            db.close();
        }
    }

    protected abstract ObjectStore createOpen() throws IOException;

    @Test
    public void testChecksClosed() {
        db.close();

        checkClosed(() -> db.delete(ObjectId.NULL));
        checkClosed(() -> db.deleteAll(emptyIterator()));
        checkClosed(() -> db.deleteAll(emptyIterator(), NOOP_LISTENER));
        checkClosed(() -> db.exists(RevTree.EMPTY_TREE_ID));
        checkClosed(() -> db.get(RevTree.EMPTY_TREE_ID));
        checkClosed(() -> db.get(RevTree.EMPTY_TREE_ID, RevTree.class));
        checkClosed(() -> db.getAll(ImmutableList.of()));
        checkClosed(() -> db.getAll(ImmutableList.of(), NOOP_LISTENER));
        checkClosed(() -> db.getAll(ImmutableList.of(), NOOP_LISTENER, RevTree.class));
        checkClosed(() -> db.getIfPresent(ObjectId.NULL));
        checkClosed(() -> db.getIfPresent(RevTree.EMPTY_TREE_ID, RevTree.class));
        checkClosed(() -> db.lookUp("abcd1234"));
        checkClosed(() -> db.put(RevTree.EMPTY));
        checkClosed(() -> db.putAll(emptyIterator()));
        checkClosed(() -> db.putAll(emptyIterator(), NOOP_LISTENER));
    }

    @Test
    public void testChecksNullArgs() {
        checkNullArgument(() -> db.delete(null));
        checkNullArgument(() -> db.deleteAll(null));
        checkNullArgument(() -> db.deleteAll(null, NOOP_LISTENER));
        checkNullArgument(() -> db.deleteAll(emptyIterator(), null));
        checkNullArgument(() -> db.exists(null));
        checkNullArgument(() -> db.get(null));
        checkNullArgument(() -> db.get(null, RevTree.class));
        checkNullArgument(() -> db.get(RevTree.EMPTY_TREE_ID, null));
        checkNullArgument(() -> db.getAll(null));
        checkNullArgument(() -> db.getAll(null, NOOP_LISTENER));
        checkNullArgument(() -> db.getAll(ImmutableList.of(), NOOP_LISTENER, null));
        checkNullArgument(() -> db.getAll(ImmutableList.of(), null));
        checkNullArgument(() -> db.getIfPresent(null));
        checkNullArgument(() -> db.getIfPresent(null, RevTree.class));
        checkNullArgument(() -> db.getIfPresent(RevTree.EMPTY_TREE_ID, null));
        checkNullArgument(() -> db.lookUp(null));
        checkNullArgument(() -> db.put(null));
        checkNullArgument(() -> db.putAll(null));
        checkNullArgument(() -> db.putAll(null, NOOP_LISTENER));
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
        db.delete(RevTree.EMPTY_TREE_ID);
        assertFalse(db.exists(RevTree.EMPTY_TREE_ID));
    }

    @Test
    public void testDeleteAll() {
        ImmutableList<RevObject> objs = ImmutableList.of(feature(0, null, "some value"),
                feature(1, "value", new Integer(111)), feature(2, (Object) null));

        for (RevObject o : objs) {
            assertTrue(db.put(o));
        }

        ObjectId notInDb1 = RevObjectTestSupport.hashString("fake1");
        ObjectId notInDb2 = RevObjectTestSupport.hashString("fake2");

        Function<RevObject, ObjectId> toId = p -> p.getId();
        List<ObjectId> ids = Lists.newArrayList(concat(singletonIterator(notInDb1),
                transform(objs.iterator(), toId), singletonIterator(notInDb2)));

        db.deleteAll(ids.iterator());
        for (ObjectId id : ids) {
            assertFalse(db.exists(id));
        }
    }

    @Test
    public void testDeleteAllWithListener() {
        ImmutableList<RevObject> objs = ImmutableList.of(feature(0, null, "some value"),
                feature(1, "value", new Integer(111)), feature(2, (Object) null));

        for (RevObject o : objs) {
            assertTrue(db.put(o));
        }

        ObjectId notInDb1 = RevObjectTestSupport.hashString("fake1");
        ObjectId notInDb2 = RevObjectTestSupport.hashString("fake2");

        Function<RevObject, ObjectId> toId = p -> p.getId();
        Iterator<ObjectId> ids = concat(singletonIterator(notInDb1),
                transform(objs.iterator(), toId), singletonIterator(notInDb2));

        CountingListener listener = BulkOpListener.newCountingListener();
        db.deleteAll(ids, listener);
        assertEquals(3, listener.deleted());
        assertEquals(2, listener.notFound());
    }

    @Test
    public void testExists() {

        RevFeature o = feature(0, null, "some value");
        assertFalse(db.exists(o.getId()));
        assertTrue(db.put(o));
        assertTrue(db.exists(o.getId()));
        assertTrue(db.exists(o.getId()));
    }

    @Test
    public void testGet() {
        RevFeature o = feature(0, null, "some value");

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
        RevFeature o = feature(0, null, "some value");

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

        ImmutableList<RevObject> expected = ImmutableList.of(feature(0, null, "some value"),
                feature(1, "value", new Integer(111)), feature(2, (Object) null), RevTree.EMPTY);

        for (RevObject o : expected) {
            assertTrue(db.put(o));
        }
        assertTrue(db.put(feature(5, "not queried 1")));
        assertTrue(db.put(feature(6, "not queried 2")));

        Function<RevObject, ObjectId> toId = p -> p.getId();
        Iterable<ObjectId> ids = Iterables.transform(expected, toId);

        Iterator<RevObject> iterator = db.getAll(ids);
        List<RevObject> actual = ImmutableList.copyOf(iterator);

        assertEquals(Sets.newHashSet(ids), Sets.newHashSet(Iterables.transform(actual, toId)));

    }

    @Test
    public void testGetAllWithListener() {

        ImmutableList<RevObject> expected = ImmutableList.of(feature(0, null, "some value"),
                feature(1, "value", new Integer(111)), feature(2, (Object) null), RevTree.EMPTY);

        for (RevObject o : expected) {
            assertTrue(db.put(o));
        }
        assertTrue(db.put(feature(5, "not queried 1")));
        assertTrue(db.put(feature(6, "not queried 2")));

        Function<RevObject, ObjectId> toId = p -> p.getId();
        Iterable<ObjectId> ids = Iterables.transform(expected, toId);

        CountingListener listener = BulkOpListener.newCountingListener();

        Iterable<ObjectId> notFound = ImmutableList.of(RevObjectTestSupport.hashString("notfound1"),
                RevObjectTestSupport.hashString("notfound2"));

        Iterator<RevObject> result = db.getAll(Iterables.concat(notFound, ids), listener);

        List<RevObject> actual = ImmutableList.copyOf(result);

        assertEquals(Sets.newHashSet(ids), Sets.newHashSet(Iterables.transform(actual, toId)));

        assertEquals(expected.size(), listener.found());
        assertEquals(2, listener.notFound());
    }

    @Test
    public void testGetAllOfASpecificType() {
        final RevFeature f1 = feature(0, null, "some value");
        final RevFeature f2 = feature(1, "value", new Integer(111));
        final RevFeature f3 = feature(2, (Object) null);
        final RevTree t1 = RevTree.EMPTY;
        final RevTree t2 = RevObjectTestSupport.INSTANCE.createFeaturesTree(db, "t", 10);
        final RevTree t3 = RevObjectTestSupport.INSTANCE.createFeaturesTree(db, "t", 100);

        db.putAll(ImmutableList.of(f1, f2, f3, t1, t2, t3).iterator());

        Iterable<ObjectId> queryIds = ImmutableList.of(f1.getId(), f3.getId(), t1.getId(),
                t2.getId(), t3.getId());

        CountingListener listener;

        listener = BulkOpListener.newCountingListener();
        Set<RevFeature> features = Sets.newHashSet(db.getAll(queryIds, listener, RevFeature.class));
        assertEquals(2, listener.found());
        assertEquals(3, listener.notFound());
        assertEquals(Sets.newHashSet(f1.getId(), f3.getId()),
                Sets.newHashSet(Iterables.transform(features, (f) -> f.getId())));

        listener = BulkOpListener.newCountingListener();
        Set<RevTree> trees = Sets.newHashSet(db.getAll(queryIds, listener, RevTree.class));
        assertEquals(3, listener.found());
        assertEquals(2, listener.notFound());
        assertEquals(Sets.newHashSet(t1.getId(), t2.getId(), t3.getId()),
                Sets.newHashSet(Iterables.transform(trees, (t) -> t.getId())));

        listener = BulkOpListener.newCountingListener();
        Set<RevObject> all = Sets.newHashSet(db.getAll(queryIds, listener, RevObject.class));
        assertEquals(5, listener.found());
        assertEquals(0, listener.notFound());
        assertEquals(Sets.newHashSet(f1.getId(), f3.getId(), t1.getId(), t2.getId(), t3.getId()),
                Sets.newHashSet(Iterables.transform(all, (o) -> o.getId())));

    }

    @Test
    public void testGetIfPresent() {
        ImmutableList<RevObject> expected = ImmutableList.of(feature(0, null, "some value"),
                feature(1, "value", new Integer(111)), feature(2, (Object) null), RevTree.EMPTY);

        for (RevObject o : expected) {
            assertTrue(db.put(o));
        }
        for (RevObject o : expected) {
            assertEquals(o, db.getIfPresent(o.getId()));
        }
        assertNull(db.getIfPresent(RevObjectTestSupport.hashString("notfound")));
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

        assertTrue(db.put(featureForceId(id1, "f1")));
        assertTrue(db.put(featureForceId(id2, "f2")));

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

        assertTrue(db.put(featureForceId(id1, "f1")));
        assertTrue(db.put(featureForceId(id2, "f2")));
        assertTrue(db.put(featureForceId(id3, "f3")));
        assertTrue(db.put(featureForceId(id4, "f4")));
        assertTrue(db.put(featureForceId(id5, "f5")));

        HashSet<ObjectId> matches;

        matches = Sets.newHashSet(db.lookUp("00000000"));
        assertEquals(Sets.newHashSet(), matches);

        matches = Sets.newHashSet(db.lookUp("ffffffff"));
        assertEquals(Sets.newHashSet(), matches);

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

        ImmutableList<RevObject> expected = ImmutableList.of(feature(0, null, "some value"),
                feature(1, "value", new Integer(111)), feature(2, (Object) null), RevTree.EMPTY);

        db.putAll(expected.iterator());
        for (RevObject o : expected) {
            assertEquals(o, db.get(o.getId()));
        }
    }

    @Test
    public void testPutAllWithListener() {

        ImmutableList<RevObject> expected = ImmutableList.of(feature(0, null, "some value"),
                feature(1, "value", new Integer(111)), feature(2, (Object) null), RevTree.EMPTY);

        Function<RevObject, ObjectId> toId = p -> p.getId();
        final Iterable<ObjectId> ids = Iterables.transform(expected, toId);

        final List<ObjectId> found = new CopyOnWriteArrayList<>();
        final List<ObjectId> inserted = new CopyOnWriteArrayList<>();

        BulkOpListener listener = new BulkOpListener() {
            @Override
            public void found(ObjectId object, @Nullable Integer storageSizeBytes) {
                found.add(object);
                // make sure it's in the database
                assertNotNull(db.getIfPresent(object));
            }

            @Override
            public void inserted(ObjectId object, @Nullable Integer storageSizeBytes) {
                inserted.add(object);
                // make sure it was inserted into the database
                assertNotNull(db.getIfPresent(object));
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

    @Test
    public void testGetObjects() throws Exception {
        final int numSubTrees = 512;
        final int featuresPerSubtre = 2;
        final int totalFeatures = numSubTrees * featuresPerSubtre;
        final ObjectId metadataId = hashString("fakeid");
        final RevTree tree = RevObjectTestSupport.INSTANCE.createTreesTree(db, numSubTrees,
                featuresPerSubtre, metadataId);
        final List<NodeRef> treeNodes;
        final List<NodeRef> featureNodes;
        treeNodes = Lists.newArrayList(
                new DepthTreeIterator("", metadataId, tree, db, Strategy.RECURSIVE_TREES_ONLY));
        featureNodes = Lists.newArrayList(
                new DepthTreeIterator("", metadataId, tree, db, Strategy.RECURSIVE_FEATURES_ONLY));
        // preflight checks
        assertEquals(numSubTrees, treeNodes.size());
        assertEquals(totalFeatures, featureNodes.size());
        // make sure the features do exist in the db
        {
            Iterator<RevFeature> fakeFeatures = Iterators.transform(featureNodes.iterator(),
                    (fr) -> RevObjectTestSupport.featureForceId(fr.getObjectId(),
                            fr.getObjectId().toString()));
            db.putAll(fakeFeatures);
        }

        // assertions
        {
            CountingListener listener = BulkOpListener.newCountingListener();
            AutoCloseableIterator<ObjectInfo<RevTree>> treeInfos;
            treeInfos = db.getObjects(treeNodes.iterator(), listener, RevTree.class);
            assertEquals(numSubTrees, Iterators.size(treeInfos));
            assertEquals(numSubTrees, listener.found());
            assertEquals(0, listener.notFound());
        }
        {
            CountingListener listener = BulkOpListener.newCountingListener();
            AutoCloseableIterator<ObjectInfo<RevFeature>> featureInfos;
            featureInfos = db.getObjects(treeNodes.iterator(), listener, RevFeature.class);
            assertEquals(0, Iterators.size(featureInfos));
            assertEquals(0, listener.found());
            assertEquals(numSubTrees, listener.notFound());
        }

        {
            CountingListener listener = BulkOpListener.newCountingListener();
            AutoCloseableIterator<ObjectInfo<RevFeature>> featureInfos;
            featureInfos = db.getObjects(featureNodes.iterator(), listener, RevFeature.class);
            assertEquals(totalFeatures, Iterators.size(featureInfos));
            assertEquals(totalFeatures, listener.found());
            assertEquals(0, listener.notFound());
        }
        {
            CountingListener listener = BulkOpListener.newCountingListener();
            AutoCloseableIterator<ObjectInfo<RevTree>> treeInfos;
            treeInfos = db.getObjects(featureNodes.iterator(), listener, RevTree.class);
            assertEquals(0, Iterators.size(treeInfos));
            assertEquals(0, listener.found());
            assertEquals(totalFeatures, listener.notFound());
        }
    }

    public @Test void testGetDiffObjects() throws Exception {
        SimpleFeatureType featureType = DataUtilities.createType("points",
                "sp:String,ip:Integer,pp:Point:srid=4326");
        final RevFeatureType revFeatureType = RevFeatureType.builder().type(featureType).build();
        final int leftTreeSize = 1000;
        final List<SimpleFeature> features = createFeatures(featureType, leftTreeSize);
        final List<RevFeature> revFeatures = features.stream()
                .map((f) -> RevFeature.builder().build(f)).collect(Collectors.toList());

        db.put(revFeatureType);
        db.putAll(revFeatures.iterator());

        List<SimpleFeature> rightFeatures = new ArrayList<>(features);
        List<SimpleFeature> added;
        List<SimpleFeature> removed;
        List<SimpleFeature> changedAttribute;
        List<SimpleFeature> changedGeometry;
        {
            added = new ArrayList<>();
            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
            for (int i = leftTreeSize; i < leftTreeSize + 100; i++) {
                added.add(poiFeature(builder, i));
            }
            removed = new ArrayList<>(rightFeatures.subList(200, 300));
            rightFeatures.removeAll(removed);
            rightFeatures.addAll(added);
            changedAttribute = new ArrayList<>(rightFeatures.subList(100, 200));
            for (SimpleFeature f : changedAttribute) {
                f.setAttribute("sp", f.getAttribute("sp") + "_changed");
            }
            changedGeometry = new ArrayList<>(rightFeatures.subList(400, 500));
            GeometryFactory gf = new GeometryFactory();
            for (SimpleFeature f : changedGeometry) {
                Point geom = (Point) f.getAttribute("pp");
                geom = gf.createPoint(new Coordinate(-geom.getX(), -geom.getY()));
                f.setAttribute("pp", geom);
            }
        }
        final List<RevFeature> rightRevFeatures = rightFeatures.stream()
                .map((f) -> RevFeature.builder().build(f)).collect(Collectors.toList());
        db.putAll(rightRevFeatures.iterator());

        List<DiffEntry> entries = createDiffEntries(revFeatures, rightRevFeatures);
        List<DiffObjectInfo<RevFeature>> objectDiffs;
        {
            AutoCloseableIterator<DiffObjectInfo<RevFeature>> iterator;
            iterator = db.getDiffObjects(entries.iterator(), RevFeature.class);
            assertNotNull(iterator);
            try {
                objectDiffs = Lists.newArrayList(iterator);
            } finally {
                iterator.close();
            }
        }
        assertEquals(entries.size(), objectDiffs.size());

        Set<DiffEntry> expected = new HashSet<>(entries);
        Set<DiffEntry> actual = objectDiffs.stream().map((d) -> d.entry())
                .collect(Collectors.toSet());
        assertEquals(expected, actual);
        assertDiffObjects(objectDiffs);
    }

    private void assertDiffObjects(List<DiffObjectInfo<RevFeature>> objects) {
        for (DiffObjectInfo<RevFeature> o : objects) {
            DiffEntry entry = o.entry();
            switch (entry.changeType()) {
            case ADDED:
                assertFalse(o.oldValue().isPresent());
                assertTrue(o.newValue().isPresent());
                assertEquals(entry.newObjectId(), o.newValue().get().getId());
                break;
            case MODIFIED:
                assertTrue(o.oldValue().isPresent());
                assertTrue(o.newValue().isPresent());
                assertEquals(entry.oldObjectId(), o.oldValue().get().getId());
                assertEquals(entry.newObjectId(), o.newValue().get().getId());
                break;
            case REMOVED:
                assertTrue(o.oldValue().isPresent());
                assertFalse(o.newValue().isPresent());
                assertEquals(entry.oldObjectId(), o.oldValue().get().getId());
                break;
            default:
                throw new IllegalStateException();
            }
        }
    }

    private List<DiffEntry> createDiffEntries(List<RevFeature> left, List<RevFeature> right) {
        final java.util.function.Function<RevFeature, String> fid = (f) -> String
                .valueOf(f.get(1).get());// ip
        Map<String, RevFeature> lEntries = left.stream().collect(Collectors.toMap(fid, f -> f));
        Map<String, RevFeature> rEntries = right.stream().collect(Collectors.toMap(fid, f -> f));

        MapDifference<String, RevFeature> difference = Maps.difference(lEntries, rEntries);

        List<DiffEntry> entries = new ArrayList<>();

        difference.entriesOnlyOnLeft().forEach((k, v) -> entries.add(entry(k, v, null)));
        difference.entriesOnlyOnRight().forEach((k, v) -> entries.add(entry(k, null, v)));
        difference.entriesDiffering()
                .forEach((k, v) -> entries.add(entry(k, v.leftValue(), v.rightValue())));

        return entries;
    }

    private DiffEntry entry(String name, RevFeature l, RevFeature r) {
        //@formatter:off
        Node lnode = l == null? null: RevObjectFactory.defaultInstance().createNode(name, l.getId(), ObjectId.NULL, TYPE.FEATURE, SpatialOps.boundsOf(l), null);
        Node rnode = r == null? null: RevObjectFactory.defaultInstance().createNode(name, r.getId(), ObjectId.NULL, TYPE.FEATURE, SpatialOps.boundsOf(r), null);
        //@formatter:on
        NodeRef oldObject = lnode == null ? null : NodeRef.create(NodeRef.ROOT, lnode);
        NodeRef newObject = rnode == null ? null : NodeRef.create(NodeRef.ROOT, rnode);
        return new DiffEntry(oldObject, newObject);
    }

    private List<SimpleFeature> createFeatures(SimpleFeatureType featureType, int count) {

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
        List<SimpleFeature> list = IntStream.range(0, count).mapToObj((i) -> poiFeature(builder, i))
                .collect(Collectors.toList());

        return list;
    }

    private SimpleFeature poiFeature(SimpleFeatureBuilder builder, int i) {
        builder.reset();
        builder.set("sp", "string_" + i);
        builder.set("ip", i);
        try {
            builder.set("pp",
                    new WKTReader().read(String.format("POINT(%f %f)", i / 100d, i / 100d)));

        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        SimpleFeature f = builder.buildFeature(String.valueOf(i));
        return f;
    }
}
