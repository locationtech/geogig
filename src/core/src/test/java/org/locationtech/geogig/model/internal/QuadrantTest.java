/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import com.vividsolutions.jts.geom.Envelope;
import org.junit.Test;

import static org.junit.Assert.*;


public class QuadrantTest {

    @Test
    public void testQuadrant() {

        Envelope eSW = Quadrant.SW.slice(new Envelope(0, 1, 0, 1));
        Envelope eSE = Quadrant.SE.slice(new Envelope(0, 1, 0, 1));
        Envelope eNW = Quadrant.NW.slice(new Envelope(0, 1, 0, 1));
        Envelope eNE = Quadrant.NE.slice(new Envelope(0, 1, 0, 1));

        assertTrue(eSW.getMinX() == 0);
        assertTrue(eSW.getMinY() == 0);
        assertTrue(eSW.getMaxX() == 0.5);
        assertTrue(eSW.getMaxY() == 0.5);

        assertTrue(eSE.getMinX() == 0.5);
        assertTrue(eSE.getMinY() == 0);
        assertTrue(eSE.getMaxX() == 1);
        assertTrue(eSE.getMaxY() == 0.5);


        assertTrue(eNW.getMinX() == 0);
        assertTrue(eNW.getMinY() == 0.5);
        assertTrue(eNW.getMaxX() == 0.5);
        assertTrue(eNW.getMaxY() == 1);

        assertTrue(eNE.getMinX() == 0.5);
        assertTrue(eNE.getMinY() == 0.5);
        assertTrue(eNE.getMaxX() == 1);
        assertTrue(eNE.getMaxY() == 1);

    }
}
