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

import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.crs.CRS;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import com.google.common.base.Splitter;

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
    public static org.locationtech.jts.geom.Envelope aggregatedBounds(Node oldObject,
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

    public static Envelope boundsOf(RevTree tree) {
        Envelope env = new Envelope();
        tree.forEachBucket(bucket -> bucket.expand(env));
        tree.forEachTree(n -> n.expand(env));
        tree.forEachFeature(n -> n.expand(env));
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
    public static Optional<Envelope> boundsOf(
            org.locationtech.geogig.crs.CoordinateReferenceSystem crs) {
        return CRS.findAreaOfValidity(crs.getSrsIdentifier());
    }

    /**
     * Parses a bounding box in the format {@code <minx,miny,maxx,maxy>}
     * <p>
     * The oridinates must be given in "longitude first" format
     * 
     * @throws IllegalArgumentException if the argument doesn't match the expected format
     */
    @Nullable
    public static Envelope parseNonReferencedBBOX(final @Nullable String bboxArg) {
        if (bboxArg == null) {
            return null;
        }
        List<String> split = Splitter.on(',').omitEmptyStrings().splitToList(bboxArg);
        if (split.size() != 4) {
            throw new IllegalArgumentException(String.format(
                    "Invalid bbox parameter: '%s'. Expected format: <minx,miny,maxx,maxy>",
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
                    "Invalid bbox parameter: '%s'. Expected format: <minx,miny,maxx,maxy>",
                    bboxArg));
        }

        Envelope env = new Envelope(minx, maxx, miny, maxy);
        return env;
    }

    public static Polygon toGeometry(Envelope e) {
        if (e == null || e.isNull()) {
            return gfac.createPolygon();
        }
        Coordinate[] shell = new Coordinate[5];
        shell[0] = new Coordinate(e.getMinX(), e.getMinY());
        shell[1] = new Coordinate(e.getMinX(), e.getMaxY());
        shell[2] = new Coordinate(e.getMaxX(), e.getMaxY());
        shell[3] = new Coordinate(e.getMaxX(), e.getMinY());
        shell[4] = new Coordinate(e.getMinX(), e.getMinY());
        return gfac.createPolygon(shell);
    }

    // public static CoordinateReferenceSystem findIdentifier(GeometryDescriptor geometryDescriptor)
    // throws FactoryException, CRSException {
    // if (geometryDescriptor != null) {
    // CoordinateReferenceSystem crs = geometryDescriptor.getCoordinateReferenceSystem();
    // return findKnownCrs(crs);
    // }
    // return null;
    // }
    //
    // public static CoordinateReferenceSystem findKnownCrs(CoordinateReferenceSystem crs)
    // throws FactoryException, CRSException {
    // String srs = CRS.toSRS(crs);
    // if (srs != null && !srs.startsWith("EPSG:")) {
    // boolean fullScan = true;
    // String knownIdentifier;
    // knownIdentifier = CRS.lookupIdentifier(crs, fullScan);
    // if (knownIdentifier != null) {
    // boolean longitudeFirst = CRS.getAxisOrder(crs).equals(CRS.AxisOrder.EAST_NORTH);
    // crs = CRS.decode(knownIdentifier, longitudeFirst);
    // } else {
    // throw new CRSException(
    // "Could not find identifier associated with the defined CRS: \n" + crs);
    // }
    // }
    // return crs;
    // }
}
