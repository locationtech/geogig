/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.plumbing.diff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;

public class GeometryDiffTest {

    @Test
    public void testModifiedMultiPolygon() throws Exception {
        int NUM_COORDS = 10;
        Random rand = new Random();
        List<Coordinate> list = Lists.newArrayList();
        for (int i = 0; i < NUM_COORDS; i++) {
            list.add(new Coordinate(rand.nextInt(), rand.nextInt()));
        }
        Geometry oldGeom = new WKTReader()
                .read("MULTIPOLYGON (((40 40, 20 45, 45 30, 40 40)),((20 35, 45 10, 30 5, 10 30, 20 35),(30 20, 20 25, 20 15, 30 20)))");
        Geometry newGeom = new WKTReader()
                .read("MULTIPOLYGON (((40 40, 20 45, 45 30, 40 40)),((20 35, 45 20, 30 5, 10 10, 10 30, 20 35)))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(Optional.of(oldGeom),
                Optional.of(newGeom));
        LCSGeometryDiffImpl deserializedDiff = new LCSGeometryDiffImpl(diff.asText());
        assertEquals(diff, deserializedDiff);
        assertEquals("4 point(s) deleted, 1 new point(s) added, 1 point(s) moved", diff.toString());
        Optional<Geometry> resultingGeom = diff.applyOn(Optional.of(oldGeom));
        assertEquals(newGeom, resultingGeom.get());
    }

    @Test
    public void testModifiedMultiLineString() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 35, 45 30, 40 40),(20 35, 45 20, 30 15, 10 10, 10 30, 20 35),(10 10, 20 20, 35 30))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(Optional.of(oldGeom),
                Optional.of(newGeom));
        LCSGeometryDiffImpl deserializedDiff = new LCSGeometryDiffImpl(diff.asText());
        assertEquals(diff, deserializedDiff);
        assertEquals("0 point(s) deleted, 4 new point(s) added, 3 point(s) moved", diff.toString());
        Optional<Geometry> resultingGeom = diff.applyOn(Optional.of(oldGeom));
        assertEquals(newGeom, resultingGeom.get());
    }

    @Test
    public void testNoOldGeometry() throws Exception {
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 35, 45 30, 40 40),(20 35, 45 20, 30 15, 10 10, 10 30, 20 35),(10 10, 20 20, 35 30))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(Optional.fromNullable((Geometry) null),
                Optional.of(newGeom));
        LCSGeometryDiffImpl deserializedDiff = new LCSGeometryDiffImpl(diff.asText());
        assertEquals(diff, deserializedDiff);
        assertEquals("0 point(s) deleted, 13 new point(s) added, 0 point(s) moved", diff.toString());
    }

    @Test
    public void testNoNewGeometry() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(Optional.of(oldGeom),
                Optional.fromNullable((Geometry) null));
        LCSGeometryDiffImpl deserializedDiff = new LCSGeometryDiffImpl(diff.asText());
        assertEquals(diff, deserializedDiff);
        assertEquals("9 point(s) deleted, 0 new point(s) added, 0 point(s) moved", diff.toString());
        Optional<Geometry> resultingGeom = diff.applyOn(Optional.of(oldGeom));
        assertFalse(resultingGeom.isPresent());
    }

    @Test
    public void testDoubleReverseEquality() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 35, 45 30, 40 40),(20 35, 45 20, 30 15, 10 10, 10 30, 20 35),(10 10, 20 20, 35 30))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(Optional.of(oldGeom),
                Optional.of(newGeom));
        LCSGeometryDiffImpl diff2 = diff.reversed().reversed();
        assertEquals(diff, diff2);
    }

    @Test
    public void testCanApply() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 35, 45 30, 40 40),(20 35, 45 20, 30 15, 10 10, 10 30, 20 35),(10 10, 20 20, 35 30))");
        LCSGeometryDiffImpl diff = new LCSGeometryDiffImpl(Optional.of(oldGeom),
                Optional.of(newGeom));
        Geometry oldGeomModified = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 41),(20 35, 45 10, 30 5, 10 30, 20 35))");
        assertTrue(diff.canBeAppliedOn(Optional.of(oldGeomModified)));
        Geometry oldGeomModified2 = new WKTReader().read("MULTILINESTRING ((40 40, 10 10))");
        assertFalse(diff.canBeAppliedOn(Optional.of(oldGeomModified2)));
    }

    @Test
    public void testConflict() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45),(20 35, 45 10, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom));
        Geometry newGeom2 = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 41 33, 25 25),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom2));
        assertTrue(diff.conflicts(diff2));
    }

    @Test
    public void testConflictEditedSamePoint() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 48 32, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom));
        Geometry newGeom2 = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 41 33, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom2));
        assertTrue(diff.conflicts(diff2));
    }

    @Test
    public void testNoConflict() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 35, 30 30),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom));
        Geometry newGeom2 = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 31 6, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom2));
        assertFalse(diff.conflicts(diff2));
        Optional<?> merged = diff2.applyOn(Optional.of(newGeom));
        assertTrue(merged.isPresent());
        Geometry mergedGeom = (Geometry) merged.get();
        assertEquals(
                "MULTILINESTRING ((40 40, 20 45, 45 35, 30 30), (20 35, 45 10, 31 6, 10 30, 20 35))",
                mergedGeom.toText());
    }

    @Test
    public void testNoConflictAddingPoints() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 10 10, 20 45, 45 30, 30 30),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom));
        Geometry newGeom2 = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 31 6, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom2));
        assertFalse(diff.conflicts(diff2));
        Optional<?> merged = diff2.applyOn(Optional.of(newGeom));
        assertTrue(merged.isPresent());
        Geometry mergedGeom = (Geometry) merged.get();
        assertEquals(
                "MULTILINESTRING ((40 40, 10 10, 20 45, 45 30, 30 30), (20 35, 45 10, 31 6, 10 30, 20 35))",
                mergedGeom.toText());
    }

    @Test
    public void testNoConflictRemovingPoints() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom));
        Geometry newGeom2 = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 31 6, 10 30, 20 35))");
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom2));
        assertFalse(diff.conflicts(diff2));
        Optional<?> merged = diff2.applyOn(Optional.of(newGeom));
        assertTrue(merged.isPresent());
        Geometry mergedGeom = (Geometry) merged.get();
        assertEquals("MULTILINESTRING ((40 40, 45 30, 40 40), (20 35, 45 10, 31 6, 10 30, 20 35))",
                mergedGeom.toText());
    }

    @Test
    public void testNoConflictIfSameDiff() throws Exception {
        Geometry oldGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 30, 40 40),(20 35, 45 10, 30 5, 10 30, 20 35))");
        Geometry newGeom = new WKTReader()
                .read("MULTILINESTRING ((40 40, 20 45, 45 35, 30 30),(20 35, 45 10, 30 5, 10 30, 20 35))");
        GeometryAttributeDiff diff = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom));
        GeometryAttributeDiff diff2 = new GeometryAttributeDiff(Optional.of(oldGeom),
                Optional.of(newGeom));
        assertFalse(diff.conflicts(diff2));
    }

}
