/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.locationtech.jts.geom.Envelope;

public class BucketTest {

    private Bucket create(ObjectId id, Envelope bounds) {
        return RevObjectFactory.defaultInstance().createBucket(id, 0, bounds);
    }

    @Test
    public void testPointBucket() {
        ObjectId oId = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        Bucket pointBucket = create(oId, new Envelope(0, 0, 1, 1));

        assertEquals(oId, pointBucket.getObjectId());
        assertEquals(new Envelope(0, 0, 1, 1), pointBucket.bounds().get());

        Envelope env = new Envelope(0, 0, 0, 0);
        pointBucket.expand(env);
        assertEquals(new Envelope(0, 0, 0, 1), env);

        assertTrue(pointBucket.intersects(env));
        assertFalse(pointBucket.intersects(new Envelope(0, 5, 0, 0)));

        assertTrue(pointBucket.toString().contains(RevObjects.toShortString(oId)));
    }

    @Test
    public void testRectangleBucket() {
        ObjectId oId = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        Bucket rectangleBucket = create(oId, new Envelope(0, 1, 2, 3));
        Bucket noWidthBucket = create(oId, new Envelope(0, 1, 0, 3));
        Bucket noHeightBucket = create(oId, new Envelope(0, 1, 2, 1));

        assertEquals(oId, rectangleBucket.getObjectId());
        assertEquals(new Envelope(0, 1, 2, 3), rectangleBucket.bounds().get());

        Envelope env = new Envelope(0, 0, 0, 0);
        rectangleBucket.expand(env);
        assertEquals(new Envelope(0, 1, 0, 3), env);

        assertTrue(rectangleBucket.intersects(env));
        assertFalse(rectangleBucket.intersects(new Envelope(5, 5, 7, 7)));

        assertTrue(rectangleBucket.toString().contains(RevObjects.toShortString(oId)));

        assertTrue(noWidthBucket.toString().contains(RevObjects.toShortString(oId)));

        assertTrue(noHeightBucket.toString().contains(RevObjects.toShortString(oId)));
    }

    @Test
    public void testNonspatialBucket() {
        ObjectId oId = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");
        Bucket nonspatialBucket = create(oId, null);
        Bucket nonspatialBucket2 = create(oId, new Envelope());

        assertEquals(oId, nonspatialBucket.getObjectId());
        assertEquals(oId, nonspatialBucket2.getObjectId());
        assertFalse(nonspatialBucket.bounds().isPresent());
        assertFalse(nonspatialBucket2.bounds().isPresent());

        assertEquals(nonspatialBucket, nonspatialBucket2);

        Envelope env = new Envelope(0, 0, 0, 0);
        nonspatialBucket.expand(env);
        assertEquals(new Envelope(0, 0, 0, 0), env);

        assertFalse(nonspatialBucket.intersects(env));
        assertFalse(nonspatialBucket.intersects(new Envelope(0, 0, 100, 100)));
        assertTrue(nonspatialBucket.toString().contains(RevObjects.toShortString(oId)));
    }

    @Test
    public void testEquals() {
        ObjectId oId1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");
        Bucket pointBucket = create(oId1, new Envelope(0, 0, 1, 1));
        ObjectId oId2 = ObjectId.valueOf("abc123000000000000001234567890abcdef0001");
        Bucket rectangleBucket = create(oId2, new Envelope(0, 1, 2, 3));
        ObjectId oId3 = ObjectId.valueOf("abc123000000000000001234567890abcdef0002");
        Bucket nonspatialBucket = create(oId3, null);

        assertEquals(pointBucket, pointBucket);
        assertEquals(rectangleBucket, rectangleBucket);
        assertEquals(nonspatialBucket, nonspatialBucket);
        assertFalse(pointBucket.equals(rectangleBucket));
        assertFalse(pointBucket.equals(nonspatialBucket));
        assertFalse(rectangleBucket.equals(nonspatialBucket));

        assertFalse(pointBucket.equals(oId1));
    }
}
