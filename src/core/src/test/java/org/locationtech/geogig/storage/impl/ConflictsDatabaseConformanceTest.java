/* Copyright (c) 2016 Boundless and others.
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
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.ObjectId.NULL;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public abstract class ConflictsDatabaseConformanceTest<T extends ConflictsDatabase> {

    @Rule
    public ExpectedException expected = ExpectedException.none();

    protected T conflicts;

    protected final Conflict c1 = new Conflict("Rivers/1", NULL,
            RevObjectTestSupport.hashString("ours"), RevObjectTestSupport.hashString("theirs"));

    protected final Conflict c2 = new Conflict("Rivers/2",
            RevObjectTestSupport.hashString("ancestor"), NULL,
            RevObjectTestSupport.hashString("theirs2"));

    protected final Conflict c3 = new Conflict("Rivers/3",
            RevObjectTestSupport.hashString("ancestor"), RevObjectTestSupport.hashString("ours3"),
            RevObjectTestSupport.hashString("theirs3"));

    protected final Conflict b1 = new Conflict("buildings/1", NULL,
            RevObjectTestSupport.hashString("ours"), RevObjectTestSupport.hashString("theirs"));

    protected final Conflict b2 = new Conflict("buildings/2",
            RevObjectTestSupport.hashString("ancestor"), RevObjectTestSupport.hashString("ours2"),
            NULL);

    protected final Conflict b3 = new Conflict("buildings/3",
            RevObjectTestSupport.hashString("ancestor"), RevObjectTestSupport.hashString("ours3"),
            RevObjectTestSupport.hashString("theirs3"));

    @Before
    public void before() throws Exception {
        this.conflicts = createConflictsDatabase();
    }

    @After
    public void after() throws Exception {
        dispose(conflicts);
    }

    protected abstract T createConflictsDatabase() throws Exception;

    protected abstract void dispose(@Nullable T conflicts) throws Exception;

    protected void add(String namespace, Conflict... conflicts) {
        for (Conflict c : conflicts) {
            this.conflicts.addConflict(namespace, c);
        }
    }

    protected Conflict createTestConflict(String path) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(path));
        ObjectId ancestor = RevObjectTestSupport.hashString(NodeRef.parentPath(path));
        ObjectId ours = RevObjectTestSupport.hashString(path);
        ObjectId theirs = RevObjectTestSupport.hashString(path + "1");
        Conflict c = new Conflict(path, ancestor, ours, theirs);
        return c;
    }

    protected List<Conflict> createConflicts(final String parentPath, final int size) {
        List<Conflict> l = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String path = parentPath + (parentPath.isEmpty() ? "" : "/") + i;
            l.add(createTestConflict(path));
        }
        return l;
    }

    @Test
    public void testAddGetNullNamespace() {
        add(null, c1);
        assertEquals(c1, conflicts.getConflict(null, c1.getPath()).get());

        add(null, c2, c3);

        assertEquals(c2, conflicts.getConflict(null, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(null, c3.getPath()).get());

        assertFalse(conflicts.getConflict(null, "not/a/conflict").isPresent());
    }

    @Test
    public void testAddGetNamespace() {
        final String ns = UUID.randomUUID().toString();

        add(ns, c1);
        assertEquals(c1, conflicts.getConflict(ns, c1.getPath()).get());
        assertFalse(conflicts.getConflict(null, c1.getPath()).isPresent());

        add(ns, c2, c3);

        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(ns, c3.getPath()).get());

        assertFalse(conflicts.getConflict(ns, "not/a/conflict").isPresent());
        assertFalse(conflicts.getConflict(null, c2.getPath()).isPresent());
        assertFalse(conflicts.getConflict(null, c3.getPath()).isPresent());
    }

    @Test
    public void testAddConflicts() {
        final String ns = null;
        Iterable<Conflict> list = ImmutableList.of(c1, c2, c3, b1, b2, b3);
        conflicts.addConflicts(ns, list);
        assertEquals(c1, conflicts.getConflict(ns, c1.getPath()).get());
        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(ns, c3.getPath()).get());
        assertEquals(b1, conflicts.getConflict(ns, b1.getPath()).get());
        assertEquals(b2, conflicts.getConflict(ns, b2.getPath()).get());
        assertEquals(b3, conflicts.getConflict(ns, b3.getPath()).get());
    }

    @Test
    public void testAddConflictsNS() {
        final String ns = UUID.randomUUID().toString();
        Iterable<Conflict> list = ImmutableList.of(c1, c2, c3);
        conflicts.addConflicts(ns, list);
        assertEquals(c1, conflicts.getConflict(ns, c1.getPath()).get());
        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(ns, c3.getPath()).get());
    }

    @Test
    public void testHasConflicts() {
        final String ns = UUID.randomUUID().toString();
        assertFalse(conflicts.hasConflicts(null));
        assertFalse(conflicts.hasConflicts(ns));

        add(null, c1);
        add(ns, c2, c3);
        assertTrue(conflicts.hasConflicts(null));
        assertTrue(conflicts.hasConflicts(ns));
        assertFalse(conflicts.hasConflicts(UUID.randomUUID().toString()));
    }

    @Test
    public void testGetCountByPrefix() {
        List<Conflict> rivers = createConflicts("rivers", 1555);
        List<Conflict> highways = createConflicts("roads/highways", 77);
        conflicts.addConflicts(null, rivers);
        conflicts.addConflicts("someTxId", highways);
        // create also conflict entries for the tree objects
        conflicts.addConflict(null, createTestConflict("rivers"));
        conflicts.addConflict("someTxId", createTestConflict("roads/highways"));

        assertEquals(0, conflicts.getCountByPrefix("someTxId", "rivers"));
        assertEquals(1 + rivers.size(), conflicts.getCountByPrefix(null, "rivers"));

        assertEquals(0, conflicts.getCountByPrefix(null, "highways"));
        assertEquals(1 + highways.size(), conflicts.getCountByPrefix("someTxId", "roads/highways"));
    }

    @Test
    public void testGetByPrefix() {
        List<Conflict> rivers = createConflicts("rivers", 1111);
        rivers.add(createTestConflict("rivers"));// create an entry for the tree too

        List<Conflict> highways = createConflicts("roads/highways", 1500);
        highways.add(createTestConflict("roads"));// create an entry for the tree too

        List<Conflict> buildings = createConflicts("buildings", 100);
        buildings.add(createTestConflict("buildings"));// create an entry for the tree too

        List<Conflict> pois = createConflicts("pois", 2001);
        pois.add(createTestConflict("pois"));// create an entry for the tree too

        conflicts.addConflicts(null, rivers);
        conflicts.addConflicts(null, highways);

        final String txId = UUID.randomUUID().toString();
        conflicts.addConflicts(txId, buildings);
        conflicts.addConflicts(txId, pois);

        testGetByPrefix(null, "rivers", rivers);
        testGetByPrefix(txId, "rivers", ImmutableList.of());

        testGetByPrefix(null, "roads", highways);
        testGetByPrefix(txId, "roads", ImmutableList.of());

        testGetByPrefix(txId, "buildings", buildings);
        testGetByPrefix(null, "buildings", ImmutableList.of());

        testGetByPrefix(txId, "pois", pois);
        testGetByPrefix(null, "pois", ImmutableList.of());

        List<Conflict> defaultns = new ArrayList<>(rivers);
        defaultns.addAll(highways);
        List<Conflict> txns = new ArrayList<>(buildings);
        txns.addAll(pois);

        testGetByPrefix(null, null, defaultns);
        testGetByPrefix(txId, null, txns);

    }

    private void testGetByPrefix(String namespace, String treePath, List<Conflict> expected) {

        Iterator<Conflict> it = conflicts.getByPrefix(namespace, treePath);

        Map<String, Conflict> actual = new TreeMap<>(Maps.uniqueIndex(it, (c) -> c.getPath()));

        assertEquals(expected.size(), actual.size());

        Map<String, Conflict> expectedMap = new TreeMap<>(
                Maps.uniqueIndex(expected, (c) -> c.getPath()));

        assertEquals(expectedMap.keySet(), actual.keySet());
    }

    @Test
    public void testRemoveConflict() {
        final String ns = UUID.randomUUID().toString();
        add(null, c1, c3);
        add(ns, c2, c3);

        conflicts.removeConflict(ns, c1.getPath());
        assertEquals(c1, conflicts.getConflict(null, c1.getPath()).get());
        conflicts.removeConflict(null, c1.getPath());
        assertFalse(conflicts.getConflict(null, c1.getPath()).isPresent());
        assertFalse(conflicts.getConflict(ns, c1.getPath()).isPresent());

        conflicts.removeConflict(null, c2.getPath());
        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        conflicts.removeConflict(ns, c2.getPath());
        assertFalse(conflicts.getConflict(null, c2.getPath()).isPresent());
        assertFalse(conflicts.getConflict(ns, c2.getPath()).isPresent());

        assertTrue(conflicts.hasConflicts(null));
        assertTrue(conflicts.hasConflicts(ns));
        conflicts.removeConflict(null, c3.getPath());
        assertFalse(conflicts.hasConflicts(null));
        conflicts.removeConflict(ns, c3.getPath());
        assertFalse(conflicts.hasConflicts(ns));
    }

    @Test
    public void testRemoveConflicts() {
        final String ns = UUID.randomUUID().toString();
        add(null, c1, c3);
        add(ns, c2, c3);

        assertTrue(conflicts.hasConflicts(null));
        assertTrue(conflicts.hasConflicts(ns));

        conflicts.removeConflicts(null);
        assertFalse(conflicts.hasConflicts(null));
        assertTrue(conflicts.hasConflicts(ns));

        add(null, c1);

        conflicts.removeConflicts(ns);
        assertTrue(conflicts.hasConflicts(null));
        assertFalse(conflicts.hasConflicts(ns));
    }

    @Test
    public void testRemoveConflictsIterableNS() {
        final String ns = UUID.randomUUID().toString();
        add(ns, c1, c2, c3);
        add(null, c1, c2, c3);

        conflicts.removeConflicts(ns, ImmutableList.of(c3.getPath(), c2.getPath()));
        assertTrue(conflicts.getConflict(ns, c1.getPath()).isPresent());
        assertFalse(conflicts.getConflict(ns, c2.getPath()).isPresent());
        assertFalse(conflicts.getConflict(ns, c3.getPath()).isPresent());

        assertEquals(3, conflicts.getCountByPrefix(null, null));
    }

    @Test
    public void testRemoveConflictsIterableDefaultNS() {
        final String ns = UUID.randomUUID().toString();
        add(ns, c1, c2, c3);
        add(null, c1, c2, c3);

        conflicts.removeConflicts(null, ImmutableList.of(c3.getPath(), c2.getPath()));

        assertTrue(conflicts.getConflict(null, c1.getPath()).isPresent());
        assertFalse(conflicts.getConflict(null, c2.getPath()).isPresent());
        assertFalse(conflicts.getConflict(null, c3.getPath()).isPresent());

        assertEquals(3, conflicts.getCountByPrefix(ns, null));
    }

    @Test
    public void testRemoveByPrefix() {
        final String ns = UUID.randomUUID().toString();
        add(ns, c1, c2, c3);
        add(ns, b1, b2, b3);
        add(null, c1, c2, c3);

        assertEquals(3, conflicts.getCountByPrefix(null, "Rivers"));
        assertEquals(3, conflicts.getCountByPrefix(ns, "Rivers"));

        conflicts.removeByPrefix(null, c2.getPath());
        assertEquals(2, conflicts.getCountByPrefix(null, "Rivers"));
        assertEquals(3, conflicts.getCountByPrefix(ns, "Rivers"));
        assertFalse(conflicts.getConflict(null, c2.getPath()).isPresent());

        assertEquals(3, conflicts.getCountByPrefix(ns, "buildings"));
        conflicts.removeByPrefix(ns, "buildings");
        assertEquals(0, conflicts.getCountByPrefix(ns, "buildings"));
    }

    @Test
    public void testFindConflicts() {
        final String ns = UUID.randomUUID().toString();
        add(ns, c1, c2, c3);
        add(ns, b1, b2, b3);
        add(null, c1, c2, c3);

        assertTrue(conflicts
                .findConflicts(null, Sets.newHashSet(b1.getPath(), b2.getPath(), b3.getPath()))
                .isEmpty());

        assertEquals(Sets.newHashSet(b2.getPath(), b3.getPath()),
                conflicts.findConflicts(ns, Sets.newHashSet(b2.getPath(), b3.getPath())));

    }
}
