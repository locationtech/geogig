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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.datastream.LZ4SerializationFactory;
import org.locationtech.geogig.storage.datastream.v2_3.DataStreamSerializationFactoryV2_3;
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * A {@link RevObject} cache to be used by multiple {@link ObjectStore} instances operating upon a
 * single internal cache, discriminating specific {@link ObjectStore} entries by means of the
 * {@link Key} instances, which provide a target backend's store prefix besides the
 * {@code RevObject's} id.
 * <p>
 * Except for unit tests, one single instance of a {@code SharedCache} will exist at any given time
 * as a member of the {@link CacheManager#INSTANCE} JVM singleton.
 * <p>
 * {@code SharedCache} instances are created through the {@link #build(long)} factory method.
 * 
 */
interface SharedCache {

    /**
     * Singleton no-op cache instance for when {@link #build(long)} is called with {@code 0L} as
     * argument
     */
    static final SharedCache NO_CACHE = new SharedCache() {
    };

    /**
     * Creates and returns a shared cache with the given maximum heap memory capacity in bytes.
     * <p>
     * This factory method does not check whether the provided maximum capacity exceeds any JVM
     * limit, it's up to the calling code to make sure the provided capacity can be delivered by the
     * JVM heap.
     * 
     * @throws IllegalArgumentException if {@code maxCacheSizeBytes} is lower than zero, with zero
     *         meaning no caching at all.
     */
    public static SharedCache build(final long maxCacheSizeBytes) {
        return SharedCache.build(Impl.L1_CACHE_SIZE, maxCacheSizeBytes);
    }

    @VisibleForTesting
    static SharedCache build(int L1capacity, long maxCacheSizeBytes) {
        checkArgument(L1capacity >= 0);
        checkArgument(maxCacheSizeBytes >= 0, "Cache size can't be < 0, 0 meaning no cache at all");

        if (0L == maxCacheSizeBytes) {
            return NO_CACHE;
        }

        int initialCapacityCount = 1_000_000;
        int concurrencyLevel = 16;

        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
        cacheBuilder = cacheBuilder.maximumWeight(maxCacheSizeBytes);
        cacheBuilder.weigher(Impl.SizeTracker.WEIGHER);

        cacheBuilder.initialCapacity(initialCapacityCount);
        cacheBuilder.concurrencyLevel(concurrencyLevel);
        cacheBuilder.recordStats();

        Impl.SizeTracker sizeTracker = new Impl.SizeTracker();
        cacheBuilder.removalListener(sizeTracker);

        Cache<Key, byte[]> byteCache = cacheBuilder.build();

        return new Impl(L1capacity, byteCache, sizeTracker);
    }

    default boolean contains(Key id) {
        return false;
    }

    default void invalidateAll() {
    }

    default void invalidateAll(CacheIdentifier prefix) {
    }

    default void dispose() {
    }

    default void invalidate(Key id) {
    }

    default @Nullable RevObject getIfPresent(Key key) {
        return null;
    }

    default @Nullable Future<?> put(Key key, RevObject obj) {
        return null;
    }

    default long sizeBytes() {
        return 0L;
    }

    default long objectCount() {
        return 0L;
    }

    default CacheStats getStats() {
        return new CacheStats(0, 0, 0, 0, 0, 0);
    }

    static class Impl implements SharedCache {
        /**
         * Executor service used to encode a {@link RevObject} to a {@code byte[]} and add it to the
         * L2cache.
         * <p>
         * The executor alleviates the overhead of adding an object to the cache, as it needs to be
         * serialized, but uses a bounded queue of up to # of cores, and a {@link CallerRunsPolicy}
         * as rejected execution handler, so that under load, the calling thread will pay the price
         * of encoding and caching instead of running an unbounded number of threads to store the
         * objects in the cache.
         * 
         * @see #insert(Key, RevObject)
         */
        static final ExecutorService WRITE_BACK_EXECUTOR;
        static {
            ThreadFactory tf = new ThreadFactoryBuilder().setDaemon(true)
                    .setNameFormat("GeoGig shared cache %d").build();

            final int nThreads = Math.max(2, Runtime.getRuntime().availableProcessors());

            RejectedExecutionHandler sameThreadHandler = new ThreadPoolExecutor.CallerRunsPolicy();

            WRITE_BACK_EXECUTOR = new ThreadPoolExecutor(1, nThreads, 30L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(nThreads), tf, sameThreadHandler);
        }

        private static final ObjectSerializingFactory ENCODER = //
                new LZ4SerializationFactory(//
                        DataStreamSerializationFactoryV2_3.INSTANCE//
                );

        /**
         * Size of the L1 cache {@link Key} -> {@link RevTree}
         */
        private static final int L1_CACHE_SIZE = 10_000;

        private ObjectSerializingFactory encoder = ENCODER;

        /**
         * Used to track the size in bytes of the cache, since {@link Cache} can return only the
         * approximate number of entries but not the accumulated {@link Weigher#weigh weight}
         *
         */
        private static class SizeTracker implements RemovalListener<Key, byte[]> {

            private static Weigher<Key, byte[]> WEIGHER = new Weigher<Key, byte[]>() {

                static final int ESTIMATED_Key_SIZE = 32;

                @Override
                public int weigh(Key key, byte[] value) {
                    return ESTIMATED_Key_SIZE + value.length;
                }

            };

            public final AtomicLong size = new AtomicLong();

            @Override
            public void onRemoval(RemovalNotification<Key, byte[]> notification) {
                Key key = notification.getKey();
                byte[] value = notification.getValue();
                int weigh = WEIGHER.weigh(key, value);
                size.addAndGet(-weigh);
            }

            public void inserted(Key id, byte[] value) {
                int weigh = WEIGHER.weigh(id, value);
                size.addAndGet(weigh);
            }
        }

        @VisibleForTesting
        public void setEncoder(ObjectSerializingFactory encoder) {
            this.encoder = encoder;
        }

        /**
         * The Level1 cache contains most recently used, already parsed, instances of
         * {@link RevTree} objects as they tend to be slow to parse and queried very often.
         * <p>
         * When trees are evicted from the L1Cache due to size constraints, their serialized version
         * will be added to the L2Cache if it's not already present.
         * 
         * @see #put(Key, RevObject)
         * @see #getIfPresent(Key)
         */
        final Cache<Key, RevTree> L1Cache;

        /**
         * The Level2 cache contains serialized versions of RevObjects, as they take less memory
         * than Java objects and their size can be more or less accurately tracked.
         */
        final Cache<Key, byte[]> L2Cache;

        private final SizeTracker sizeTracker;

        Impl() {
            this.L1Cache = CacheBuilder.newBuilder().maximumSize(0).build();
            this.L2Cache = CacheBuilder.newBuilder().maximumSize(0).build();
            this.sizeTracker = new SizeTracker();
        }

        Impl(final int L1Capacity, Cache<Key, byte[]> byteCache, SizeTracker sizeTracker) {
            this.L2Cache = byteCache;
            this.sizeTracker = sizeTracker;

            RemovalListener<Key, RevObject> L1WriteBack = (notification) -> {
                RemovalCause cause = notification.getCause();
                if (RemovalCause.SIZE == cause) {
                    Key key = notification.getKey();
                    RevObject value = notification.getValue();
                    if (value != null) {
                        putInternal(key, value);
                    }
                }
            };

            this.L1Cache = CacheBuilder.newBuilder()//
                    .concurrencyLevel(1)//
                    .maximumSize(L1Capacity)//
                    .softValues()//
                    .removalListener(L1WriteBack)//
                    .build();
        }

        public boolean contains(Key id) {
            boolean contains = L1Cache.asMap().containsKey(id) || L2Cache.asMap().containsKey(id);
            return contains;
        }

        public void invalidateAll() {
            L1Cache.invalidateAll();
            L2Cache.invalidateAll();

            L1Cache.cleanUp();
            L2Cache.cleanUp();
        }

        public void invalidateAll(CacheIdentifier prefix) {
            invalidateAll(prefix, L1Cache.asMap());
            invalidateAll(prefix, L2Cache.asMap());
        }

        private void invalidateAll(CacheIdentifier prefix, ConcurrentMap<Key, ?> map) {
            map.keySet().parallelStream().filter((k) -> {
                int keyprefix = k.prefix();
                int expectedPrefix = prefix.prefix();
                return keyprefix == expectedPrefix;
            }).forEach((k) -> {
                map.remove(k);
            });
        }

        public void dispose() {
            invalidateAll();
        }

        public void invalidate(Key id) {
            L2Cache.invalidate(id);
        }

        /**
         * Returns the cached {@link RevObject}, if present in either the L1 or L2 cache, or
         * {@code null} otherwise.
         * <p>
         * As {@link RevTree}s are frequently requested and tend to be slower to parse, cache miss
         * to the L1 cache that resulted in a cache hit to the L2 cache, and where the resulting
         * object is a {@code RevTree}, will result in the tree being added back to the L1 cache.
         */
        public @Nullable RevObject getIfPresent(Key key) {
            RevObject obj = L1Cache.getIfPresent(key);
            if (obj == null) {
                // call cache.getIfPresent instead of map.get() or the cache stats don't record the
                // hits/misses
                byte[] val = L2Cache.getIfPresent(key);
                if (val != null) {
                    obj = decode(key, val);
                    if (TYPE.TREE == obj.getType()) {// keep L1 hot on tree objects
                        L1Cache.asMap().putIfAbsent(key, (RevTree) obj);
                    }
                }
            }
            return obj;
        }

        /**
         * Adds the given object to the cache under the given key, if not already present.
         * <p>
         * If the object happens to be a {@link RevTree}, it will first be added to the
         * {@link #L1Cache}. In either case, it's serialized version will be, possibly
         * asynchronously, added to the {@link #L2Cache}.
         */
        public @Nullable Future<?> put(Key key, RevObject obj) {
            RevObject l1val = TYPE.TREE == obj.getType()
                    ? L1Cache.asMap().putIfAbsent(key, (RevTree) obj) : null;
            if (l1val == null) {
                // add it to L2 if not already present, even if it's a RevTree and has been added to
                // the L1 cache, since removal notifications happen after the fact
                return putInternal(key, obj);
            }
            return null;
        }

        @Nullable
        Future<?> putInternal(Key key, RevObject obj) {
            if (!L2Cache.asMap().containsKey(key)) {
                return WRITE_BACK_EXECUTOR.submit(() -> insert(key, obj));
            }
            return null;
        }

        void insert(Key key, RevObject obj) {
            byte[] value = encode(obj);
            if (null == L2Cache.asMap().putIfAbsent(key, value)) {
                sizeTracker.inserted(key, value);
            }
        }

        private byte[] encode(RevObject obj) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                encoder.write(obj, out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            byte[] byteArray = out.toByteArray();
            return byteArray;
        }

        private RevObject decode(Key key, byte[] val) {
            try {
                return encoder.read(key.id(), val, 0, val.length);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public String toString() {
            long size = L2Cache.size();
            long bytes = sizeTracker.size.get();
            long avg = size == 0 ? 0 : bytes / size;
            return String.format("Size: %,d, bytes: %,d, avg: %,d bytes/entry, %s", size, bytes,
                    avg, L2Cache.stats());
        }

        public long sizeBytes() {
            return sizeTracker.size.get();
        }

        public long objectCount() {
            return L2Cache.size();
        }

        public CacheStats getStats() {
            return L2Cache.stats();
        }
    }
}
