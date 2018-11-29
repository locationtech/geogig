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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.hashString;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.storage.cache.CacheKey.BigKey;
import org.locationtech.geogig.storage.cache.CacheKey.SmallKey;

public class KeyTest {

    ObjectId id1 = hashString("1"), id2 = hashString("2");

    public @Test void testCreate() {
        CacheKey k1 = CacheKey.create(0, id1);
        CacheKey k2 = CacheKey.create(Byte.MAX_VALUE + 1, id1);
        assertTrue(k1 instanceof SmallKey);
        assertTrue(k2 instanceof BigKey);

        assertEquals(0, k1.prefix());
        assertEquals(Byte.MAX_VALUE + 1, k2.prefix());

        assertEquals(id1, k1.id());
        assertEquals(id1, k2.id());
    }

    public @Test void testEquals() {
        CacheKey k11 = CacheKey.create(0, id1);
        CacheKey k21 = CacheKey.create(1000, id1);

        assertEquals(k11, CacheKey.create(0, id1));
        assertEquals(k21, CacheKey.create(1000, id1));

        assertNotEquals(k11, k21);

        assertNotEquals(k11, CacheKey.create(0, id2));
        assertNotEquals(k21, CacheKey.create(1000, id2));
    }
}
