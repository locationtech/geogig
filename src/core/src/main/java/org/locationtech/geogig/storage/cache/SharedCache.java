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

import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.RevObjectSerializer;

/**
 * A {@link RevObject} cache to be used by multiple {@link ObjectStore} instances operating upon a
 * single internal cache, discriminating specific {@link ObjectStore} entries by means of the
 * {@link CacheKey} instances, which provide a target backend's store prefix besides the
 * {@code RevObject's} id.
 * <p>
 * Except for unit tests, one single instance of a {@code SharedCache} will exist at any given time
 * as a member of the {@link CacheManager#INSTANCE} JVM singleton.
 * <p>
 * {@code SharedCache} instances are created through the {@link #build(long)} factory method.
 * 
 */
public interface SharedCache {

    /**
     * Size of the L1 cache {@link CacheKey} -> {@link RevTree}
     */
    static final int L1_CACHE_SIZE = 10_000;

    /**
     * Singleton no-op cache instance for when {@link #build(long)} is called with {@code 0L} as
     * argument
     */
    static final SharedCache NO_CACHE = new SharedCache() {
        @Override
        public void setEncoder(RevObjectSerializer encoder) {
            // nothing to do
        }
    };

    public void setEncoder(RevObjectSerializer encoder);

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
    // public static SharedCache build(final long maxCacheSizeBytes) {
    // return SharedCache.build(L1_CACHE_SIZE, maxCacheSizeBytes);
    // }

    default boolean contains(CacheKey id) {
        return false;
    }

    default void invalidateAll() {
    }

    default void invalidateAll(CacheIdentifier prefix) {
    }

    default void dispose() {
    }

    default void invalidate(CacheKey id) {
    }

    default @Nullable RevObject getIfPresent(CacheKey key) {
        return null;
    }

    default @Nullable Future<?> put(CacheKey key, RevObject obj) {
        return null;
    }

    default long sizeBytes() {
        return 0L;
    }

    default long objectCount() {
        return 0L;
    }

    default CacheStats getStats() {
        return new CacheStats() {
        };
    }

}
