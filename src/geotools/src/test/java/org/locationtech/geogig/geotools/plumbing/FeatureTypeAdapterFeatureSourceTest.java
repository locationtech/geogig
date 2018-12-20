/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import java.io.IOException;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.Hints;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.AbstractReferenceSystem;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.util.Assert;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class FeatureTypeAdapterFeatureSourceTest {

    // these refer to the same CRS, but are a bit different
    private static String PROJ_EPSG4326 = "EPSG:4326"; // other axis order -
                                                       // "urn:ogc:def:crs:EPSG:4326";

    private static String PROJ_EQUIV_EPSG4326 = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.01745329251994328,AUTHORITY[\"EPSG\",\"9122\"]],AUTHORITY[\"EPSG\",\"4326\"]]";

    private static AbstractReferenceSystem PROJ_EPSG4326_CRS;

    private static AbstractReferenceSystem PROJ_EQUIV_EPSG4326_CRS;

    GeometryFactory gf = new GeometryFactory();

    @BeforeClass
    public static void setup() throws NoSuchAuthorityCodeException, FactoryException {
        Hints hints = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);
        CRSAuthorityFactory factory = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG",
                hints);

        PROJ_EPSG4326_CRS = (AbstractReferenceSystem) factory
                .createCoordinateReferenceSystem(PROJ_EPSG4326);
        PROJ_EQUIV_EPSG4326_CRS = (AbstractReferenceSystem) CRS.parseWKT(PROJ_EQUIV_EPSG4326);
    }

    @Test
    public void testCRSesAreSlightlyDifferentButCompatible() throws FactoryException {
        // these CRS are not exactly equal, but mostly equal...
        Assert.isTrue(!PROJ_EPSG4326_CRS.equals(PROJ_EQUIV_EPSG4326_CRS, true));
        Assert.isTrue(PROJ_EPSG4326_CRS.equals(PROJ_EQUIV_EPSG4326_CRS, false));
    }

    @Test
    public void testFeatureTypeAdapterHandlesSimilarCRSes() throws SchemaException, IOException {
        // create two FT - only difference is they have different (but compatible) CRS.
        SimpleFeatureType schema1 = createSampleType((CoordinateReferenceSystem) PROJ_EPSG4326_CRS);
        SimpleFeatureType schema2 = createSampleType(
                (CoordinateReferenceSystem) PROJ_EQUIV_EPSG4326_CRS);

        // make 2 features for the first schema
        SimpleFeature f1 = DataUtilities.template(schema1);
        f1.setDefaultGeometry(gf.createPoint(new Coordinate(1, 1)));

        SimpleFeature f2 = DataUtilities.template(schema1);
        f2.setDefaultGeometry(gf.createPoint(new Coordinate(2, 2)));

        // create a FeatureSource for these 2 features
        SimpleFeature[] features = new SimpleFeature[] { f1, f2 };
        SimpleFeatureSource fs = DataUtilities.source(features);

        // this is what we are testing - adapting between the two different (but compatible) CRS.
        FeatureTypeAdapterFeatureSource ftafs = new FeatureTypeAdapterFeatureSource(fs, schema2);

        // get the resulting features
        List<SimpleFeature> newFeatures = DataUtilities.list(ftafs.getFeatures());

        //verify correct
        Assert.isTrue(f1.equals(newFeatures.get(0)));
        Assert.isTrue(f2.equals(newFeatures.get(1)));
    }

    // creates a simple feature type
    public SimpleFeatureType createSampleType(CoordinateReferenceSystem crs) {
        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        ftb.setName("POINT1");
        ftb.add("name", String.class);
        ftb.add("geom", Geometry.class, crs);
        ftb.add("att", Integer.class);
        ftb.setDefaultGeometry("geom");
        return ftb.buildFeatureType();
    }

}
