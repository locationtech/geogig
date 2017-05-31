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
import org.locationtech.geogig.storage.cache.Key.BigKey;
import org.locationtech.geogig.storage.cache.Key.SmallKey;

public class KeyTest {

    ObjectId id1 = hashString("1"), id2 = hashString("2");

    public @Test void testCreate() {
        Key k1 = Key.create(0, id1);
        Key k2 = Key.create(Byte.MAX_VALUE + 1, id1);
        assertTrue(k1 instanceof SmallKey);
        assertTrue(k2 instanceof BigKey);

        assertEquals(0, k1.prefix());
        assertEquals(Byte.MAX_VALUE + 1, k2.prefix());

        assertEquals(id1, k1.id());
        assertEquals(id1, k2.id());
    }

    public @Test void testEquals() {
        Key k11 = Key.create(0, id1);
        Key k21 = Key.create(1000, id1);

        assertEquals(k11, Key.create(0, id1));
        assertEquals(k21, Key.create(1000, id1));

        assertNotEquals(k11, k21);

        assertNotEquals(k11, Key.create(0, id2));
        assertNotEquals(k21, Key.create(1000, id2));
    }
}
