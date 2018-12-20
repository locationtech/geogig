/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;

public class RevFeatureBuilderTest {

    private SimpleFeature points1;

    public @Before void before() throws SchemaException {
        SimpleFeatureType pointsType = DataUtilities.createType("Points",
                "sp:String,ip:Integer,pp:Point:srid=4326");
        points1 = feature(pointsType, "Points.1", "StringProp1_1", new Integer(1000), "POINT(1 1)");

    }

    protected SimpleFeature feature(SimpleFeatureType type, String id, Object... values) {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (type.getDescriptor(i) instanceof GeometryDescriptor) {
                if (value instanceof String) {
                    value = geom((String) value);
                }
            }
            builder.set(i, value);
        }
        return builder.buildFeature(id);
    }

    protected Geometry geom(String wkt) {
        try {
            return new WKTReader2().read(wkt);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testNullFeature() throws Exception {
        try {
            RevFeature.builder().build((Feature) null);
            fail("expected NullPointerException on null feature");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage(),
                    e.getMessage().contains("feature is marked @NonNull but is null"));
        }
    }

    @Test
    public void testBuildEmpty() throws Exception {

        RevFeature emptyFeature = RevFeature.builder().build();
        assertNotNull(emptyFeature);
        assertTrue(emptyFeature.getValues().isEmpty());

        assertEquals(ObjectId.valueOf("0aaf76f425c6e0f43a36197de768e67d9e035abb"),
                emptyFeature.getId());
    }

    @Test
    public void testBuildWithAddValue() throws Exception {
        SimpleFeature f = (SimpleFeature) points1;
        RevFeature feature = RevFeature.builder().build(f);

        RevFeatureBuilder b = RevFeature.builder();
        for (int i = 0; i < f.getAttributeCount(); i++) {
            b.addValue(f.getAttribute(i));
        }
        RevFeature built = b.build();
        assertEquals(feature, built);

        for (int i = 0; i < f.getAttributeCount(); i++) {
            assertEquals(f.getAttribute(i), built.getValues().get(i).orNull());
        }
    }

    @Test
    public void testBuildWithAddProperty() throws Exception {
        SimpleFeature f = (SimpleFeature) points1;
        RevFeature feature = RevFeature.builder().build(f);

        RevFeatureBuilder b = RevFeature.builder();
        for (Property p : f.getProperties()) {
            b.addProperty(p);
        }
        RevFeature built = b.build();
        assertEquals(feature, built);

        for (int i = 0; i < f.getAttributeCount(); i++) {
            assertEquals(f.getAttribute(i), built.getValues().get(i).orNull());
        }
    }

    @Test
    public void testReset() {
        SimpleFeature f = (SimpleFeature) points1;
        RevFeature feature = RevFeature.builder().build(f);

        RevFeatureBuilder b = RevFeature.builder();
        b.addValue(1000);
        b.addValue("str");
        b.reset();
        for (Property p : f.getProperties()) {
            b.addProperty(p);
        }
        RevFeature built = b.build();
        assertEquals(feature, built);

        for (int i = 0; i < f.getAttributeCount(); i++) {
            assertEquals(f.getAttribute(i), built.getValues().get(i).orNull());
        }
    }

    @Test
    public void testAddAll() {
        SimpleFeature f = (SimpleFeature) points1;
        RevFeature feature = RevFeature.builder().build(f);

        RevFeatureBuilder b = RevFeature.builder();
        b.addAll(f.getAttributes());

        RevFeature builtWithList = b.build();
        assertEquals(feature, builtWithList);

        b.reset();
        b.addAll(f.getAttributes().toArray(new Object[f.getAttributeCount()]));
        RevFeature builtWithArray = b.build();
        assertEquals(feature, builtWithArray);

        for (int i = 0; i < f.getAttributeCount(); i++) {
            assertEquals(f.getAttribute(i), builtWithList.getValues().get(i).orNull());
        }
    }

    @Test
    public void testEnforcesPolygonNormalization() throws Exception {
        // outer ring in cw order, inner rings in ccw order
        String normalizedWKT = "POLYGON((0 0, 0 9, 9 9, 9 0, 0 0), (3 3, 6 3, 6 6, 3 6, 3 3))";
        // outer ring in ccw order, inner rings in cc order
        String reversedWKT = "POLYGON((0 0, 9 0, 9 9, 0 9, 0 0), (3 3, 3 6, 6 6, 6 3, 3 3))";

        Geometry normalized = new WKTReader().read(normalizedWKT);
        Geometry reversed = new WKTReader().read(reversedWKT);

        assertTrue(normalized.equalsExact(normalized.norm()));
        assertFalse(reversed.equalsExact(reversed.norm()));

        RevFeatureBuilder builder = RevFeature.builder();
        RevFeature norm = builder.addValue(normalized).build();
        RevFeature rev = builder.reset().addValue(reversed).build();

        Geometry expected = (Geometry) norm.getValues().get(0).get();
        Geometry actual = (Geometry) rev.getValues().get(0).get();

        assertTrue(normalized.equalsExact(expected));
        assertTrue(normalized.equalsExact(actual));
    }

    @Test
    public void testEnforcesPolygonNormalization2() throws Exception {
        // outer ring in cw order, inner rings in ccw order
        String normalizedWKT = "GEOMETRYCOLLECTION("//
                + " POINT(2 2), LINESTRING(5 0, 0 0),"//
                + " POLYGON((0 0, 0 9, 9 9, 9 0, 0 0), (3 3, 6 3, 6 6, 3 6, 3 3))"//
                + ")";
        // outer ring in ccw order, inner rings in cc order
        String reversedWKT = "GEOMETRYCOLLECTION("//
                + " POINT(2 2), LINESTRING(5 0, 0 0),"//
                + " POLYGON((0 0, 9 0, 9 9, 0 9, 0 0), (3 3, 3 6, 6 6, 6 3, 3 3))"//
                + ")";

        Geometry normalized = new WKTReader().read(normalizedWKT);
        Geometry reversed = new WKTReader().read(reversedWKT);

        // preflight assertions
        assertFalse(normalized.equalsExact(normalized.norm()));// the linestring is not normalized
        // but the polygon is
        assertTrue(normalized.getGeometryN(2).equalsExact(normalized.getGeometryN(2).norm()));

        assertFalse(reversed.getGeometryN(2).equalsExact(reversed.getGeometryN(2).norm()));

        // make sure RevFeatureBuilder normalized only the polygon
        RevFeatureBuilder builder = RevFeature.builder();
        RevFeature norm = builder.addValue(normalized).build();
        RevFeature rev = builder.reset().addValue(reversed).build();

        Geometry expected = (Geometry) norm.getValues().get(0).get();
        Geometry actual = (Geometry) rev.getValues().get(0).get();

        assertTrue(normalized.equalsExact(expected));
        assertTrue(normalized.equalsExact(actual));
    }
}
