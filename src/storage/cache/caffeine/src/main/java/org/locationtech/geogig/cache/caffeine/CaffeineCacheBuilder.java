/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - Pulled off from SharedCache.Impl as a top level class
 */
package org.locationtech.geogig.cache.caffeine;

import org.locationtech.geogig.storage.cache.SharedCache;
import org.locationtech.geogig.storage.cache.SharedCacheBuilder;

public class CaffeineCacheBuilder implements SharedCacheBuilder {

    private static final int DEFAULT_L1_CAPACITY = 10_000;

    private long maxSizeBytes;

    public @Override int getPriority() {
        return 0;
    }

    public @Override void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public @Override SharedCache build() {
        return new CaffeineSharedCache(DEFAULT_L1_CAPACITY, maxSizeBytes);
    }

}
