/*
 *  Copyright (c) 2017 Boundless and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Distribution License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/edl-v10.html
 *
 *  Contributors:
 *  Morgan Thompson (Boundless) - initial implementation
 *  Alex Goudine (Boundless)
 */

package org.locationtech.geogig.data;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.porcelain.CRSException;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;

public class EPSGBoundsCalcTest extends RepositoryTestCase {

    private static final double TOLERANCE = 0.000000002;

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "mthompson");
        injector.configDatabase().put("user.email", "mthompson@boundlessgeo.com");
    }

    private void assertEnvelopesEqual(Envelope expected, Envelope actual) {
        assertEquals("MinX values not equal", expected.getMinX(), actual.getMinX(), TOLERANCE);
        assertEquals("MaxX values not equal", expected.getMaxX(), actual.getMaxX(), TOLERANCE);
        assertEquals("MinY values not equal", expected.getMinY(), actual.getMinY(), TOLERANCE);
        assertEquals("MaxY values not equal", expected.getMaxY(), actual.getMaxY(), TOLERANCE);
    }

    @Test
    public void epsgTest() throws Exception {

        String[] testArray = {"EPSG:3031","EPSG:26910","EPSG:3857","EPSG:3412","EPSG:3411"};
        Envelope[] testEnvelopes = new Envelope[5];

        testEnvelopes[0] = new Envelope(-3333134.027630277, 3333134.027630277, -3333134.027630277, 3333134.027630277);
        testEnvelopes[1] = new Envelope(212172.22206537757, 788787.632995196, 3378624.2031936757, 9083749.435906317);
        testEnvelopes[2] = new Envelope(-2.0037508342789244E7, 2.0037508342789244E7, -2.00489661040146E7, 2.0048966104014594E7);
        testEnvelopes[3] = new Envelope(-3323231.542684214, 3323231.542684214, -3323231.542684214, 3323231.542684214);
        testEnvelopes[4] = new Envelope(-2349879.5592850395, 2349879.5592850395, -2349879.5592850395, 2349879.5592850395);

        Envelope bounds;
        for (int i=0; i<testArray.length; i++) {
            bounds = new EPSGBoundsCalc().getCRSBounds(testArray[i]);
            assertEnvelopesEqual(testEnvelopes[i], bounds);
        }
    }

    @Test(expected=NoSuchAuthorityCodeException.class)
    public void googleProjectionTest() throws Exception{
        new EPSGBoundsCalc().getCRSBounds("EPSG:900913");
    }

    @Test(expected=NoSuchAuthorityCodeException.class)
    public void badCodeTest() throws Exception{
        new EPSGBoundsCalc().getCRSBounds("random stuff!!!");
    }

    @Test(expected=CRSException.class)
    public void noCRSMatch() throws Exception {
        String noEPSGMatchWKT = "GEOGCS[\"GCS_WGS_1985\",DATUM[\"D_WGS_1985\",SPHEROID[\"WGS_1985\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.017453292519943295]]";

        CoordinateReferenceSystem origCrs = CRS.parseWKT(noEPSGMatchWKT);

        String typeName = "noCRSMatchType";
        String id = "Points.6";

        //create the feature with the noEPSGMatchWKT CRS
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setCRS(origCrs);
        builder.add("geom", Point.class);
        builder.setName(typeName);

        //need to make this a feature, so it can be used with the insertAndAdd command
        SimpleFeatureType type = builder.buildFeatureType();
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
        Feature f = featureBuilder.buildFeature(id);

        insertAndAdd(f);

        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
            .setRefSpec("WORK_HEAD:" + NodeRef.appendChild(typeName, id)).call();

        RevFeatureType ft = null;
        if (featureType.isPresent())
            ft = featureType.get();

        new EPSGBoundsCalc().getCRSBounds(ft);
    }

    @Test
    public void CRSMatch() throws Exception {

        String esri26918 = "PROJCS[\"NAD_1983_UTM_Zone_18N\", GEOGCS[\"GCS_North_American_1983\", DATUM[\"D_North_American_1983\", SPHEROID[\"GRS_1980\", 6378137.0, 298.257222101]], PRIMEM[\"Greenwich\", 0.0], UNIT[\"degree\", 0.017453292519943295], AXIS[\"Longitude\", EAST], AXIS[\"Latitude\", NORTH]], PROJECTION[\"Transverse_Mercator\"], PARAMETER[\"central_meridian\", -75.0], PARAMETER[\"latitude_of_origin\", 0.0], PARAMETER[\"scale_factor\", 0.9996], PARAMETER[\"false_easting\", 500000.0], PARAMETER[\"false_northing\", 0.0], UNIT[\"m\", 1.0], AXIS[\"x\", EAST], AXIS[\"y\", NORTH]]";
        CoordinateReferenceSystem origCrs = CRS.parseWKT(esri26918);

        String typeName = "CRSMatchType";
        String id = "Points.6";

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setCRS(origCrs);
        builder.add("geom", Point.class);
        builder.setName(typeName);

        SimpleFeatureType type = builder.buildFeatureType();
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
        Feature f = featureBuilder.buildFeature(id);

        insertAndAdd(f);

        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
            .setRefSpec("WORK_HEAD:" + NodeRef.appendChild(typeName, id)).call();

        RevFeatureType ft = null;
        if (featureType.isPresent())
            ft = featureType.get();

        //double check the actual bounds
        Envelope actual = new EPSGBoundsCalc().getCRSBounds(ft);
        Envelope expected = new Envelope(205723.76927073707, 794276.2307292629, 3128220.0383561817, 9329005.182379141);
        assertEnvelopesEqual(expected, actual);
    }
}
