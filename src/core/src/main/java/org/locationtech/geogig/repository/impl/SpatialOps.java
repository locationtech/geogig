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

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.data.EPSGBoundsCalc;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.porcelain.CRSException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

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
    public @Nullable static Envelope boundsOf(CoordinateReferenceSystem crs) throws Exception {
        crs = findKnownCrs(crs);
        return EPSGBoundsCalc.getExtents(crs);
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

    public static CoordinateReferenceSystem findIdentifier(GeometryDescriptor geometryDescriptor)
            throws FactoryException, CRSException {
        if (geometryDescriptor != null) {
            CoordinateReferenceSystem crs = geometryDescriptor.getCoordinateReferenceSystem();
            return findKnownCrs(crs);
        }
        return null;
    }

    public static CoordinateReferenceSystem findKnownCrs(CoordinateReferenceSystem crs)
            throws FactoryException, CRSException {
        String srs = CRS.toSRS(crs);
        if (srs != null && !srs.startsWith("EPSG:")) {
            boolean fullScan = true;
            String knownIdentifier;
            knownIdentifier = CRS.lookupIdentifier(crs, fullScan);
            if (knownIdentifier != null) {
                boolean longitudeFirst = CRS.getAxisOrder(crs).equals(CRS.AxisOrder.EAST_NORTH);
                crs = CRS.decode(knownIdentifier, longitudeFirst);
            } else {
                throw new CRSException(
                        "Could not find identifier associated with the defined CRS: \n" + crs);
            }
        }
        return crs;
    }
}
