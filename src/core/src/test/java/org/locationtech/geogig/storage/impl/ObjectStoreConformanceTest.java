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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.feature;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.featureForceId;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.hashString;
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureTypes;
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;

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
        checkClosed(() -> db.getAll(Collections.emptyList()));
        checkClosed(() -> db.getAll(Collections.emptyList(), NOOP_LISTENER));
        checkClosed(() -> db.getAll(Collections.emptyList(), NOOP_LISTENER, RevTree.class));
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
        checkNullArgument(() -> db.getAll(Collections.emptyList(), NOOP_LISTENER, null));
        checkNullArgument(() -> db.getAll(Collections.emptyList(), null));
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
        Exception e = assertThrows(IllegalStateException.class, op::run);
        assertThat(e.getMessage(), containsString("closed"));
    }

    private void checkNullArgument(Runnable op) {
        Exception e = assertThrows(NullPointerException.class, op::run);
        assertThat(e.getMessage(), containsString("is null"));
    }

    @Test
    public void testDelete() {
        assertTrue(db.put(RevTree.EMPTY));
        db.delete(RevTree.EMPTY_TREE_ID);
        assertFalse(db.exists(RevTree.EMPTY_TREE_ID));
    }

    @Test
    public void testDeleteAll() {
        List<RevObject> objs = Arrays.asList(feature(0, null, "some value"),
                feature(1, "value", Integer.valueOf(111)), feature(2, (Object) null));

        for (RevObject o : objs) {
            assertTrue(db.put(o));
        }

        ObjectId notInDb1 = RevObjectTestSupport.hashString("fake1");
        ObjectId notInDb2 = RevObjectTestSupport.hashString("fake2");

        List<ObjectId> ids = Lists.newArrayList(concat(singletonIterator(notInDb1),
                transform(objs.iterator(), RevObject::getId), singletonIterator(notInDb2)));

        db.deleteAll(ids.iterator());
        for (ObjectId id : ids) {
            assertFalse(db.exists(id));
        }
    }

    @Test
    public void testDeleteAllWithListener() {
        List<RevObject> objs = Arrays.asList(feature(0, null, "some value"),
                feature(1, "value", Integer.valueOf(111)), feature(2, (Object) null));

        for (RevObject o : objs) {
            assertTrue(db.put(o));
        }

        ObjectId notInDb1 = RevObjectTestSupport.hashString("fake1");
        ObjectId notInDb2 = RevObjectTestSupport.hashString("fake2");

        Iterator<ObjectId> ids = concat(singletonIterator(notInDb1),
                transform(objs.iterator(), RevObject::getId), singletonIterator(notInDb2));

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

        List<RevObject> expected = Arrays.asList(feature(0, null, "some value"),
                feature(1, "value", Integer.valueOf(111)), feature(2, (Object) null),
                RevTree.EMPTY);

        for (RevObject o : expected) {
            assertTrue(db.put(o));
        }
        assertTrue(db.put(feature(5, "not queried 1")));
        assertTrue(db.put(feature(6, "not queried 2")));

        Set<ObjectId> ids = expected.stream().map(RevObject::getId).collect(Collectors.toSet());

        Iterator<RevObject> iterator = db.getAll(ids);
        List<RevObject> actual = Streams.stream(iterator).collect(Collectors.toList());

        assertEquals(ids, actual.stream().map(RevObject::getId).collect(Collectors.toSet()));

    }

    @Test
    public void testGetAllWithListener() {

        List<RevObject> expected = Arrays.asList(feature(0, null, "some value"),
                feature(1, "value", Integer.valueOf(111)), feature(2, (Object) null),
                RevTree.EMPTY);

        for (RevObject o : expected) {
            assertTrue(db.put(o));
        }
        assertTrue(db.put(feature(5, "not queried 1")));
        assertTrue(db.put(feature(6, "not queried 2")));

        Set<ObjectId> ids = expected.stream().map(RevObject::getId).collect(Collectors.toSet());

        CountingListener listener = BulkOpListener.newCountingListener();

        Iterable<ObjectId> notFound = Arrays.asList(RevObjectTestSupport.hashString("notfound1"),
                RevObjectTestSupport.hashString("notfound2"));

        Iterator<RevObject> result = db.getAll(Iterables.concat(notFound, ids), listener);

        Set<ObjectId> actual = Streams.stream(result).map(RevObject::getId)
                .collect(Collectors.toSet());

        assertEquals(ids, actual);

        assertEquals(expected.size(), listener.found());
        assertEquals(2, listener.notFound());
    }

    @Test
    public void testGetAllOfASpecificType() {
        final RevFeature f1 = feature(0, null, "some value");
        final RevFeature f2 = feature(1, "value", Integer.valueOf(111));
        final RevFeature f3 = feature(2, (Object) null);
        final RevTree t1 = RevTree.EMPTY;
        final RevTree t2 = RevObjectTestSupport.INSTANCE.createFeaturesTree(db, "t", 10);
        final RevTree t3 = RevObjectTestSupport.INSTANCE.createFeaturesTree(db, "t", 100);

        db.putAll(Arrays.asList(f1, f2, f3, t1, t2, t3).iterator());

        Iterable<ObjectId> queryIds = Arrays.asList(f1.getId(), f3.getId(), t1.getId(), t2.getId(),
                t3.getId());

        CountingListener listener;

        listener = BulkOpListener.newCountingListener();
        Set<RevFeature> features = Sets.newHashSet(db.getAll(queryIds, listener, RevFeature.class));
        assertEquals(2, listener.found());
        assertEquals(3, listener.notFound());
        assertEquals(Set.of(f1.getId(), f3.getId()),
                features.stream().map(RevFeature::getId).collect(Collectors.toSet()));

        listener = BulkOpListener.newCountingListener();
        Set<RevTree> trees = Sets.newHashSet(db.getAll(queryIds, listener, RevTree.class));
        assertEquals(3, listener.found());
        assertEquals(2, listener.notFound());
        assertEquals(Set.of(t1.getId(), t2.getId(), t3.getId()),
                trees.stream().map(RevTree::getId).collect(Collectors.toSet()));

        listener = BulkOpListener.newCountingListener();
        Set<RevObject> all = Sets.newHashSet(db.getAll(queryIds, listener, RevObject.class));
        assertEquals(5, listener.found());
        assertEquals(0, listener.notFound());
        assertEquals(Set.of(f1.getId(), f3.getId(), t1.getId(), t2.getId(), t3.getId()),
                all.stream().map(RevObject::getId).collect(Collectors.toSet()));

    }

    @Test
    public void testGetIfPresent() {
        List<RevObject> expected = Arrays.asList(feature(0, null, "some value"),
                feature(1, "value", Integer.valueOf(111)), feature(2, (Object) null),
                RevTree.EMPTY);

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

        Set<ObjectId> matches;

        matches = db.lookUp("00000000").stream().collect(Collectors.toSet());
        assertEquals(Set.of(), matches);

        matches = db.lookUp("ffffffff").stream().collect(Collectors.toSet());
        assertEquals(Set.of(), matches);

        matches = db.lookUp("01234567").stream().collect(Collectors.toSet());
        assertEquals(Set.of(id1, id2, id3, id4, id5), matches);

        matches = db.lookUp("012345678").stream().collect(Collectors.toSet());
        assertEquals(Set.of(id2, id3, id4, id5), matches);

        matches = db.lookUp("0123456789").stream().collect(Collectors.toSet());
        assertEquals(Set.of(id3, id4, id5), matches);

        matches = db.lookUp("0123456789a").stream().collect(Collectors.toSet());
        assertEquals(Set.of(id4, id5), matches);

        matches = db.lookUp("0123456789ab").stream().collect(Collectors.toSet());
        assertEquals(Set.of(id5), matches);

        matches = db.lookUp("0123456789abc").stream().collect(Collectors.toSet());
        assertEquals(Set.of(), matches);
    }

    @Test
    public void testPut() {
        assertTrue(db.put(RevTree.EMPTY));
        assertFalse(db.put(RevTree.EMPTY));
    }

    @Test
    public void testPutAll() {

        List<RevObject> expected = Arrays.asList(feature(0, null, "some value"),
                feature(1, "value", Integer.valueOf(111)), feature(2, (Object) null),
                RevTree.EMPTY);

        db.putAll(expected.iterator());
        for (RevObject o : expected) {
            assertEquals(o, db.get(o.getId()));
        }
    }

    @Test
    public void testPutAllWithListener() {

        List<RevObject> expected = Arrays.asList(feature(0, null, "some value"),
                feature(1, "value", Integer.valueOf(111)), feature(2, (Object) null),
                RevTree.EMPTY);

        final Set<ObjectId> ids = expected.stream().map(RevObject::getId)
                .collect(Collectors.toSet());

        final List<ObjectId> found = new CopyOnWriteArrayList<>();
        final List<ObjectId> inserted = new CopyOnWriteArrayList<>();

        BulkOpListener listener = new BulkOpListener() {
            public @Override void found(ObjectId object, @Nullable Integer storageSizeBytes) {
                found.add(object);
                // make sure it's in the database
                assertNotNull(db.getIfPresent(object));
            }

            public @Override void inserted(ObjectId object, @Nullable Integer storageSizeBytes) {
                inserted.add(object);
                // make sure it was inserted into the database
                assertNotNull(db.getIfPresent(object));
            }

        };

        db.putAll(expected.iterator(), listener);

        assertTrue(found.toString(), found.isEmpty());
        assertEquals(ids, inserted.stream().collect(Collectors.toSet()));

        found.clear();
        inserted.clear();

        db.putAll(expected.iterator(), listener);

        assertTrue(inserted.toString(), inserted.isEmpty());
        assertEquals(ids, found.stream().collect(Collectors.toSet()));
    }

    @SuppressWarnings("resource")
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
        treeNodes = new DepthTreeIterator("", metadataId, tree, db, Strategy.RECURSIVE_TREES_ONLY)
                .toList();
        featureNodes = new DepthTreeIterator("", metadataId, tree, db,
                Strategy.RECURSIVE_FEATURES_ONLY).toList();
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
        FeatureType featureType = FeatureTypes.createType("points", "sp:String", "ip:Integer",
                "pp:Point:srid=4326");
        final RevFeatureType revFeatureType = RevFeatureType.builder().type(featureType).build();
        final int leftTreeSize = 1000;
        final List<Feature> features = createFeatures(featureType, leftTreeSize);
        final List<RevFeature> revFeatures = features.stream()
                .map((f) -> RevFeature.builder().build(f)).collect(Collectors.toList());

        db.put(revFeatureType);
        db.putAll(revFeatures.iterator());

        List<Feature> rightFeatures = new ArrayList<>(features);
        List<Feature> added;
        List<Feature> removed;
        List<Feature> changedAttribute;
        List<Feature> changedGeometry;
        {
            added = new ArrayList<>();
            for (int i = leftTreeSize; i < leftTreeSize + 100; i++) {
                added.add(poiFeature(featureType, i));
            }
            removed = new ArrayList<>(rightFeatures.subList(200, 300));
            rightFeatures.removeAll(removed);
            rightFeatures.addAll(added);
            changedAttribute = new ArrayList<>(rightFeatures.subList(100, 200));
            for (Feature f : changedAttribute) {
                f.setAttribute("sp", f.getAttribute("sp") + "_changed");
            }
            changedGeometry = new ArrayList<>(rightFeatures.subList(400, 500));
            GeometryFactory gf = new GeometryFactory();
            for (Feature f : changedGeometry) {
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
            objectDiffs = iterator.toList();
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

    private List<Feature> createFeatures(FeatureType featureType, int count) {

        return IntStream.range(0, count).mapToObj((i) -> poiFeature(featureType, i))
                .collect(Collectors.toList());
    }

    private Feature poiFeature(FeatureType featureType, int i) {
        Feature f = Feature.build(String.valueOf(i), featureType);
        f.setAttribute("sp", "string_" + i);
        f.setAttribute("ip", i);
        try {
            f.setAttribute("pp",
                    new WKTReader().read(String.format("POINT(%f %f)", i / 100d, i / 100d)));

        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return f;
    }
}
