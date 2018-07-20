/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;

public class SharedCacheTest {

    private final long maxCacheSizeBytes = 1024 * 1024;

    private SharedCache cache;

    private CacheIdentifier repo1Id, repo2Id;

    private ObjectStore store;

    @Rule
    public ExpectedException ex = ExpectedException.none();

    private RevTree obj;

    public @Before void before() {
        cache = spy(SharedCache.build(maxCacheSizeBytes));
        repo1Id = new CacheIdentifier(1);
        repo2Id = new CacheIdentifier(1000);
        store = new HeapObjectStore();
        store.open();
        obj = RevObjectTestSupport.INSTANCE.createFeaturesTree(store, "f-", 10);
    }

    public @After void after() {
        store.close();
    }

    public @Test void testBuildPrecondition() {
        ex.expect(IllegalArgumentException.class);
        SharedCache.build(-1L);
    }

    public @Test void testCacheDisabled() {
        SharedCache cache = SharedCache.build(0L);
        Key k1 = repo1Id.create(obj.getId());
        cache.put(k1, obj);
        assertFalse(cache.contains(k1));
        assertEquals(0L, cache.sizeBytes());
    }

    public @Test void testPutIfAbsent() {
        Key k1 = repo1Id.create(obj.getId());
        assertNotNull(cache.put(k1, obj));
        assertNull(cache.put(k1, obj));
    }

    public @Test void testKeyPrefix() {
        Key k1 = repo1Id.create(obj.getId());
        Key k2 = repo2Id.create(obj.getId());

        cache.put(k1, obj);
        assertTrue(cache.contains(k1));
        assertFalse(cache.contains(k2));
    }

    public @Test void testGetIfPresentImmediately() {
        Key k1 = repo1Id.create(obj.getId());
        assertNull(cache.getIfPresent(k1));
        assertNotNull(cache.put(k1, obj));

        RevObject cached = cache.getIfPresent(k1);
        assertNotNull(cached);
        assertNull(cache.getIfPresent(repo2Id.create(obj.getId())));
        assertEquals(obj, cached);
    }

    public @Test void testGetIfPresentEnsureL2Cache() throws Exception {
        Key k1 = repo1Id.create(obj.getId());
        assertNull(cache.getIfPresent(k1));

        Future<?> l2Future = cache.put(k1, obj);
        assertNotNull(l2Future);
        l2Future.get();

        RevObject cached = cache.getIfPresent(k1);
        assertNotNull(cached);
        assertNull(cache.getIfPresent(repo2Id.create(obj.getId())));
        assertEquals(obj, cached);
    }

    public @Test void testL1WriteBack() {
        final int L1Capacity = 1000;
        SharedCache cache = SharedCache.build(L1Capacity, maxCacheSizeBytes);

        List<RevObject> objects = createObjects(100);

        objects.forEach((o) -> cache.put(repo1Id.create(o.getId()), o));

        objects.forEach((o) -> assertNull(cache.getIfPresent(repo2Id.create(o.getId()))));
        objects.forEach((o) -> assertNotNull(cache.getIfPresent(repo1Id.create(o.getId()))));
    }

    public @Test void testInvalidateAllForPrefix() {
        final int L1Capacity = 10;
        SharedCache cache = SharedCache.build(L1Capacity, 32 * 1024 * 1024);

        List<RevObject> objects = createObjects(100);

        objects.forEach((o) -> cache.put(repo1Id.create(o.getId()), o));
        objects.forEach((o) -> cache.put(repo2Id.create(o.getId()), o));

        objects.forEach((o) -> assertNotNull(cache.getIfPresent(repo1Id.create(o.getId()))));
        objects.forEach((o) -> assertNotNull(cache.getIfPresent(repo2Id.create(o.getId()))));

        cache.invalidateAll(repo2Id);

        objects.forEach((o) -> assertNull(cache.getIfPresent(repo2Id.create(o.getId()))));
        objects.forEach((o) -> assertNotNull(cache.getIfPresent(repo1Id.create(o.getId()))));

        cache.invalidateAll(repo1Id);
        objects.forEach((o) -> assertNull(cache.getIfPresent(repo1Id.create(o.getId()))));
    }

    public @Test void testInvalidateAll() {
        final int L1Capacity = 10;
        SharedCache cache = SharedCache.build(L1Capacity, 32 * 1024 * 1024);

        List<RevObject> objects = createObjects(500);

        objects.forEach((o) -> cache.put(repo1Id.create(o.getId()), o));
        objects.forEach((o) -> cache.put(repo2Id.create(o.getId()), o));

        objects.forEach((o) -> assertNotNull(cache.getIfPresent(repo1Id.create(o.getId()))));
        objects.forEach((o) -> assertNotNull(cache.getIfPresent(repo2Id.create(o.getId()))));

        cache.invalidateAll();

        objects.forEach((o) -> assertNull(cache.getIfPresent(repo2Id.create(o.getId()))));
        objects.forEach((o) -> assertNull(cache.getIfPresent(repo1Id.create(o.getId()))));
    }

    private List<RevObject> createObjects(int count) {
        List<RevObject> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(RevObjectTestSupport.INSTANCE.createFeaturesTree(store, "f", i));
        }
        return list;
    }
}
