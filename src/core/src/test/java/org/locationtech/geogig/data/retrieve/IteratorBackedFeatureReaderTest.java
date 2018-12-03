/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.data.retrieve;

import java.util.ArrayList;

import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.junit.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.util.Assert;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class IteratorBackedFeatureReaderTest {

    @Test
    public void simpleTest() throws Exception {

        SimpleFeatureType TYPE = DataUtilities.createType("location",
                "the_geom:Point:srid=4326,name:String");
        ArrayList<SimpleFeature> list = new ArrayList<SimpleFeature>();
        WKTReader2 wkt = new WKTReader2();
        list.add(SimpleFeatureBuilder.build(TYPE,
                new Object[] { (Point) wkt.read("POINT(0 0)"), "abc1" }, "id1"));
        list.add(SimpleFeatureBuilder.build(TYPE,
                new Object[] { (Point) wkt.read("POINT(1 1)"), "abc2" }, "id2"));

        try (IteratorBackedFeatureReader reader = new IteratorBackedFeatureReader(TYPE,
                list.iterator())) {
            SimpleFeature f1 = reader.next();
            SimpleFeature f2 = reader.next();

            Assert.isTrue(!reader.hasNext());
            Assert.isTrue(f1.getID() == "id1");
            Assert.isTrue(f2.getID() == "id2");
        }
    }

}
