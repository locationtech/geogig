/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.coordcache;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.osm.internal.OSMCoordinateSequence;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;

public abstract class PointCacheTest extends Assert {

    private PointCache cache;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void before() throws IOException {
        tmpFolder.newFolder(".geogig");
        TestPlatform platform = new TestPlatform(tmpFolder.getRoot());
        platform.setUserHome(tmpFolder.newFolder("fakeHome"));
        cache = createCache(platform);
    }

    @After
    public void after() {
        if (cache != null) {
            cache.dispose();
            cache = null;
        }
    }

    protected abstract PointCache createCache(Platform platform);

    @Test
    public void testGetNull() {
        thrown.expect(NullPointerException.class);
        thrown.expectMessage("ids");
        cache.get(null);
    }

    @Test
    public void testGetEmpty() {
        CoordinateSequence coords = cache.get(ImmutableList.<Long> of());
        assertNotNull(coords);
        assertEquals(0, coords.size());
    }

    @Test
    public void testPutNullId() {
        thrown.expect(NullPointerException.class);
        thrown.expectMessage("id");
        cache.put(null, coord(0, 0));
    }

    @Test
    public void testPutNullCoord() {
        thrown.expect(NullPointerException.class);
        thrown.expectMessage("coord");
        cache.put(1L, null);
    }

    @Test
    public void testDisposeIsIdempotent() {
        cache.dispose();
        cache.dispose();
    }

    @Test
    public void testGetNonExistentCoordinate() {
        cache.put(3L, coord(0, 0));
        cache.put(2L, coord(0, 0));
        cache.put(1L, coord(0, 0));
        List<Long> ids = ImmutableList.<Long> of(1L, 5L, 2L, 3L);
        thrown.expect(IllegalArgumentException.class);
        cache.get(ids);
    }

    @Test
    public void testGet() {
        cache.put(3L, coord(3, 3));
        cache.put(2L, coord(2, 2));
        cache.put(1L, coord(1, 1));
        List<Long> ids = ImmutableList.<Long> of(1L, 2L, 3L);
        CoordinateSequence sequence = cache.get(ids);
        assertNotNull(sequence);
        assertEquals(3, sequence.size());
        assertEquals(1D, sequence.getOrdinate(0, 0), 1E-9);
        assertEquals(1D, sequence.getOrdinate(0, 1), 1E-9);
        assertEquals(2D, sequence.getOrdinate(1, 0), 1E-9);
        assertEquals(2D, sequence.getOrdinate(1, 1), 1E-9);
        assertEquals(3D, sequence.getOrdinate(2, 0), 1E-9);
        assertEquals(3D, sequence.getOrdinate(2, 1), 1E-9);
    }

    @Test
    public void testLargeSequences1M() {
        testLargeSequences(1000 * 1000);
    }

    @Ignore
    @Test
    public void testLargeSequences10M() {
        testLargeSequences(10 * 1000 * 1000);
    }

    @Ignore
    @Test
    public void testLargeSequences25M() {
        testLargeSequences(25 * 1000 * 1000);
    }

    @Ignore
    @Test
    public void testLargeSequences50M() {
        testLargeSequences(50 * 1000 * 1000);
    }

    @Ignore
    @Test
    public void testLargeSequences100M() {
        testLargeSequences(100 * 1000 * 1000);
    }

    @Test
    public void testLargeSequencesNonSequentialQueries1M() {
        testLargeSequencesNonSequentialQueries(1000 * 1000);
    }

    @Ignore
    @Test
    public void testLargeSequencesNonSequentialQueries10M() {
        testLargeSequencesNonSequentialQueries(10 * 1000 * 1000);
    }

    @Ignore
    @Test
    public void testLargeSequencesNonSequentialQueries25M() {
        testLargeSequencesNonSequentialQueries(25 * 1000 * 1000);
    }

    @Ignore
    @Test
    public void testLargeSequencesNonSequentialQueries50M() {
        testLargeSequencesNonSequentialQueries(50 * 1000 * 1000);
    }

    @Ignore
    @Test
    public void testLargeSequencesNonSequentialQueries100M() {
        testLargeSequencesNonSequentialQueries(100 * 1000 * 1000);
    }

    private void testLargeSequences(final int numNodes) {
        List<Long> queryIds = new ArrayList<Long>(numNodes / 6);

        Stopwatch sw = Stopwatch.createUnstarted();
        final int bulkSize = 10_000;
        List<Integer> random = new ArrayList<>(bulkSize);
        for (int n = 1; n <= numNodes; n++) {
            if (n % 20 == 0) {
                queryIds.add(Long.valueOf(n));
            }
            random.add(Integer.valueOf(n));
            if (random.size() == bulkSize || n == numNodes) {
                Collections.shuffle(random);
                sw.start();
                for (Integer id : random) {
                    cache.put(id.longValue(), coord(id.intValue(), id.intValue()));
                }
                sw.stop();
                random.clear();
            }
        }
        System.err.printf("%,d nodes added in %s\n", numNodes, sw);

        Collections.shuffle(queryIds);

        sw.reset().start();
        CoordinateSequence sequence = cache.get(queryIds);
        System.err.printf("requested %,d coordinates in %s\n", queryIds.size(), sw.stop());
        assertNotNull(sequence);
        assertEquals(queryIds.size(), sequence.size());
        long approxDbSize = caclDbSize();
        System.err.printf("Approx db size: %,f MB\n\n", ((double) approxDbSize / 1024D / 1024D));
    }

    private void testLargeSequencesNonSequentialQueries(final int numNodes) {
        List<Long> nodeIds = new ArrayList<Long>(numNodes / 6);
        Stopwatch sw = Stopwatch.createStarted();
        for (int n = 0; n < numNodes; n++) {
            if (n % 20 == 0) {
                nodeIds.add(Long.valueOf(n));
            }
            cache.put((long) n, coord(n, n));

            if (n > 1 && n % 100 == 0) {
                Collections.shuffle(nodeIds);
                List<Long> ids = nodeIds.subList(0, Math.min(nodeIds.size(), 100));
                cache.get(ids);
                nodeIds.clear();
            }
        }
        System.err.printf("%,d nodes added in %s\n", numNodes, sw.stop());
    }

    private long caclDbSize() {
        final File repoDir = new File(tmpFolder.getRoot(), ".geogig");
        final AtomicLong sizeContainer = new AtomicLong();
        repoDir.listFiles(new FileFilter() {

            @Override
            public boolean accept(final File file) {
                if (file.isFile()) {
                    long length = file.length();
                    sizeContainer.addAndGet(length);
                } else {
                    file.listFiles(this);
                }
                return true;
            }
        });
        return sizeContainer.get();
    }

    private OSMCoordinateSequence coord(double x, double y) {
        return new OSMCoordinateSequence(new Coordinate[] { new Coordinate(x, y) });
    }
}
