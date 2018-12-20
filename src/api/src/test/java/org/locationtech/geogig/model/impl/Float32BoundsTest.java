/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;


import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

public class Float32BoundsTest {

    @Test
    public void testSimple() {
        // 1,1 is the same in float4 and float8
        Coordinate coord = new Coordinate(1, 1);
        Float32Bounds bounds = Float32Bounds.valueOf(new Envelope(coord));
        assertTrue(bounds.asEnvelope().contains(coord));
        assertTrue(bounds.intersects(new Envelope(coord)));

        Envelope testEnvelope = new Envelope();
        bounds.expand(testEnvelope);
        assertTrue(testEnvelope.covers(bounds.asEnvelope()));


        coord = new Coordinate(Math.PI, Math.E);
        bounds = Float32Bounds.valueOf(new Envelope(coord));
        assertNotEquals(bounds, new Envelope(coord));
        assertTrue(bounds.asEnvelope().contains(coord));
        assertTrue(bounds.intersects(new Envelope(coord)));

        testEnvelope = new Envelope();
        bounds.expand(testEnvelope);
        assertTrue(testEnvelope.covers(bounds.asEnvelope()));
    }


}
