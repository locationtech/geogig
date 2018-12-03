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

import java.util.Collection;

import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.porcelain.CRSException;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.metadata.extent.Extent;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.metadata.extent.GeographicExtent;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.CoordinateOperationFactory;
import org.opengis.referencing.operation.TransformException;

/**
 * Given a code string (EPSG:####) or {@link RevFeatureType} , find the CRS bounds and return as an
 * Envelope
 */
public class EPSGBoundsCalc {

    private static CoordinateReferenceSystem wgs84 = DefaultGeographicCRS.WGS84;

    /**
     * Get the bounds of the desired CRS
     *
     * @param crs the target CoordinateReferenceSystem
     * @return bounds an Envelope containing the CRS bounds, throws a NoSuchAuthorityCodeException,
     * a CRSException, or a TransformException if the CRS cannot be found
     */
    public static Envelope getExtents(CoordinateReferenceSystem crs)
        throws CRSException, FactoryException, TransformException {

        if (crs == null) {
            throw new CRSException("Invalid or no CRS found. \n");
        }

        final Extent domainOfValidity = crs.getDomainOfValidity();

        if (null == domainOfValidity)
            throw new CRSException(
                "No domain of validity provided by CRS definition. CRS may be invalid. \n" + crs);

        Collection<? extends GeographicExtent> geographicElements = domainOfValidity
            .getGeographicElements();

        GeographicExtent geographicExtent = geographicElements.iterator().next();
        GeographicBoundingBox geographicBoundingBox = (GeographicBoundingBox) geographicExtent;

        double minx = geographicBoundingBox.getWestBoundLongitude();
        double miny = geographicBoundingBox.getSouthBoundLatitude();
        double maxx = geographicBoundingBox.getEastBoundLongitude();
        double maxy = geographicBoundingBox.getNorthBoundLatitude();

        CoordinateOperationFactory coordOpFactory = CRS.getCoordinateOperationFactory(true);
        CoordinateOperation op = coordOpFactory.createOperation(wgs84, crs);

        ReferencedEnvelope refEnvelope = new ReferencedEnvelope(minx, maxx, miny, maxy, wgs84);
        GeneralEnvelope genEnvelope = CRS.transform(op, refEnvelope);

        double xmax = genEnvelope.getMaximum(0);
        double ymax = genEnvelope.getMaximum(1);
        double xmin = genEnvelope.getMinimum(0);
        double ymin = genEnvelope.getMinimum(1);

        return new Envelope(xmin, xmax, ymin, ymax);
    }

    /**
     * Search for the given CRS (EPSG code), return the bounds (domain of validity)
     *
     * @param refId the input CRS
     * @return projectionBounds an Envelope describing the CRS bounds, throws
     * a NoSuchAuthorityException, a CRSException, or a TransformException if the CRS cannot be found
     */
    public Envelope getCRSBounds(String refId)
        throws CRSException, FactoryException, TransformException {

        CRSAuthorityFactory authorityFactory = CRS.getAuthorityFactory(true);
        CoordinateReferenceSystem crs = authorityFactory.createCoordinateReferenceSystem(refId);

        return getExtents(crs);
    }

    /**
     * Search for the given CRS (EPSG code), return the bounds (domain of validity)
     *
     * @param ft the RevFeatureType of the CRS to find the bounds for
     * @return Envelope describing the CRS bounds, throws NoSuchAuthorityException, a CRSException,
     * or a TransformException if the CRS cannot be found
     */
    public Envelope getCRSBounds(RevFeatureType ft)
        throws CRSException, FactoryException, TransformException {

        GeometryDescriptor geometryDescriptor = ft.type().getGeometryDescriptor();
        CoordinateReferenceSystem crs = SpatialOps.findIdentifier(geometryDescriptor);
        return getExtents(crs);
    }

}
