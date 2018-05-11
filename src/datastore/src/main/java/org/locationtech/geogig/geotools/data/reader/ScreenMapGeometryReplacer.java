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

import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.CoordinateSequenceFactory;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import org.geotools.geometry.jts.JTS;
import org.geotools.renderer.ScreenMap;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.base.Function;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;


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
                    Geometry newGeom = getSimplifiedShapeBBOX(e.getMinX(), e.getMinY(),
                            e.getMaxX(), e.getMaxY(),g.getFactory(), g.getClass());
                    feature.setDefaultGeometry(newGeom);
            }
        }
        return feature;
    }

        public Geometry getSimplifiedShapeBBOX(double x0, double y0, double x1, double y1,
                GeometryFactory geometryFactory, Class geometryType) {
                CoordinateSequenceFactory csf = geometryFactory.getCoordinateSequenceFactory();
                CoordinateSequence cs;
                if (!Point.class.isAssignableFrom(geometryType) && !MultiPoint.class
                        .isAssignableFrom(geometryType)) {
                        if (!LineString.class.isAssignableFrom(geometryType)
                                && !MultiLineString.class.isAssignableFrom(geometryType)) {
                                cs = JTS.createCS(csf, 5, 2);
                                cs.setOrdinate(0, 0, x0);
                                cs.setOrdinate(0, 1, y0);
                                cs.setOrdinate(1, 0, x0);
                                cs.setOrdinate(1, 1, y1);
                                cs.setOrdinate(2, 0, x1);
                                cs.setOrdinate(2, 1, y1);
                                cs.setOrdinate(3, 0, x1);
                                cs.setOrdinate(3, 1, y0);
                                cs.setOrdinate(4, 0, x0);
                                cs.setOrdinate(4, 1, y0);
                                LinearRing ring = geometryFactory.createLinearRing(cs);
                                return (Geometry) (MultiPolygon.class
                                        .isAssignableFrom(geometryType) ?
                                        geometryFactory.createMultiPolygon(new Polygon[] {
                                                geometryFactory.createPolygon(ring,
                                                        (LinearRing[]) null) }) :
                                        geometryFactory.createPolygon(ring, (LinearRing[]) null));
                        } else {
                                cs = JTS.createCS(csf, 2, 2);
                                cs.setOrdinate(0, 0, x0);
                                cs.setOrdinate(0, 1, y0);
                                cs.setOrdinate(1, 0, x1);
                                cs.setOrdinate(1, 1, y1);
                                return (Geometry) (MultiLineString.class
                                        .isAssignableFrom(geometryType) ?
                                        geometryFactory.createMultiLineString(new LineString[] {
                                                geometryFactory.createLineString(cs) }) :
                                        geometryFactory.createLineString(cs));
                        }
                } else {
                        cs = JTS.createCS(csf, 1, 2);
                        cs.setOrdinate(0, 0, (x1 - x0) / 2.0);
                        cs.setOrdinate(0, 1, (y1 - y0) / 2.0);
                        return (Geometry) (Point.class.isAssignableFrom(geometryType) ?
                                geometryFactory.createPoint(cs) :
                                geometryFactory.createMultiPoint(
                                        new Point[] { geometryFactory.createPoint(cs) }));
                }
        }
}
