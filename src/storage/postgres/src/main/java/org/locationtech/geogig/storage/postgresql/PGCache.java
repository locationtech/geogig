package org.locationtech.geogig.storage.postgresql;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;

public class PGCache {

    private static Weigher<ObjectId, RevObject> weigher = new Weigher<ObjectId, RevObject>() {

        private final int ESTIMATED_OBJECTID_SIZE = 8 + ObjectId.NUM_BYTES;

        @Override
        public int weigh(ObjectId key, RevObject obj) {
            return ESTIMATED_OBJECTID_SIZE;// + value.length;
        }

    };

    public static PGCache build(ConfigDatabase configdb) {
        Optional<Long> maxSize = configdb.get(Environment.KEY_ODB_BYTE_CACHE_MAX_SIZE, Long.class);
        Optional<Integer> concurrencyLevel = configdb
                .get(Environment.KEY_ODB_BYTE_CACHE_CONCURRENCY_LEVEL, Integer.class);
        Optional<Integer> expireSeconds = configdb
                .get(Environment.KEY_ODB_BYTE_CACHE_EXPIRE_SECONDS, Integer.class);
        Optional<Integer> initialCapacity = configdb
                .get(Environment.KEY_ODB_BYTE_CACHE_INITIAL_CAPACITY, Integer.class);

        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
        Long maxWeightBytes = maxSize.or(defaultCacheSize());
        cacheBuilder = cacheBuilder.maximumWeight(maxWeightBytes);
        cacheBuilder.weigher(weigher);
        cacheBuilder.expireAfterAccess(expireSeconds.or(300), TimeUnit.SECONDS);
        cacheBuilder.initialCapacity(initialCapacity.or(1_000_000));
        cacheBuilder.concurrencyLevel(concurrencyLevel.or(16));
        cacheBuilder.softValues();
        cacheBuilder.recordStats();
        Cache<ObjectId, RevObject> byteCache = cacheBuilder.build();

        return new PGCache(byteCache);
    }

    private static long defaultCacheSize() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        // Use 20% of the heap by default
        return (long) (maxMemory * 0.5);
    }

    private Cache<ObjectId, RevObject> cache;

    private ConcurrentMap<ObjectId, RevObject> map;

    public PGCache(Cache<ObjectId, RevObject> byteCache) {
        this.cache = byteCache;
        this.map = byteCache.asMap();
    }

    public boolean contains(ObjectId id) {
        return map.containsKey(id);
    }

    public void dispose() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    public void invalidate(ObjectId id) {
        cache.invalidate(id);
    }

    public void put(ObjectId id, RevObject obj) {
        map.putIfAbsent(id, obj);
    }

    public RevObject getIfPresent(ObjectId id) {
        return cache.getIfPresent(id);
    }

    public String toString() {
        return String.format("Size: %,d, %s", cache.size(), cache.stats());
    }
}
