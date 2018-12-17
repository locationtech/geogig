/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.base.Function;

public class SimplifyingGeometryReplacer implements Function<SimpleFeature, SimpleFeature> {

        double distance;

        GeometryFactory geometryFactory;

        public SimplifyingGeometryReplacer(double distance, GeometryFactory geometryFactory) {
                this.distance = distance;
                this.geometryFactory = geometryFactory;
        }

        @Override public SimpleFeature apply(SimpleFeature input) {
                Geometry g = (Geometry) input.getDefaultGeometry();
                Geometry newGeometry;

                if ((g == null) || (g.isEmpty()))
                        return input;

                if (g.getDimension() == 2) {// polygon
                        if (g instanceof MultiPolygon) {
                                newGeometry = simplify((MultiPolygon) g);
                        } else {
                                newGeometry = simplify((Polygon) g);
                        }
                } else { // point or line -- just use the normal JTS D-P
                        DouglasPeuckerSimplifier tss = new DouglasPeuckerSimplifier(g);
                        tss.setDistanceTolerance(distance);
                        tss.setEnsureValid(false);
                        newGeometry = tss.getResultGeometry();
                }
                input.setDefaultGeometry(newGeometry);
                return input;
        }

        public MultiPolygon simplify(MultiPolygon mp) {
                Polygon[] polyBuff = new Polygon[mp.getNumGeometries()];
                for (int idx = 0; idx < mp.getNumGeometries(); idx++) {
                        polyBuff[idx] = simplify((Polygon) mp.getGeometryN(idx));
                }
                return geometryFactory.createMultiPolygon(polyBuff);
        }

        public Polygon simplify(Polygon p) {
                //best case is you're going to remove one point, don't bother with the work.
                //more likely to mess it up!
                if (p.getNumPoints() <= 5)
                        return p;
                Envelope bbox = p.getEnvelopeInternal();
                //its smaller than a pixel - replace with a polygon representing its bbox
                if ((bbox.getWidth() <= distance) && (bbox.getWidth() <= distance)) {
                        return createBBoxPolygon(bbox);
                }
                DouglasPeuckerSimplifier tss = new DouglasPeuckerSimplifier(p);
                tss.setDistanceTolerance(distance);
                tss.setEnsureValid(false);
                Geometry newGeom = tss.getResultGeometry();

                if (newGeom.isEmpty())
                        return createBBoxPolygon(bbox);
                // ... sometime the DP simplifier returns linear rings instead of Polygon
                if (newGeom instanceof LinearRing)
                        return geometryFactory.createPolygon((LinearRing)newGeom);
                return (Polygon) newGeom;
        }

        private Polygon createBBoxPolygon(Envelope bbox) {
                Coordinate[] coords = new Coordinate[5];
                //right handed (clockwise)

                coords[0] = new Coordinate(bbox.getMinX(), bbox.getMinY());
                coords[1] = new Coordinate(bbox.getMinX(), bbox.getMaxY());
                coords[2] = new Coordinate(bbox.getMaxX(), bbox.getMaxY());
                coords[3] = new Coordinate(bbox.getMaxX(), bbox.getMinY());
                coords[4] = new Coordinate(bbox.getMinX(), bbox.getMinY());

                return geometryFactory.createPolygon(coords);
        }
}
