/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.cache.guava;

import org.locationtech.geogig.storage.cache.SharedCache;
import org.locationtech.geogig.storage.cache.SharedCacheTest;

public class GuavaSharedCacheTest extends SharedCacheTest{

    protected @Override SharedCache createCache(int l1Capacity, long maxCacheSizeBytes) {
        return new GuavaSharedCache(l1Capacity, maxCacheSizeBytes);
    }
}
