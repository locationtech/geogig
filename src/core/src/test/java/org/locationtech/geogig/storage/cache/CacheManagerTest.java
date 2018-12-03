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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.lang.management.ManagementFactory;

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.storage.RevObjectSerializer;

import lombok.Getter;
import lombok.Setter;

public class CacheManagerTest {

    @Rule
    public ExpectedException ex = ExpectedException.none();

    private CacheManager cacheManager;

    public @Before void before() {
        System.setProperty(CacheManager.ENV_VAR,
                CacheManagerTest.class.getName() + "$TestCacheBuilder");
        this.cacheManager = spy(new CacheManager());
    }

    public @After void after() {
        cacheManager.clear();
        System.setProperty(CacheManager.ENV_VAR, "");
    }

    public static class TestCacheBuilder implements SharedCacheBuilder {
        private @Getter int priority = 0;

        private @Getter @Setter long maxSizeBytes;

        public @Override SharedCache build() {
            // TODO Auto-generated method stub
            return new TestCache();
        }

        public static class TestCache implements SharedCache {
            public @Override void setEncoder(RevObjectSerializer encoder) {
                // do nothing
            }
        }
    }

    public @Test void testMBean() throws Exception {
        MBeanServer mbeanserver = ManagementFactory.getPlatformMBeanServer();
        // force class loading to force mbean regisration

        ObjectName beanName = new ObjectName("org.geogig:type=shared-cache");
        assertTrue(mbeanserver.isRegistered(beanName));
        MBeanInfo mBeanInfo = mbeanserver.getMBeanInfo(beanName);
        assertNotNull(mBeanInfo);
        assertEquals(CacheManager.class.getName(), mBeanInfo.getClassName());
    }

    public @Test void setMaximumSize() {
        assertEquals(-1, cacheManager.getMaximumSize());
        assertNull(cacheManager._SHARED_CACHE);
        cacheManager.setMaximumSize(0);
        assertNotNull(cacheManager._SHARED_CACHE);
        assertEquals(0, cacheManager.getMaximumSize());

        SharedCache cache = cacheManager._SHARED_CACHE;
        cacheManager.setMaximumSize(1000);
        assertEquals(1000, cacheManager.getMaximumSize());
        assertNotNull(cacheManager._SHARED_CACHE);
        assertNotSame(cache, cacheManager._SHARED_CACHE);

        doReturn(10_000L).when(cacheManager).getAbsoluteMaximumSize();
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("Cache max size must be between 0 and");
        cacheManager.setMaximumSize(10_001);
    }

    public @Test void getCacheSizePercentNegative() {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("between zero and 90%");
        cacheManager.getCacheSizePercent(-1);
    }

    public @Test void getCacheSizePercentExceedsMaximumAllowed() {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("between zero and 90%");
        cacheManager.getCacheSizePercent(0.91);
    }

    public @Test void getCacheSizePercent() {
        final long maxHeapSize = 1024 * 1024;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();

        assertEquals(0L, cacheManager.getCacheSizePercent(0));
        assertEquals(maxHeapSize / 2, cacheManager.getCacheSizePercent(0.5));
    }

    public @Test void parseCacheSizeArgumentNull() {
        assertEquals(-1L, cacheManager.parseCacheSizeArgument(null));
    }

    public @Test void parseCacheSizeArgumentInvalidArg() {
        assertIAE(() -> cacheManager.parseCacheSizeArgument("1GB"));
        assertIAE(() -> cacheManager.parseCacheSizeArgument("G1"));
        assertIAE(() -> cacheManager.parseCacheSizeArgument("anotherInvalidValue"));
        assertIAE(() -> cacheManager.parseCacheSizeArgument("K"));
        assertIAE(() -> cacheManager.parseCacheSizeArgument("NaN"));
        assertIAE(() -> cacheManager.parseCacheSizeArgument("-1"));
        assertIAE(() -> cacheManager.parseCacheSizeArgument("-0.000001K"));
    }

