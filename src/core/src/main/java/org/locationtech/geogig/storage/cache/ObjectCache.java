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

import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.ObjectStore;

/**
 * A view of the {@link SharedCache} for a single {@link CacheIdentifier}.
 * <p>
 * {@link ObjectStore} implementations can acquire their {@code ObjectCache} through
 * {@link CacheManager#acquire(String)}.
 * <p>
 * From the point of view of the {@code ObjectStore}, the returned {@code ObjectCache} is specific
 * to its contents and has no knowledge of the {@link SharedCache}.
 * <p>
 * Once an {@code ObjectStore} is done with its cache, must return it to the {@code CacheManager}
 * through its {@link CacheManager#release(ObjectCache)} method.
 *
 */
public class ObjectCache {

    private final CacheIdentifier keyPrefix;

    private final Supplier<SharedCache> sharedCache;

    public ObjectCache(Supplier<SharedCache> cache, CacheIdentifier prefix) {
        this.sharedCache = cache;
        this.keyPrefix = prefix;
    }

    /**
     * Checks whether a given {@link ObjectId} is already cached on this {@code ObjectCache}
     */
    public boolean contains(ObjectId id) {
        return sharedCache.get().contains(keyPrefix.create(id));
    }

    /**
     * Prunes this {@code ObjectCache}, making all its entries available for GC.
     */
    public void invalidateAll() {
        sharedCache.get().invalidateAll(keyPrefix);
    }

    /**
     * Prunes the given object id from the cache
     */
    public void invalidate(ObjectId id) {
        sharedCache.get().invalidate(keyPrefix.create(id));
    }

    public void put(RevObject obj) {
        SharedCache cache = sharedCache.get();
        CacheKey key = keyPrefix.create(obj.getId());
        cache.put(key, obj);
    }

    /**
     * Returns the cached object with the given id, if present, or {@code null} otherwise
     */
    public @Nullable RevObject getIfPresent(ObjectId id) {
        return sharedCache.get().getIfPresent(keyPrefix.create(id));
    }
}
