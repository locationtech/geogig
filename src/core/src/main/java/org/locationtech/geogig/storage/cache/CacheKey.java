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
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjects;

/**
 * The key used by {@link SharedCache}, composed of an integer {@link #prefix()} and the
 * {@link RevObject}'s {@link ObjectId id}.
 * <p>
 * Keys are created by {@link CacheIdentifier#create(ObjectId)}, {@code CacheIdentifier} is unique
 * per {@link ObjectCache}.
 * 
 * @implNote: the {@link #create(int, ObjectId)} method will return a specialization of Key who's
 *            {@code prefix} is a single {@code byte} when the prefix is inside the byte value
 *            range, or an {@code int} otherwise.
 */
public abstract class CacheKey {

    final int h1;

    final long h2;

    final long h3;

    protected CacheKey(ObjectId id) {
        this.h1 = RevObjects.h1(id);
        this.h2 = RevObjects.h2(id);
        this.h3 = RevObjects.h3(id);
    }

    public abstract int prefix();

    public ObjectId id() {
        return ObjectId.create(h1, h2, h3);
    }

    public final @Override boolean equals(Object o) {
        if (!(o instanceof CacheKey)) {
            return false;
        }
        CacheKey k = (CacheKey) o;
        return prefix() == k.prefix() && h1 == k.h1 && h2 == k.h2 && h3 == k.h3;
    }

    public final @Override int hashCode() {
        int hash = 17;
        hash = hash * 31 + h1;
        hash = 31 * hash + (int) (h2 ^ (h2 >>> 32));
        hash = 31 * hash + (int) (h2 ^ (h2 >>> 32));
        return hash;
    }

    public static CacheKey create(int keyPrefix, ObjectId id) {
        if (keyPrefix >= Byte.MIN_VALUE && keyPrefix <= Byte.MAX_VALUE) {
            return new SmallKey((byte) keyPrefix, id);
        }
        return new BigKey(keyPrefix, id);
    }

    static final class SmallKey extends CacheKey {

        private final byte keyPrefix;

        public SmallKey(byte keyPrefix, ObjectId id) {
            super(id);
            this.keyPrefix = keyPrefix;
        }

        @Override
        public int prefix() {
            return keyPrefix & 0xFF;
        }
    }

    static final class BigKey extends CacheKey {

        private final int keyPrefix;

        public BigKey(int keyPrefix, ObjectId id) {
            super(id);
            this.keyPrefix = keyPrefix;
        }

        @Override
        public int prefix() {
            return keyPrefix;
        }

    }
}
