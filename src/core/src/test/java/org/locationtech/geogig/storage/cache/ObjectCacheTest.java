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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject;

public class ObjectCacheTest {

    private SharedCache mockSharedCache;

    private CacheIdentifier cacheId1, cacheId2;

    private ObjectCache cache1, cache2;

    private RevObject o1, o2;

    private CacheKey k11;

    private CacheKey k21, k22;

    public @Before void before() {
        mockSharedCache = mock(SharedCache.class);
        cacheId1 = new CacheIdentifier(0);
        cacheId2 = new CacheIdentifier(1000);
        cache1 = new ObjectCache(() -> mockSharedCache, cacheId1);
        cache2 = new ObjectCache(() -> mockSharedCache, cacheId2);
        o1 = RevFeature.builder().addValue(0L).addValue("zero").build();
        o2 = RevFeature.builder().addValue(1L).addValue("one").build();

        k11 = cacheId1.create(o1.getId());

        k21 = cacheId2.create(o1.getId());
        k22 = cacheId2.create(o2.getId());
    }

    public @Test void testContains() {
        when(mockSharedCache.contains(eq(k11))).thenReturn(false);
        when(mockSharedCache.contains(eq(k21))).thenReturn(true);

        assertFalse(cache1.contains(o1.getId()));
        assertTrue(cache2.contains(o1.getId()));
    }

    public @Test void testInvalidateAll() {
        cache1.invalidateAll();
        verify(mockSharedCache, times(1)).invalidateAll(eq(cacheId1));
    }

    public @Test void testInvalidate() {
        cache2.invalidate(o2.getId());
        cache1.invalidate(o1.getId());

        verify(mockSharedCache, times(1)).invalidate(eq(k22));
        verify(mockSharedCache, times(1)).invalidate(eq(k11));
        verifyNoMoreInteractions(mockSharedCache);
    }

    public @Test void testPut() {
        cache1.put(o1);
        cache2.put(o2);

        verify(mockSharedCache, times(1)).put(eq(k11), same(o1));
        verify(mockSharedCache, times(1)).put(eq(k22), same(o2));
        verifyNoMoreInteractions(mockSharedCache);
    }

    public @Test void testGetIfPresent() {
        cache1.getIfPresent(o1.getId());
        cache2.getIfPresent(o2.getId());

        verify(mockSharedCache, times(1)).getIfPresent(eq(k11));
        verify(mockSharedCache, times(1)).getIfPresent(eq(k22));
        verifyNoMoreInteractions(mockSharedCache);
    }

    public @Test void testUsesSupplier() {
        SharedCache sharedCacheOld = this.mockSharedCache;
        SharedCache sharedCacheNew = mock(SharedCache.class);

        cache1.getIfPresent(o1.getId());
        this.mockSharedCache = sharedCacheNew;
        cache1.getIfPresent(o1.getId());

        verify(sharedCacheOld, times(1)).getIfPresent(eq(k11));
        verify(sharedCacheNew, times(1)).getIfPresent(eq(k11));
        verifyNoMoreInteractions(mockSharedCache);
    }
}
