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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.internal.ClusteringStrategyBuilder.QuadTreeClusteringStrategyBuilder.ABSOLUTE_MAX_DEPTH;
import static org.locationtech.geogig.model.internal.Quadrant.NE;
import static org.locationtech.geogig.model.internal.Quadrant.NW;
import static org.locationtech.geogig.model.internal.Quadrant.SE;
import static org.locationtech.geogig.model.internal.Quadrant.SW;

import org.geotools.geometry.jts.JTS;
import org.junit.Test;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import com.google.common.collect.Lists;

public class QuadrantTest {

    private static final GeometryFactory GF = new GeometryFactory();

    public @Test void testQuadrant() {

        Envelope eSW = SW.slice(new Envelope(0, 1, 0, 1));
        Envelope eSE = SE.slice(new Envelope(0, 1, 0, 1));
        Envelope eNW = NW.slice(new Envelope(0, 1, 0, 1));
        Envelope eNE = NE.slice(new Envelope(0, 1, 0, 1));

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

    public @Test void testQuadrantWGS84() {

        Envelope maxBounds = new Envelope(-180, 180, -90, 90);

        final Envelope eSW = SW.slice(maxBounds);
        final Envelope eNW = NW.slice(maxBounds);
        final Envelope eNE = NE.slice(maxBounds);
        final Envelope eSE = SE.slice(maxBounds);

        assertEquals(new Envelope(-180, 0, -90, 0), eSW);
        assertEquals(new Envelope(-180, 0, 0, 90), eNW);
        assertEquals(new Envelope(0, 180, 0, 90), eNE);
        assertEquals(new Envelope(0, 180, -90, 0), eSE);

        Envelope eSW2 = SW.slice(eNE);
        Envelope eNW2 = NW.slice(eNE);
        Envelope eNE2 = NE.slice(eNE);
        Envelope eSE2 = SE.slice(eNE);

        assertEquals(new Envelope(0, 90, 0, 45), eSW2);
        assertEquals(new Envelope(0, 90, 45, 90), eNW2);
        assertEquals(new Envelope(90, 180, 45, 90), eNE2);
        assertEquals(new Envelope(90, 180, 0, 45), eSE2);
    }

    public @Test void testFindMaxDepthWGS84() {
        int maxDepth;
        maxDepth = findMaxDepth(QuadTreeTestSupport.wgs84Bounds());
        assertEquals(24, maxDepth);
    }

    public @Test void testFindMaxDepthPseudoMercator() {
        int maxDepth;
        maxDepth = findMaxDepth(QuadTreeTestSupport.epsg3857Bounds());
        assertEquals(24, maxDepth);
    }

    public @Test void findBiggestMagnitudeQuadrantForMaxBounds() {

        assertEquals(NE, Quadrant.findBiggestMagnitudeQuad(new Envelope(-1, 1, -1, 1)));

        assertEquals(SW, Quadrant.findBiggestMagnitudeQuad(new Envelope(-10, 0, -10, 0)));
        assertEquals(NW, Quadrant.findBiggestMagnitudeQuad(new Envelope(-10, 0, 0, 10)));
        assertEquals(NE, Quadrant.findBiggestMagnitudeQuad(new Envelope(0, 10, 0, 10)));
        assertEquals(SE, Quadrant.findBiggestMagnitudeQuad(new Envelope(0, 10, -10, 0)));

    }

    public @Test void testFindMaxDepth() {
        assertEquals(24, findMaxDepth(new Envelope(-1, 1, -1, 1)));

        assertEquals(23, findMaxDepth(new Envelope(0, 1e7, 0, 1e7)));
        assertEquals(23, findMaxDepth(new Envelope(-1e-7, 0, -1e-7, 0)));

        assertEquals(23, findMaxDepth(new Envelope(0, 1e9, 0, 1e9)));

        assertEquals(23, findMaxDepth(
                new Envelope(Float.MIN_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MAX_VALUE)));

        assertEquals(0, findMaxDepth(new Envelope(Double.MIN_VALUE, Double.MAX_VALUE,
                Double.MIN_VALUE, Double.MAX_VALUE)));
    }

    private int findMaxDepth(Envelope e) {
        return Quadrant.findMaxDepth(e, ABSOLUTE_MAX_DEPTH);
    }

    public @Test void testSliceMaxDepthWGS84() {
        testSliceMaxDepth(QuadTreeTestSupport.wgs84Bounds());
    }

    public @Test void testSliceMaxDepthPseudoMercator() {
        testSliceMaxDepth(QuadTreeTestSupport.epsg3857Bounds());
    }

    public @Test void testSliceMaxDepth() {
        testSliceMaxDepth(new Envelope(-10, 10, -10, 10));
    }

    private void testSliceMaxDepth(final Envelope maxBounds) {
        final int maxDepth = Quadrant.findMaxDepth(maxBounds, ABSOLUTE_MAX_DEPTH);

        Envelope parent = RevObjects.makePrecise(maxBounds);
        Envelope[] qbounds;
        for (int d = 0; d < maxDepth; d++) {
            qbounds = assertSlice(parent);
            Envelope sw = qbounds[0];
            Envelope minimalEnv = RevObjects.makePrecise(new Envelope(sw.centre()));
            assertTrue("at index " + d, sw.contains(minimalEnv));
            parent = sw;
        }
    }

    private Envelope[] assertSlice(Envelope parent) {

        final Envelope sw = SW.slice(parent);
        final Envelope nw = NW.slice(parent);
        final Envelope ne = NE.slice(parent);
        final Envelope se = SE.slice(parent);

        Geometry mpoly = GF
                .buildGeometry(Lists.newArrayList(poly(sw), poly(nw), poly(ne), poly(se)));

        assertEquals(parent, mpoly.getEnvelopeInternal());

        return new Envelope[] { sw, nw, ne, ne };
    }

    private Geometry poly(Envelope e) {
        return JTS.toGeometry(e, GF);
    }
}
