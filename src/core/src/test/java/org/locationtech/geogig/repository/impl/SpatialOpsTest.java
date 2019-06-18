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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;
import org.locationtech.geogig.crs.CRS;
import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Envelope;

/**
 *
 */
public class SpatialOpsTest {

    public @Test void testBoundsOf() throws Exception {

        final CoordinateReferenceSystem crsLonLat = CRS.decode("EPSG:26918");
        // validate the bounds
        final Optional<Envelope> aovLonLat = SpatialOps.boundsOf(crsLonLat);
        assertTrue("Envelope should not be null", aovLonLat.isPresent());
        assertFalse("Envelope should not be null", aovLonLat.get().isNull());
    }

    public @Test void testBoundsOfEpsgVsEpsgForceLonLat() throws Exception {
        final CoordinateReferenceSystem crsLatLon = CRS.decode("urn:ogc:def:crs:EPSG::4326");
        final CoordinateReferenceSystem crsLonLat = CRS.decode("EPSG:4326");

        final Optional<Envelope> aov = SpatialOps.boundsOf(crsLatLon);
        final Optional<Envelope> aovLonLat = SpatialOps.boundsOf(crsLonLat);
        assertTrue(aov.isPresent());
        assertTrue(aovLonLat.isPresent());
        assertNotEquals(aov.get(), aovLonLat.get());
    }
}
