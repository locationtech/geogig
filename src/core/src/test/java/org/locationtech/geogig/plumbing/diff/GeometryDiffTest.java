/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;

import com.google.common.collect.Lists;

public class GeometryDiffTest {

    @Test
    public void testModifiedMultiPolygon() throws Exception {
        int NUM_COORDS = 10;
        Random rand = new Random();
        List<Coordinate> list = Lists.newArrayList();
        for (int i = 0; i < NUM_COORDS; i++) {
            list.add(new Coordinate(rand.nextInt(), rand.nextInt()));
        }
        Geometry oldGeom = new WKTReader().read(
                "MULTIPOLYGON (((40 40, 20 45, 45 30, 40 40)),((20 35, 45 10, 30 5, 10 30, 20 35),(30 20, 20 25, 20 15, 30 20)))");
        Geometry newGeom = new WKTReader().read(
                "MULTIPOLYGON (((40 40, 20 45, 45 30, 40 40)),((20 35, 45 20, 30 5, 10 10, 10 30, 20 35)))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(oldGeom, newGeom);
        LCSGeometryDiffImpl deserializedDiff = new LCSGeometryDiffImpl(diff.asText());
        assertEquals(diff, deserializedDiff);
        assertEquals("4 point(s) deleted, 1 new point(s) added, 1 point(s) moved", diff.toString());
        Geometry resultingGeom = diff.applyOn(oldGeom);
        assertEquals(newGeom, resultingGeom);
    }

    @Test
    public void testModifiedMultiLineString() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 35, 45 30, 40 40),(20 35, 45 20, 30 15, 10 10, 10 30, 20 35),(10 10, 20 20, 35 30))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(oldGeom, newGeom);
        LCSGeometryDiffImpl deserializedDiff = new LCSGeometryDiffImpl(diff.asText());
        assertEquals(diff, deserializedDiff);
        assertEquals("0 point(s) deleted, 4 new point(s) added, 3 point(s) moved", diff.toString());
        Geometry resultingGeom = diff.applyOn(oldGeom);
        assertEquals(newGeom, resultingGeom);
    }

    @Test
    public void testNoOldGeometry() throws Exception {
        Geometry newGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 35, 45 30, 40 40),(20 35, 45 20, 30 15, 10 10, 10 30, 20 35),(10 10, 20 20, 35 30))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl((Geometry) null, newGeom);
        LCSGeometryDiffImpl deserializedDiff = new LCSGeometryDiffImpl(diff.asText());
        assertEquals(diff, deserializedDiff);
        assertEquals("0 point(s) deleted, 13 new point(s) added, 0 point(s) moved",
                diff.toString());
    }

    @Test
    public void testNoNewGeometry() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(oldGeom, (Geometry) null);
        LCSGeometryDiffImpl deserializedDiff = new LCSGeometryDiffImpl(diff.asText());
        assertEquals(diff, deserializedDiff);
        assertEquals("9 point(s) deleted, 0 new point(s) added, 0 point(s) moved", diff.toString());
        Geometry resultingGeom = diff.applyOn(oldGeom);
        assertNull(resultingGeom);
    }

    @Test
    public void testDoubleReverseEquality() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 35, 45 30, 40 40),(20 35, 45 20, 30 15, 10 10, 10 30, 20 35),(10 10, 20 20, 35 30))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(oldGeom, newGeom);
        LCSGeometryDiffImpl diff2 = diff.reversed().reversed();
        assertEquals(diff, diff2);
    }

    @Test
    public void testCanApply() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 35, 45 30, 40 40),(20 35, 45 20, 30 15, 10 10, 10 30, 20 35),(10 10, 20 20, 35 30))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(oldGeom, newGeom);
        Geometry oldGeomModified = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 41),(20 35, 45 10, 30 5, 10 30, 20 35))");
        assertTrue(diff.canBeAppliedOn(oldGeomModified));
        Geometry oldGeomModified2 = new WKTReader().read("MULTILINESTRING ((40 40, 10 10))");
        assertFalse(diff.canBeAppliedOn(oldGeomModified2));
    }

    @Test
    public void testConflict() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45),(20 35, 45 10, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(oldGeom, newGeom);
        Geometry newGeom2 = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 41 33, 25 25),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(oldGeom, newGeom2);
        assertTrue(diff.conflicts(diff2));
    }

    @Test
    public void testConflictEditedSamePoint() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 48 32, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(oldGeom, newGeom);
        Geometry newGeom2 = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 41 33, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(oldGeom, newGeom2);
        assertTrue(diff.conflicts(diff2));
    }

    @Test
    public void testConflictPatcheable() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 35, 30 30),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(oldGeom, newGeom);
        Geometry newGeom2 = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 31 6, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(oldGeom, newGeom2);

        // it's a conflict for the sake of merging branches
        assertTrue(diff.conflicts(diff2));

        // yet can be merged for the sake of applying a patch
        assertTrue(diff2.canBeAppliedOn(newGeom));
        Geometry merged = diff2.applyOn(newGeom);
        assertNotNull(merged);
        assertEquals(
                "MULTILINESTRING ((40 40, 20 45, 45 35, 30 30), (20 35, 45 10, 31 6, 10 30, 20 35))",
                merged.toText());
    }

    @Test
    public void testNoConflictAddingPoints() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 10 10, 20 45, 45 30, 30 30),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(oldGeom, newGeom);
        Geometry newGeom2 = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 31 6, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(oldGeom, newGeom2);

        // the diff is conflicting
        assertTrue(diff.conflicts(diff2));

        // yet it can be applied for patching
        assertTrue(diff2.canBeAppliedOn(newGeom));
        Geometry merged = diff2.applyOn(newGeom);
        assertNotNull(merged);
        assertEquals(
                "MULTILINESTRING ((40 40, 10 10, 20 45, 45 30, 30 30), (20 35, 45 10, 31 6, 10 30, 20 35))",
                merged.toText());
    }

    @Test
    public void testConflictRemovingPoints() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(oldGeom, newGeom);
        Geometry newGeom2 = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 31 6, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(oldGeom, newGeom2);

        // the diff reports a conflict
        assertTrue(diff.conflicts(diff2));

        // yet it can be applied for patching
        assertTrue(diff2.canBeAppliedOn(newGeom));
        Geometry merged = diff2.applyOn(newGeom);
        assertNotNull(merged);
        Geometry expected = new WKTReader().read(
                "MULTILINESTRING ((40 40, 45 30, 40 40), (20 35, 45 10, 31 6, 10 30, 20 35))");
        assertEquals(expected, merged);
    }

    @Test
    public void testNoConflictIfSameDiff() throws Exception {
        Geometry oldGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader().read(
                "MULTILINESTRING ((40 40, 20 45, 45 35, 30 30),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(oldGeom, newGeom);
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(oldGeom, newGeom);
        assertFalse(diff.conflicts(diff2));
    }

    @Test
    public void testSameMultiPolygon() throws Exception {
        String wkt = "MULTIPOLYGON (((-75.0740073 38.6938098, -75.0739683 38.6935296, "
                + "-75.0745695 38.6934786, -75.0745824 38.6935715, -75.0741091 38.6936117, "
                + "-75.0741352 38.6937989, -75.0740073 38.6938098)))";

        Geometry oldGeom = new WKTReader().read(wkt);
        Geometry newGeom = new WKTReader().read(wkt);
        assertTrue(oldGeom.equalsExact(newGeom));

        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(oldGeom, newGeom);
        String asText = diff.asText();
        LCSGeometryDiffImpl deserializedDiff = new LCSGeometryDiffImpl(asText);
        assertEquals(diff, deserializedDiff);
        assertEquals("0 point(s) deleted, 0 new point(s) added, 0 point(s) moved", diff.toString());
        Geometry resultingGeom = diff.applyOn(oldGeom);
        assertEquals(newGeom, resultingGeom);
    }

    @Test
    public void testNoConflictIfSameDiff2() throws Exception {
        String wkt = "MULTIPOLYGON (((-75.0740073 38.6938098, -75.0739683 38.6935296, "
                + "-75.0745695 38.6934786, -75.0745824 38.6935715, -75.0741091 38.6936117, "
                + "-75.0741352 38.6937989, -75.0740073 38.6938098)))";

        Geometry oldGeom = new WKTReader().read(wkt);
        Geometry newGeom = new WKTReader().read(wkt);
        assertTrue(oldGeom.equalsExact(newGeom));

        GeometryAttributeDiff diff = new GeometryAttributeDiff(oldGeom, newGeom);
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(oldGeom, newGeom);
        assertFalse(diff.conflicts(diff2));
    }
}