    private void assertIAE(Runnable op) {
        try {
            op.run();
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    public @Test void parseCacheSizeValidPercent() {
        final long maxHeapSize = 1000;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();

        assertEquals(0L, cacheManager.parseCacheSizeArgument("0"));
        assertEquals(100L, cacheManager.parseCacheSizeArgument("0.1"));
        assertEquals(500L, cacheManager.parseCacheSizeArgument(".5"));
        assertEquals(900L, cacheManager.parseCacheSizeArgument("0.9"));
    }

    public @Test void parseCacheSizeArgumentInvalidPercent() {
        assertIAE(() -> cacheManager.parseCacheSizeArgument("0.900001"));
        assertIAE(() -> cacheManager.parseCacheSizeArgument("0.91"));
        assertIAE(() -> cacheManager.parseCacheSizeArgument("0.99"));
        assertIAE(() -> cacheManager.parseCacheSizeArgument("-0.1"));
    }

    public @Test void parseCacheSizeArgumentBytes() {
        assertEquals(0L, cacheManager.parseCacheSizeArgument("0"));
        assertEquals(1024L, cacheManager.parseCacheSizeArgument("1024"));
        assertEquals(0L, cacheManager.parseCacheSizeArgument("0b"));
        assertEquals(1024L, cacheManager.parseCacheSizeArgument("1024B"));
    }

    public @Test void parseCacheSizeArgumentK() {
        assertEquals(1024, cacheManager.parseCacheSizeArgument("1K"));
        assertEquals(4096, cacheManager.parseCacheSizeArgument("4k"));
        assertEquals(1024 + 512, cacheManager.parseCacheSizeArgument("1.5K"));
    }

    public @Test void parseCacheSizeArgumentM() {
        assertEquals(1024L * 1024, cacheManager.parseCacheSizeArgument("1M"));
        assertEquals(1024L * 4096, cacheManager.parseCacheSizeArgument("4M"));
        assertEquals(1024L * (1024 + 512), cacheManager.parseCacheSizeArgument("1.5m"));
    }

    public @Test void parseCacheSizeArgumentG() {
        assertEquals(1024L * 1024 * 1024, cacheManager.parseCacheSizeArgument("1g"));
        assertEquals(1024L * 1024 * 4096, cacheManager.parseCacheSizeArgument("4G"));
        assertEquals(1024L * 1024 * (1024 + 512), cacheManager.parseCacheSizeArgument("1.5g"));
    }

    public @Test void sharedCacheLazilyCreated() {
        assertNull(cacheManager._SHARED_CACHE);
        SharedCache sc = cacheManager.sharedCache();
        assertNotNull(sc);
        assertNotNull(cacheManager._SHARED_CACHE);
        assertSame(sc, cacheManager._SHARED_CACHE);
        assertSame(sc, cacheManager.sharedCache());
    }

    public @Test void resolveDefaultMaxSize() {
        doReturn(null).when(cacheManager).getMaximumSizeSystemProperty();
        doReturn(null).when(cacheManager).getMaximumSizeEnvVariable();
        assertDefaultMaxSizeDefaultValue();
    }

    private void assertDefaultMaxSizeDefaultValue() {
        final long maxHeapSize = 10_000;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();
        double expected = maxHeapSize * CacheManagerBean.DEFAULT_CACHE_SIZE_PERCENT;
        assertEquals(expected, cacheManager.resolveDefaultMaxSize(), 1e-9);
    }

    public @Test void resolveDefaultMaxSizeSystemProperty() {
        doReturn("1M").when(cacheManager).getMaximumSizeSystemProperty();
        assertEquals(Math.pow(1024, 2), cacheManager.resolveDefaultMaxSize(), 1e-9);

        doReturn("100K").when(cacheManager).getMaximumSizeSystemProperty();
        assertEquals(100 * 1024, cacheManager.resolveDefaultMaxSize(), 1e-9);

        final long maxHeapSize = 10_000;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();

        doReturn("0.5").when(cacheManager).getMaximumSizeSystemProperty();
        assertEquals(5000, cacheManager.resolveDefaultMaxSize(), 1e-9);
    }

    public @Test void resolveDefaultMaxSizeSystemPropertyInvalidValue() {
        doReturn("-1G").when(cacheManager).getMaximumSizeSystemProperty();
        assertDefaultMaxSizeDefaultValue();
    }

    public @Test void resolveDefaultMaxSizeEnvironmantVariable() {
        doReturn("").when(cacheManager).getMaximumSizeSystemProperty();

        doReturn("1M").when(cacheManager).getMaximumSizeEnvVariable();
        assertEquals(Math.pow(1024, 2), cacheManager.resolveDefaultMaxSize(), 1e-9);

        doReturn("100K").when(cacheManager).getMaximumSizeEnvVariable();
        assertEquals(100 * 1024, cacheManager.resolveDefaultMaxSize(), 1e-9);

        final long maxHeapSize = 10_000;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();

        doReturn("0.5").when(cacheManager).getMaximumSizeEnvVariable();
        assertEquals(5000, cacheManager.resolveDefaultMaxSize(), 1e-9);
    }

    public @Test void resolveDefaultMaxSizeEnvironmantVariableInvalidValue() {
        doReturn("").when(cacheManager).getMaximumSizeSystemProperty();
        doReturn("-1G").when(cacheManager).getMaximumSizeEnvVariable();
        assertDefaultMaxSizeDefaultValue();
    }

    public @Test void resolveDefaultMaxSizeInvalidSysPropFallsBackToEnvVariable() {
        doReturn("-1G").when(cacheManager).getMaximumSizeSystemProperty();
        doReturn("1024").when(cacheManager).getMaximumSizeEnvVariable();
        assertEquals(1024, cacheManager.resolveDefaultMaxSize(), 1e-9);
    }

    public @Test void resolveDefaultMaxSizeInvalidSysPropAndEnvVarFallsBackToDefaultPercent() {
        doReturn("0.91").when(cacheManager).getMaximumSizeSystemProperty();
        doReturn("alsoInvalid").when(cacheManager).getMaximumSizeEnvVariable();

        assertDefaultMaxSizeDefaultValue();
    }

    public @Test void resolveDefaultMaxSizeInvalidTooBigArgumentFallsBackToDefaultPercent() {
        final long maxHeapSize = 1024;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();

        doReturn("1025").when(cacheManager).getMaximumSizeSystemProperty();
        doReturn("1.5K").when(cacheManager).getMaximumSizeEnvVariable();

        double expected = maxHeapSize * CacheManagerBean.DEFAULT_CACHE_SIZE_PERCENT;
        assertEquals(expected, cacheManager.resolveDefaultMaxSize(), 1e-9);
    }

    public @Test void getAbsoluteMaximumSizeMB() {
        final long maxHeapSize = 10 * 1024 * 1024;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();
        assertEquals("AbsoluteMaximumSizeMB should be 90% of max heap size", 9,
                cacheManager.getAbsoluteMaximumSizeMB(), 1e-9);
    }

    public @Test void getDefaultSizeMB() {
        final long maxHeapSize = 10 * 1024 * 1024;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();

        doReturn("").when(cacheManager).getMaximumSizeSystemProperty();
        doReturn("").when(cacheManager).getMaximumSizeEnvVariable();
        double expected = 10 * CacheManagerBean.DEFAULT_CACHE_SIZE_PERCENT;
        assertEquals(expected, cacheManager.getDefaultSizeMB(), 1e-9);
    }

    public @Test void getMaximumSizePercent() {
        final long maxHeapSize = 10_000;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();

        cacheManager.setMaximumSize(5_000);
        assertEquals(0.5d, cacheManager.getMaximumSizePercent(), 1e-9);
    }

    public @Test void getMaximumSizeMB() {
        final long maxSize = 10 * 1024 * 1024;
        cacheManager.setMaximumSize(maxSize);
        assertEquals(10, cacheManager.getMaximumSizeMB(), 1e-9);
    }

    public @Test void setMaximumSizeMB() {
        final long maxSize = 10 * 1024 * 1024;
        cacheManager.setMaximumSizeMB(10);
        assertEquals(maxSize, cacheManager.getMaximumSize(), 1e-9);
    }

    public @Test void setMaximumSizePercent() {
        final long maxHeapSize = 10 * 1024 * 1024;
        doReturn(maxHeapSize).when(cacheManager).getMaximumHeapSize();

        cacheManager.setMaximumSizePercent(0.0);
        assertEquals(0, cacheManager.getMaximumSize(), 1e-9);

        cacheManager.setMaximumSizePercent(0.1);
        assertEquals(maxHeapSize * 0.1, cacheManager.getMaximumSize(), 1e-9);

        cacheManager.setMaximumSizePercent(0.9);
        assertEquals(maxHeapSize * 0.9, cacheManager.getMaximumSize(), 1e-9);

        ex.expect(IllegalArgumentException.class);
        cacheManager.setMaximumSizePercent(0.91);
    }

    public @Test void acquire() {

        ObjectCache cache1 = cacheManager.acquire("id1");
        assertNotNull(cache1);

        ObjectCache cache2 = cacheManager.acquire("id1");
        assertSame(cache1, cache2);

        ObjectCache cache3 = cacheManager.acquire("id2");
        assertNotNull(cache3);
        assertNotSame(cache1, cache3);

    }

    public @Test void release() {
        ObjectCache cache1 = cacheManager.acquire("id1");
        ObjectCache cache2 = cacheManager.acquire("id1");
        ObjectCache cache3 = cacheManager.acquire("id2");

        assertNotNull(cache1);
        assertSame(cache1, cache2);
        assertNotNull(cache3);
        assertNotSame(cache1, cache3);

        cacheManager.release(cache1);
        cacheManager.release(cache2);

        ObjectCache cache4 = cacheManager.acquire("id1");
        assertNotNull(cache4);
        assertNotSame(cache1, cache4);

        ObjectCache cache5 = cacheManager.acquire("id2");
        assertSame(cache3, cache5);
    }

    public @Test void testStats() {
        SharedCache sharedCache = mock(SharedCache.class);
        CacheStats stats = mock(CacheStats.class);
        when(stats.evictionCount()).thenReturn(1L);
        when(stats.missCount()).thenReturn(1L);
        when(stats.hitCount()).thenReturn(1L);
        when(stats.missRate()).thenReturn(0.5);
        when(stats.hitRate()).thenReturn(0.5);

        when(sharedCache.getStats()).thenReturn(stats);
        when(sharedCache.sizeBytes()).thenReturn(1000L);
        when(sharedCache.objectCount()).thenReturn(100L);
        cacheManager._SHARED_CACHE = sharedCache;

        assertEquals(1, cacheManager.getEvictionCount());
        assertEquals(1, cacheManager.getMissCount());
        assertEquals(0.5d, cacheManager.getMissRate(), 1e-9);
        assertEquals(1, cacheManager.getHitCount());
        assertEquals(0.5d, cacheManager.getHitRate(), 1e-9);
        assertEquals(1000, cacheManager.getSizeBytes());
        assertEquals(100, cacheManager.getSize());
        assertEquals(1000 / (1024D * 1024D), cacheManager.getSizeMB(), 1e-9);
    }
}
