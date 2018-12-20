/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */

package org.locationtech.geogig.geotools.data.reader;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.geotools.referencing.operation.transform.IdentityTransform;
import org.geotools.renderer.ScreenMap;
import org.junit.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.operation.TransformException;

public class ScreenMapTests {

    @Test
    public void testFeatureScreenMapPredicate() throws SchemaException {
        SimpleFeatureType lineType = DataUtilities.createType("LINE", "centerline:LineString,name:\"\",id:0");
        SimpleFeature feature1 = DataUtilities.createFeature(lineType, "LINESTRING(0.1 0.1, 0.9 0.9)|dave|7");
        SimpleFeature feature2 = DataUtilities.createFeature(lineType, "LINESTRING(0.1 0.1, 0.9 0.9)|dave|7");

        ScreenMap sm = new ScreenMap(0, 0, 100, 100, IdentityTransform.create(2));
        sm.setSpans(1, 1);

        //both are in the same pixel
        assertTrue(sm.canSimplify(((Geometry) feature1.getDefaultGeometry()).getEnvelopeInternal()));
        assertTrue(sm.canSimplify(((Geometry) feature2.getDefaultGeometry()).getEnvelopeInternal()));

        FeatureScreenMapPredicate smp = new FeatureScreenMapPredicate(sm);

        //first one goes through
        assertTrue(smp.apply(feature1));
        //second one doesn't
        assertFalse(smp.apply(feature2));
    }

    @Test
    public void testGeometryReplacer() throws SchemaException, TransformException {
        SimpleFeatureType lineType = DataUtilities.createType("LINE", "centerline:LineString,name:\"\",id:0");
        SimpleFeature feature1 = DataUtilities.createFeature(lineType, "LINESTRING(0.1 0.1, 0.9 0.9)|dave|7");
        //this feature isn't affected
        SimpleFeature feature2 = DataUtilities.createFeature(lineType, "LINESTRING(0 0,10 10)|dave|7");


        ScreenMap sm = new ScreenMap(0, 0, 100, 100, IdentityTransform.create(2));
        sm.setSpans(1, 1);

        //prep
        // sm.checkAndSet( ((Geometry) feature1.getDefaultGeometry()).getEnvelopeInternal()   );

        ScreenMapGeometryReplacer replacer = new ScreenMapGeometryReplacer(sm);

        SimpleFeature feature1b = replacer.apply(feature1);
        assertSame(feature1, feature1b);
        Geometry newGeom = (Geometry) feature1b.getDefaultGeometry();
        assertTrue(newGeom instanceof LineString);

        Envelope newGeomEnv = newGeom.getEnvelopeInternal();
        assertTrue(0 == newGeomEnv.getMinX());
        assertTrue(0 == newGeomEnv.getMinY());


        assertTrue(1 == newGeomEnv.getMaxX());
        assertTrue(1 == newGeomEnv.getMaxY());


        SimpleFeature feature2b = replacer.apply(feature2);
        assertEquals(feature2, feature2b);


    }
}
