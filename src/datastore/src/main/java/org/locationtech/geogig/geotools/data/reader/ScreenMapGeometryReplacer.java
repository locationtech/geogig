/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import org.geotools.renderer.ScreenMap;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.base.Function;


/**
 * This is a simple function that, if a geometry is "small" (fits in a pixel - as defined by
 * the screenmap) then the geometry is replaced with one that is a full pixel's size.
 * <p>
 * This is the same behaviour as the StreamingRenderer and ShapefileReader.  If you don't do this
 * the resulting image looks "funny" due to anti-aliasing.
 * <p>
 * NOTE: its possible for the ScreenMapGeometryReplacer to be running at the same time
 *       as the ScreenMapPredicate or FeatureScreenMapPredicate.  However, the features
 *       handed to this class will have already go through the filtering.
 */
 public class ScreenMapGeometryReplacer implements Function<SimpleFeature, SimpleFeature> {

    ScreenMap screenMap;
    boolean   replaceWithPixel = true;

    public ScreenMapGeometryReplacer(ScreenMap screenMap) {
        this.screenMap = screenMap;
    }

    public ScreenMapGeometryReplacer(ScreenMap screenMap, boolean replaceWithPixel) {
                this.screenMap = screenMap;
                this.replaceWithPixel = replaceWithPixel;
    }

    /**
     * if the features is fully inside a pixel (screenMap.canSimplify()), then
     * we replace it's geometry with a one that takes up the entire pixels
     * (screenMap.getSimplifiedShape()) OR with the feature's boundingbox.
     * <p>
     * TODO: allow to specify the geometry attribute.
     *
     * @param feature
     * @return
     */
    @Override
    public SimpleFeature apply(SimpleFeature feature) {
        Geometry g = (Geometry) feature.getDefaultGeometry();
        if (g == null || g instanceof Point) {
            return feature; // cannot simplify a point
        }
        Envelope e = g.getEnvelopeInternal();
        // this is safe to call multi-threaded
        if (!screenMap.canSimplify(e)) {
            return feature;
        } else {
            if (replaceWithPixel) {
                    Geometry newGeom = screenMap
                            .getSimplifiedShape(e.getMinX(), e.getMinY(), e.getMaxX(), e.getMaxY(),
                                    g.getFactory(), g.getClass());
                    feature.setDefaultGeometry(newGeom);
            }
            else {
                    Geometry newGeom = getSimplifiedShapeBBOX(e,g.getFactory(),g);
                    feature.setDefaultGeometry(newGeom);
            }
        }
        return feature;
    }

        private Polygon createBBoxPolygon(Envelope bbox, GeometryFactory geometryFactory) {
                Coordinate[] coords = new Coordinate[5];
                //right handed (clockwise)

                coords[0] = new Coordinate(bbox.getMinX(), bbox.getMinY());
                coords[1] = new Coordinate(bbox.getMinX(), bbox.getMaxY());
                coords[2] = new Coordinate(bbox.getMaxX(), bbox.getMaxY());
                coords[3] = new Coordinate(bbox.getMaxX(), bbox.getMinY());
                coords[4] = new Coordinate(bbox.getMinX(), bbox.getMinY());

                return geometryFactory.createPolygon(coords);
        }

        public Point createMidPoint(Envelope bbox, GeometryFactory geometryFactory) {
            return geometryFactory.createPoint(bbox.centre());
        }

        public LineString createDiagonalLine(Envelope bbox, GeometryFactory geometryFactory) {
                Coordinate[] coords = new Coordinate[2];

                coords[0] = new Coordinate(bbox.getMinX(), bbox.getMinY());
                coords[1] = new Coordinate(bbox.getMaxX(), bbox.getMaxY());

                return geometryFactory.createLineString(coords);
        }

        // create a shape that is the geometry's "bbox" size
        public Geometry getSimplifiedShapeBBOX(Envelope e, GeometryFactory geometryFactory,
                Geometry g) {

                if (e.isNull())
                        return null; //if there's isn't an envelope, we cannot make one...

                if (g instanceof Polygon) {
                        return createBBoxPolygon(e, geometryFactory);
                } else if (g instanceof MultiPolygon) {
                        Polygon[] polyArray = new Polygon[] { createBBoxPolygon(e, geometryFactory) };
                        return geometryFactory.createMultiPolygon(polyArray);
                } else if (g instanceof Point) {
                        return createMidPoint(e, geometryFactory);
                } else if (g instanceof MultiPoint) {
                        Point[] pointArray = new Point[] { createMidPoint(e, geometryFactory) };
                        return geometryFactory.createMultiPoint(pointArray);
                } else if (g instanceof LineString) {
                        return createDiagonalLine(e, geometryFactory);
                } else if (g instanceof MultiLineString) {
                        LineString[] ls = new LineString[] {createDiagonalLine(e, geometryFactory)};
                        return geometryFactory.createMultiLineString(ls);
                } else if (g instanceof LinearRing) {
                        Polygon p = createBBoxPolygon(e, geometryFactory);
                        return p.getExteriorRing();
                }
                return null;
        }
}
