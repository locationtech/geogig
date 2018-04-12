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

    public ScreenMapGeometryReplacer(ScreenMap screenMap) {
        this.screenMap = screenMap;
    }

    /**
     * if the features is fully inside a pixel (screenMap.canSimplify()), then
     * we replace it's geometry with a one that takes up the entire pixels
     * (screenMap.getSimplifiedShape()).
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
            Geometry newGeom = screenMap.getSimplifiedShape(
                    e.getMinX(), e.getMinY(),
                    e.getMaxX(), e.getMaxY(),
                    g.getFactory(), g.getClass());
            feature.setDefaultGeometry(newGeom);
        }
        return feature;
    }
}
