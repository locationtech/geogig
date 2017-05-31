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
abstract class Key {

    private final ObjectId id;

    protected Key(ObjectId id) {
        this.id = id;
    }

    public abstract int prefix();

    public ObjectId id() {
        return this.id;
    }

    public @Override boolean equals(Object o) {
        if (!(o instanceof Key)) {
            return false;
        }
        Key k = (Key) o;
        return prefix() == k.prefix() && id.equals(k.id);
    }

    public @Override int hashCode() {
        return 31 * prefix() + id.hashCode();
    }

    public static Key create(int keyPrefix, ObjectId id) {
        if (keyPrefix >= Byte.MIN_VALUE && keyPrefix <= Byte.MAX_VALUE) {
            return new SmallKey((byte) keyPrefix, id);
        }
        return new BigKey(keyPrefix, id);
    }

    static final class SmallKey extends Key {

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

    static final class BigKey extends Key {

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
