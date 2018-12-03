/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.geotools.referencing.CRS;
import org.junit.Test;
import org.locationtech.jts.geom.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 *
 */
public class SpatialOpsTest {

    private static final String CUSTOM_EPSG_26918 = "PROJCS[\"NAD_1983_UTM_Zone_18N\",GEOGCS[\"GCS_North_American_1983\",DATUM[\"D_North_American_1983\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",500000.0],PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",-75.0],PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0]]";

    @Test
    public void test_boundsOf() throws Exception {
        // EPSG:26918 CRS
        final CoordinateReferenceSystem epsg26918 = CRS.decode("EPSG:26918");
        // domain of validity should not be null
        validateCRS(epsg26918, true);
        // validate the bounds
        final Envelope envelopeExpected = SpatialOps.boundsOf(epsg26918);
        assertNotNull("Envelope should not be null", envelopeExpected);

        // Custom projection that should match EPSG:26918
        CoordinateReferenceSystem custom26918 = CRS.parseWKT(CUSTOM_EPSG_26918);
        // domain of validity is null for this custom CRS
        validateCRS(custom26918, false);
        // validate the bounds
        final Envelope envelopeActual = SpatialOps.boundsOf(custom26918);
        assertNotNull("Envelope should not be null", envelopeActual);
        // envelopes should match
        assertEquals("Envelopes should match", envelopeExpected, envelopeActual);
    }

    @Test
    public void test_epsg4326() throws Exception {
        CoordinateReferenceSystem crs = CRS.decode("EPSG:4326");
        // domain of validity should not be null
        validateCRS(crs, true);
        // validate the bounds
        final Envelope envelope = SpatialOps.boundsOf(crs);
        assertNotNull("Envelope should not be null", envelope);
    }

    private void validateCRS(CoordinateReferenceSystem crs, boolean domainShouldNotBeNull) throws Exception {
        assertNotNull("CRS should not be null", crs);
        if (domainShouldNotBeNull) {
            assertNotNull("Domain of Validity should not be null", crs.getDomainOfValidity());
        } else {
            assertNull("Unexpected Domain of Validity", crs.getDomainOfValidity());
        }
    }

}
