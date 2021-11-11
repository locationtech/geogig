package org.locationtech.geogig.cache.caffeine;

import static org.locationtech.geogig.base.Preconditions.checkArgument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.flatbuffers.FlatBuffersRevObjectSerializer;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.cache.CacheIdentifier;
import org.locationtech.geogig.storage.cache.CacheKey;
import org.locationtech.geogig.storage.cache.CacheStats;
import org.locationtech.geogig.storage.cache.SharedCache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Weigher;
import com.google.common.annotations.VisibleForTesting;

/**
 * 
 * @since 1.4
 */
public class CaffeineSharedCache implements SharedCache {

    private static final RevObjectSerializer ENCODER = new FlatBuffersRevObjectSerializer();

    private RevObjectSerializer encoder = ENCODER;

    /**
     * Used to track the size in bytes of the cache, since {@link Cache} can return only the
     * approximate number of entries but not the accumulated {@link Weigher#weigh weight}
     *
     */
    private static class SizeTracker implements RemovalListener<CacheKey, byte[]> {

        private static Weigher<CacheKey, byte[]> WEIGHER = new Weigher<CacheKey, byte[]>() {

            static final int ESTIMATED_KEY_SIZE = 32;

            public @Override int weigh(CacheKey key, byte[] value) {
                return ESTIMATED_KEY_SIZE + value.length;
            }

        };

        public final AtomicLong size = new AtomicLong();

        public @Override void onRemoval(CacheKey key, byte[] value, RemovalCause cause) {
            int weigh = WEIGHER.weigh(key, value);
            size.addAndGet(-weigh);
        }

        public void inserted(CacheKey id, byte[] value) {
            int weigh = WEIGHER.weigh(id, value);
            size.addAndGet(weigh);
        }
    }

    @VisibleForTesting
    public void setEncoder(RevObjectSerializer encoder) {
        this.encoder = encoder;
    }

    /**
     * The Level2 cache contains serialized versions of RevObjects, as they take less memory than
     * Java objects and their size can be more or less accurately tracked.
     */
    final Cache<CacheKey, byte[]> byteCache;

    private final SizeTracker sizeTracker;

    private long maxCacheSizeBytes;

    CaffeineSharedCache() {
        this.byteCache = Caffeine.newBuilder().maximumSize(0).build();
        this.sizeTracker = new SizeTracker();
    }

    public CaffeineSharedCache(final long maxCacheSizeBytes) {
        this.maxCacheSizeBytes = maxCacheSizeBytes;
        checkArgument(maxCacheSizeBytes >= 0, "Cache size can't be < 0, 0 meaning no cache at all");

        int initialCapacityCount = 1_000_000;

        Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        cacheBuilder = cacheBuilder.maximumWeight(maxCacheSizeBytes);
        cacheBuilder.weigher(SizeTracker.WEIGHER);
        cacheBuilder.initialCapacity(initialCapacityCount);
        cacheBuilder.recordStats();
        sizeTracker = new SizeTracker();
        cacheBuilder.removalListener(sizeTracker);
        this.byteCache = cacheBuilder.build();
    }

    CaffeineSharedCache(Cache<CacheKey, byte[]> byteCache, SizeTracker sizeTracker) {
        this.byteCache = byteCache;
        this.sizeTracker = sizeTracker;
    }

    public @Override boolean contains(CacheKey id) {
        return byteCache.asMap().containsKey(id);
    }

    public @Override void invalidateAll() {
        byteCache.invalidateAll();
        byteCache.cleanUp();
    }

    public @Override void invalidateAll(CacheIdentifier prefix) {
        invalidateAll(prefix, byteCache.asMap());
    }

    private void invalidateAll(CacheIdentifier prefix, ConcurrentMap<CacheKey, ?> map) {
        map.keySet().parallelStream().filter(k -> {
            int keyprefix = k.prefix();
            int expectedPrefix = prefix.prefix();
            return keyprefix == expectedPrefix;
        }).forEach(map::remove);
    }

    public @Override void dispose() {
        invalidateAll();
    }

    public @Override void invalidate(CacheKey id) {
        byteCache.invalidate(id);
    }

    /**
     * Returns the cached {@link RevObject}, if present in either the L1 or L2 cache, or
     * {@code null} otherwise.
     * <p>
     * As {@link RevTree}s are frequently requested and tend to be slower to parse, cache miss to
     * the L1 cache that resulted in a cache hit to the L2 cache, and where the resulting object is
     * a {@code RevTree}, will result in the tree being added back to the L1 cache.
     */
    public @Override @Nullable RevObject getIfPresent(CacheKey key) {
        // call cache.getIfPresent instead of map.get() or the cache stats don't record the
        // hits/misses
        byte[] val = byteCache.getIfPresent(key);
        if (val != null) {
            return decode(key, val);
        }
        return null;
    }

    /**
     * Adds the given object to the cache under the given key, if not already present.
     */
    public @Override @Nullable Future<?> put(CacheKey key, RevObject obj) {
        if (maxCacheSizeBytes > 0L) {
            // add it to L2 if not already present, even if it's a RevTree and has been added to
            // the L1 cache, since removal notifications happen after the fact
            return putInternal(key, obj);
        }
        return null;
    }

    @Nullable
    Future<?> putInternal(CacheKey key, RevObject obj) {
        byte[] value = encode(obj);
        if (null == byteCache.asMap().putIfAbsent(key, value)) {
            sizeTracker.inserted(key, value);
            return CompletableFuture.completedFuture(null);
        }
        return null;
    }

    private byte[] encode(RevObject obj) {
        if (encoder instanceof FlatBuffersRevObjectSerializer) {
            return ((FlatBuffersRevObjectSerializer) encoder).encode(obj);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
        out.reset();
        try {
            encoder.write(obj, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    private RevObject decode(CacheKey key, byte[] val) {
        try {
            return encoder.read(key.id(), val, 0, val.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public @Override String toString() {
        long size = byteCache.estimatedSize();
        long bytes = sizeTracker.size.get();
        long avg = size == 0 ? 0 : bytes / size;
        return String.format("Size: %,d, bytes: %,d, avg: %,d bytes/entry, %s", size, bytes, avg,
                byteCache.stats());
    }

    public @Override long sizeBytes() {
        return sizeTracker.size.get();
    }

    public @Override long objectCount() {
        return byteCache.estimatedSize();
    }

    public @Override CacheStats getStats() {
        final com.github.benmanes.caffeine.cache.stats.CacheStats stats = byteCache.stats();
        return new CacheStats() {
            public @Override long hitCount() {
                return stats.hitCount();
            }

            public @Override double hitRate() {
                return stats.hitRate();
            }

            public @Override long missCount() {
                return stats.missCount();
            }

            public @Override double missRate() {
                return stats.missRate();
            }

            public @Override long evictionCount() {
                return stats.evictionCount();
            }
        };
    }
}
