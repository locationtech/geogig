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

import org.locationtech.geogig.model.ObjectId;

/**
 * Uniquely identifies an {@link ObjectCache} inside the {@link SharedCache} by holding an integer
 * {@link #prefix} that's valid during the lifetime of the JVM.
 * <p>
 * The {@code CacheIdentifier} is given to the {@link ObjectCache} and acts as a factory for
 * {@link CacheKey keys} specific to that object cache to then be used as keys on the
 * {@link SharedCache}.
 */
public class CacheIdentifier {

    private final int prefix;

    public CacheIdentifier(int prefix) {
        this.prefix = prefix;
    }

    /**
     * Creates a Key for an {@link ObjectId} to uniquely identify an object in the
     * {@link SharedCache} belonging to one {@link ObjectCache}
     */
    public CacheKey create(ObjectId id) {
        return CacheKey.create(prefix, id);
    }

    /**
     * @return the prefix for this cache identifier
     */
    public int prefix() {
        return prefix;
    }
}
