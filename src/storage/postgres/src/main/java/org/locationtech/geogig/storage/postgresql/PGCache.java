/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.datastream.LZ4SerializationFactory;
import org.locationtech.geogig.storage.datastream.v2_2.DataStreamSerializationFactoryV2_2;
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.cache.Weigher;

public class PGCache {

    private static final ObjectSerializingFactory ENCODER = new LZ4SerializationFactory(//
            DataStreamSerializationFactoryV2_2.INSTANCE//
    );

    protected static final int ESTIMATED_OBJECTID_SIZE = 28;

    private ObjectSerializingFactory encoder = ENCODER;

    private static Weigher<ObjectId, byte[]> weigher = new Weigher<ObjectId, byte[]>() {

        // private static Instrumentation instrumentation;

        @Override
        public int weigh(ObjectId key, byte[] value) {
            return ESTIMATED_OBJECTID_SIZE + value.length;
        }

    };

    private static class SizeTracker implements RemovalListener<ObjectId, byte[]> {

        public final AtomicLong size = new AtomicLong();

        @Override
        public void onRemoval(RemovalNotification<ObjectId, byte[]> notification) {
            ObjectId key = notification.getKey();
            byte[] value = notification.getValue();
            int weigh = weigher.weigh(key, value);
            size.addAndGet(-weigh);
        }

        public void inserted(ObjectId id, byte[] value) {
            int weigh = weigher.weigh(id, value);
            size.addAndGet(weigh);
        }
    }

    public static PGCache build(ConfigDatabase configdb) {
        Optional<Long> maxSize = configdb.get(Environment.KEY_ODB_BYTE_CACHE_MAX_SIZE, Long.class);
        Optional<Integer> concurrencyLevel = configdb
                .get(Environment.KEY_ODB_BYTE_CACHE_CONCURRENCY_LEVEL, Integer.class);
        Optional<Integer> expireSeconds = configdb
                .get(Environment.KEY_ODB_BYTE_CACHE_EXPIRE_SECONDS, Integer.class);
        Optional<Integer> initialCapacity = configdb
                .get(Environment.KEY_ODB_BYTE_CACHE_INITIAL_CAPACITY, Integer.class);

        Integer expireAfterAccessSeconds = expireSeconds.or(300);
        Integer initialCapacityCount = initialCapacity.or(1_000_000);
        Integer concurrencyLevel2 = concurrencyLevel.or(16);
        Long maxWeightBytes = maxSize.or(defaultCacheSize());

        return build(initialCapacityCount, concurrencyLevel2, maxWeightBytes,
                expireAfterAccessSeconds);
    }

    private static PGCache build(Integer initialCapacityCount, Integer concurrencyLevel2,
            Long maxWeightBytes, Integer expireAfterAccessSeconds) {
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
        cacheBuilder = cacheBuilder.maximumWeight(maxWeightBytes);
        cacheBuilder.weigher(weigher);
        cacheBuilder.expireAfterAccess(expireAfterAccessSeconds, TimeUnit.SECONDS);
        cacheBuilder.initialCapacity(initialCapacityCount);
        cacheBuilder.concurrencyLevel(concurrencyLevel2);
        cacheBuilder.recordStats();

        SizeTracker sizeTracker = new SizeTracker();
        cacheBuilder.removalListener(sizeTracker);

        Cache<ObjectId, byte[]> byteCache = cacheBuilder.build();

        return new PGCache(byteCache, sizeTracker);
    }

    @VisibleForTesting
    public static PGCache build() {
        Integer expireAfterAccessSeconds = 300;
        Integer initialCapacityCount = 1_000_000;
        Integer concurrencyLevel2 = 16;
        Long maxWeightBytes = defaultCacheSize();

        return build(initialCapacityCount, concurrencyLevel2, maxWeightBytes,
                expireAfterAccessSeconds);
    }

    @VisibleForTesting
    public void setEncoder(ObjectSerializingFactory encoder) {
        this.encoder = encoder;
    }

    private static long defaultCacheSize() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        // Use up to 50% of the heap by default
        return (long) (maxMemory * 0.5);
    }

    private Cache<ObjectId, byte[]> cache;

    /**
     * ConcurrentMap view of the cache, used to check for existent through containsKey instead of
     * cache.getIfPresent() (to avoid getting the value unnecessarily), and putIfAbsent instead of
     * cache.put(), to avoid replacing objects since RevObject instances are immutable
     */
    private Map<ObjectId, byte[]> map;

    private SizeTracker sizeTracker;

    public PGCache(Cache<ObjectId, byte[]> byteCache, SizeTracker sizeTracker) {
        this.cache = byteCache;
        this.sizeTracker = sizeTracker;
        this.map = cache.asMap();
    }

    public boolean contains(ObjectId id) {
        return map.containsKey(id);
    }

    public void invalidateAll() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    public void dispose() {
        invalidateAll();
    }

    public void invalidate(ObjectId id) {
        cache.invalidate(id);
    }

    public void put(RevObject obj) {
        byte[] value = encode(obj);
        byte[] prev = map.putIfAbsent(obj.getId(), value);
        if (prev == null) {
            sizeTracker.inserted(obj.getId(), value);
        }
    }

    private byte[] encode(RevObject obj) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            encoder.write(obj, out);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        byte[] byteArray = out.toByteArray();
        return byteArray;
    }

    private RevObject decode(ObjectId id, byte[] val) {
        try {
            return encoder.read(id, new ByteArrayInputStream(val));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public RevObject getIfPresent(ObjectId id) {
        // call cache.getIfPresent instead of map.get() or the cache stats don't record the
        // hits/misses
        byte[] val = cache.getIfPresent(id);
        return val == null ? null : decode(id, val);
    }

    public String toString() {
        long size = cache.size();
        long bytes = sizeTracker.size.get();
        long avg = size == 0 ? 0 : bytes / size;
        return String.format("Size: %,d, bytes: %,d, avg: %,d bytes/entry, %s", size, bytes, avg,
                cache.stats());
    }

    public long sizeBytes() {
        return sizeTracker.size.get();
    }
}
