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


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuadTreeNodeIdTest {

    @Test
    public void testCompare_root() {

        //very simple tests
        //root - Q0 < Q1 < Q2 < Q3

        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SW}, new Quadrant[]{Quadrant.NW}), -1);
        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.NW}, new Quadrant[]{Quadrant.SW}), +1);

        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.NW}, new Quadrant[]{Quadrant.NE}), -1);
        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.NE}, new Quadrant[]{Quadrant.NW}), +1);

        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.NE}, new Quadrant[]{Quadrant.SE}), -1);
        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SE}, new Quadrant[]{Quadrant.NE}), +1);

        assertTrue(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SW}, new Quadrant[]{Quadrant.NE}) <= -1);
        assertTrue(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.NE}, new Quadrant[]{Quadrant.SW}) >= +1);


        assertTrue(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SW}, new Quadrant[]{Quadrant.SE}) <= -1);
        assertTrue(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SE}, new Quadrant[]{Quadrant.SW}) >= +1);

        //same
        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SW}, new Quadrant[]{Quadrant.SW}), 0);
        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SE}, new Quadrant[]{Quadrant.SE}), 0);
        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.NW}, new Quadrant[]{Quadrant.NW}), 0);
        assertEquals(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.NE}, new Quadrant[]{Quadrant.NE}), 0);
    }

    @Test
    public void testCompare_differentTreeHeights() {
        assertTrue(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SW}, new Quadrant[]{Quadrant.SW, Quadrant.SW}) < 0);
        assertTrue(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SW, Quadrant.SW}, new Quadrant[]{Quadrant.SW}) > 0);


        assertTrue(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.NW}, new Quadrant[]{Quadrant.SW, Quadrant.SW}) > 0);
        assertTrue(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SW, Quadrant.SW}, new Quadrant[]{Quadrant.NW}) < 0);

        assertTrue(QuadTreeNodeId.COMPARATOR.compare(new Quadrant[]{Quadrant.SW, Quadrant.SW}, new Quadrant[]{Quadrant.SW, Quadrant.SW}) == 0);
    }

    @Test
    public void testCompare_equal() {
        QuadTreeNodeId q1 = new QuadTreeNodeId("aaa", new Quadrant[]{Quadrant.SW});
        QuadTreeNodeId q2 = new QuadTreeNodeId("bb", new Quadrant[]{Quadrant.SW, Quadrant.SW});

        //uses the name to determine order

        assertTrue(q1.compareTo(q2) < 0);
        assertTrue(q2.compareTo(q1) > 0);
    }
}
