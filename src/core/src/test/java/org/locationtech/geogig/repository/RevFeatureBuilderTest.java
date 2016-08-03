/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.locationtech.geogig.model.RevFeatureBuilder.builder;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;

public class RevFeatureBuilderTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {

    }

    @Test
    public void testNullFeature() throws Exception {
        try {
            RevFeatureBuilder.build((Feature) null);
            fail("expected IllegalStateException on null feature");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No feature set"));
        }
    }

    @Test
    public void testBuildEmpty() throws Exception {

        RevFeature emptyFeature = RevFeatureBuilder.builder().build();
        assertNotNull(emptyFeature);
        assertTrue(emptyFeature.getValues().isEmpty());

        assertEquals(ObjectId.valueOf("0aaf76f425c6e0f43a36197de768e67d9e035abb"),
                emptyFeature.getId());
    }

    @Test
    public void testBuildWithAddValue() throws Exception {
        SimpleFeature f = (SimpleFeature) points1;
        RevFeature feature = RevFeatureBuilder.build(f);

        RevFeatureBuilder b = builder();
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
        RevFeature feature = RevFeatureBuilder.build(f);

        RevFeatureBuilder b = builder();
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
        RevFeature feature = RevFeatureBuilder.build(f);

        RevFeatureBuilder b = builder();
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
        RevFeature feature = RevFeatureBuilder.build(f);

        RevFeatureBuilder b = builder();
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

        RevFeatureBuilder builder = builder();
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
        RevFeatureBuilder builder = builder();
        RevFeature norm = builder.addValue(normalized).build();
        RevFeature rev = builder.reset().addValue(reversed).build();

        Geometry expected = (Geometry) norm.getValues().get(0).get();
        Geometry actual = (Geometry) rev.getValues().get(0).get();

        assertTrue(normalized.equalsExact(expected));
        assertTrue(normalized.equalsExact(actual));
    }
}
