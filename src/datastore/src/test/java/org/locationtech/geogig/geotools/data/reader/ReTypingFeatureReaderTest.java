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


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReTypingFeatureReaderTest {

    @Test
    public void testRetyping() throws Exception {
        String typeSchema1 = "geom:Point,tag:String,featureNumber:Integer";
        String typeSchema2 = "geom:Point,featureNumber:Integer";

        SimpleFeatureType featureType1 = DataUtilities.createType("typeSchema1", typeSchema1);
        SimpleFeatureType featureType2 = DataUtilities.createType("typeSchema2", typeSchema2);
        GeometryFactory gf = new GeometryFactory();


        SimpleFeature f = DataUtilities.template(featureType1, "id.1");
        f.setAttribute("geom", gf.createPoint(new Coordinate(1, 2)));
        f.setAttribute("tag", "mytag");
        f.setAttribute("featureNumber", 123);

        FeatureReader<SimpleFeatureType, SimpleFeature> underlying = DataUtilities.reader(new SimpleFeature[]{f});

        ReTypingFeatureReader retyping = new ReTypingFeatureReader(underlying, featureType1, featureType2);

        assertTrue(retyping.hasNext());
        SimpleFeature f2 = retyping.next();

        assertEquals(f2.getID(), f.getID());
        assertEquals(f2.getAttribute("geom"), f.getAttribute("geom"));
        assertEquals(f2.getAttribute("featureNumber"), f.getAttribute("featureNumber"));

        assertNull(f2.getAttribute("tag"));

        assertFalse(retyping.hasNext());
    }
}
