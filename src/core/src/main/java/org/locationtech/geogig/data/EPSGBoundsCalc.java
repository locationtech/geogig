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

import com.vividsolutions.jts.geom.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import java.util.Collection;
import java.util.Set;

import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.metadata.extent.Extent;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.metadata.extent.GeographicExtent;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.CoordinateOperationFactory;

/**
 * Given a EPSG code, find the CRS bounds and return as an Envelope
 */
public class EPSGBoundsCalc {

    /**
     * Get the bounds of the desired CRS, uses JTS ReferencedEnvelope transform to properly
     * handle polar projections
     * @param crs the target CoordinateReferenceSystem
     * @return bounds an Envelope containing the CRS bounds
     */
    public Envelope getExtents(CoordinateReferenceSystem crs) throws Exception {

        final Extent domainOfValidity = crs.getDomainOfValidity();

        if (null == domainOfValidity) {
            throw new Exception("No domain of validity provided by CRS definition");
        }

        Collection<? extends GeographicExtent> geographicElements;
        geographicElements = domainOfValidity.getGeographicElements();

        if (null == geographicElements || geographicElements.size() != 1) {
            throw new Exception("Number of geographic elements != 1");
        }

        GeographicExtent geographicExtent = geographicElements.iterator().next();
        if (!(geographicExtent instanceof GeographicBoundingBox)) {
            throw new Exception("geographic extent is not a geographic bounding box");
        }

        if (!geographicExtent.getInclusion()) {
            throw new Exception("geographic extent is exclusive, can only deal with inclusive ones");
        }

        GeographicBoundingBox geographicBoundingBox = (GeographicBoundingBox) geographicExtent;

        double minx = geographicBoundingBox.getWestBoundLongitude();
        double miny = geographicBoundingBox.getSouthBoundLatitude();
        double maxx = geographicBoundingBox.getEastBoundLongitude();
        double maxy = geographicBoundingBox.getNorthBoundLatitude();

        CoordinateReferenceSystem wgs84LongFirst;

        try {

            wgs84LongFirst = CRS.decode("EPSG:4326", true);
            CoordinateOperationFactory coordOpFactory = CRS.getCoordinateOperationFactory(true);
            CoordinateOperation op = coordOpFactory.createOperation(wgs84LongFirst,crs);

            ReferencedEnvelope refEnvelope = new ReferencedEnvelope(minx, maxx, miny, maxy, wgs84LongFirst);
            GeneralEnvelope genEnvelope = CRS.transform(op, refEnvelope);

            double xmax = genEnvelope.getMaximum(0);
            double ymax = genEnvelope.getMaximum(1);
            double xmin = genEnvelope.getMinimum(0);
            double ymin = genEnvelope.getMinimum(1);

            Envelope envelope = new Envelope(xmin, xmax, ymin, ymax);
            return envelope;
        } catch (Exception e) {
            throw new Exception("ERROR: " + e.getMessage());
        }
    }

    /**
     * Search for the given CRS (EPSG code), return the bounds (domain of validity)
     * @param refId the input CRS
     * @return projectionBounds an Envelope describing the CRS bounds, or null if bounds are not found
     */
    public Envelope findCode(String refId) throws Exception {
        Envelope projectionBounds = null;
        CoordinateReferenceSystem crs = null;

        CRSAuthorityFactory authorityFactory = CRS.getAuthorityFactory(true);
        Set<String> authorityCodes = authorityFactory.getAuthorityCodes(CoordinateReferenceSystem.class);

        if (refId.contains("WGS 84")) {
            crs = authorityFactory.createCoordinateReferenceSystem("EPSG:4326");
            projectionBounds = getExtents(crs);
        } else {
            for (String code : authorityCodes) {
                if (code.startsWith("EPSG:") && code.contains(refId)) {
                    try {
                        crs = authorityFactory.createCoordinateReferenceSystem(code);
                    } catch (Exception e) {
                        System.err.printf("%s: Unable to create CRS: %s\n", code, e.getMessage());
                    }
                    projectionBounds = getExtents(crs);
                    System.err.printf("%s, %s \n", code, projectionBounds);
                }
            }
        }
        return projectionBounds;
    }
}
