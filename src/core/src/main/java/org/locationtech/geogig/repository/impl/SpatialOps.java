/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.opengis.geometry.BoundingBox;
import org.opengis.metadata.extent.Extent;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.metadata.extent.GeographicExtent;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.CoordinateOperationFactory;

import com.google.common.base.Splitter;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * Utility methods to deal with various spatial operations
 * 
 */
public class SpatialOps {

    private static final GeometryFactory gfac = new GeometryFactory();

    /**
     * @param oldObject
     * @param newObject
     * @return the aggregated bounding box
     */
    public static com.vividsolutions.jts.geom.Envelope aggregatedBounds(Node oldObject,
            Node newObject) {
        Envelope env = new Envelope();
        if (oldObject != null) {
            oldObject.expand(env);
        }
        if (newObject != null) {
            newObject.expand(env);
        }
        return env;
    }

    /**
     * Creates and returns a geometry out of bounds (a point if bounds.getSpan(0) ==
     * bounds.getSpan(1) == 0D, a polygon otherwise), setting the bounds
     * {@link BoundingBox#getCoordinateReferenceSystem() CRS} as the geometry's
     * {@link Geometry#getUserData() user data}.
     * 
     * @param bounds the bounding box to build from
     * @return the newly constructed geometry
     */
    public static Geometry toGeometry(final BoundingBox bounds) {
        if (bounds == null) {
            return null;
        }
        Geometry geom;
        if (bounds.getSpan(0) == 0D && bounds.getSpan(1) == 0D) {
            geom = gfac.createPoint(new Coordinate(bounds.getMinX(), bounds.getMinY()));
        } else {
            geom = JTS.toGeometry(bounds, gfac);
        }
        geom.setUserData(bounds.getCoordinateReferenceSystem());
        return geom;
    }

    public static Envelope boundsOf(RevTree tree) {
        Envelope env = new Envelope();
        tree.buckets().values().forEach((b) -> b.expand(env));
        tree.trees().forEach((t) -> t.expand(env));
        tree.features().forEach((f) -> f.expand(env));
        return env;
    }

    @Nullable
    public static Envelope boundsOf(RevFeature feat) {
        Envelope env = new Envelope();
        feat.forEach((o) -> {
            if (o instanceof Geometry) {
                env.expandToInclude(((Geometry) o).getEnvelopeInternal());
            }
        });
        return env.isNull() ? null : env;
    }

    /**
     * Get the bounds of the desired CRS, uses JTS ReferencedEnvelope transform to properly handle
     * polar projections
     * 
     * @param crs the target CoordinateReferenceSystem
     * @return bounds an Envelope containing the CRS bounds, throws a NoSuchAuthorityCodeException
     *         if the crs cannot be found
     */
    public @Nullable static Envelope boundsOf(CoordinateReferenceSystem crs) throws Exception {

        final Extent domainOfValidity = crs.getDomainOfValidity();

        if (null == domainOfValidity) {
            return null;
        }

        Collection<? extends GeographicExtent> geographicElements;
        geographicElements = domainOfValidity.getGeographicElements();

        GeographicExtent geographicExtent = geographicElements.iterator().next();
        GeographicBoundingBox geographicBoundingBox = (GeographicBoundingBox) geographicExtent;

        double minx = geographicBoundingBox.getWestBoundLongitude();
        double miny = geographicBoundingBox.getSouthBoundLatitude();
        double maxx = geographicBoundingBox.getEastBoundLongitude();
        double maxy = geographicBoundingBox.getNorthBoundLatitude();

        CoordinateReferenceSystem wgs84LongFirst;

        wgs84LongFirst = CRS.decode("EPSG:4326", true);
        CoordinateOperationFactory coordOpFactory = CRS.getCoordinateOperationFactory(true);
        CoordinateOperation op = coordOpFactory.createOperation(wgs84LongFirst, crs);

        ReferencedEnvelope refEnvelope = new ReferencedEnvelope(minx, maxx, miny, maxy,
                wgs84LongFirst);
        GeneralEnvelope genEnvelope = CRS.transform(op, refEnvelope);

        double xmax = genEnvelope.getMaximum(0);
        double ymax = genEnvelope.getMaximum(1);
        double xmin = genEnvelope.getMinimum(0);
        double ymin = genEnvelope.getMinimum(1);

        Envelope envelope = new Envelope(xmin, xmax, ymin, ymax);
        return envelope;
    }

    /**
     * Parses a bounding box in the format {@code <minx,miny,maxx,maxy,SRS>} where SRS is an EPSG
     * code like {@code EPSG:4325} etc.
     * <p>
     * The oridinates must be given in "longitude first" format, and the SRS will be decoded the
     * same way.
     * 
     * @throws IllegalArgumentException if the argument doesn't match the expected format, or the
     *         SRS can't be parsed.
     */
    @Nullable
    public static ReferencedEnvelope parseBBOX(final @Nullable String bboxArg) {
        if (bboxArg == null) {
            return null;
        }
        List<String> split = Splitter.on(',').omitEmptyStrings().splitToList(bboxArg);
        if (split.size() != 5) {
            throw new IllegalArgumentException(String.format(
                    "Invalid bbox parameter: '%s'. Expected format: <minx,miny,maxx,maxy,CRS>",
                    bboxArg));
        }
        double minx;
        double miny;
        double maxx;
        double maxy;
        try {
            minx = Double.parseDouble(split.get(0));
            miny = Double.parseDouble(split.get(1));
            maxx = Double.parseDouble(split.get(2));
            maxy = Double.parseDouble(split.get(3));
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(String.format(
                    "Invalid bbox parameter: '%s'. Expected format: <minx,miny,maxx,maxy,CRS>",
                    bboxArg));
        }
        final String srs = split.get(4);
        final CoordinateReferenceSystem crs;
        try {
            crs = CRS.decode(srs, true);
        } catch (FactoryException e) {
            throw new IllegalArgumentException(String
                    .format("Invalid bbox parameter: '%s'. Can't parse CRS '%s'", bboxArg, srs));
        }
        ReferencedEnvelope env = new ReferencedEnvelope(minx, maxx, miny, maxy, crs);
        return env;
    }

}
