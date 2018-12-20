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
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.operation.TransformException;

import com.google.common.base.Predicate;


/**
 * This is a simple class that is very much like the ScreenMapPredicate class.
 * This works on SimpleFeatures only.
 */
class FeatureScreenMapPredicate implements Predicate<SimpleFeature> {
    ScreenMap screenMap;

    public FeatureScreenMapPredicate(ScreenMap screenMap) {
        this.screenMap = screenMap;
    }


    /**
     * Filter out small features (<pixel) where that pixel already has a small feature in it.
     */
    @Override
    public boolean apply(SimpleFeature feature) {
        Envelope e = ((Geometry) feature.getDefaultGeometry()).getEnvelopeInternal();
        //only do work if its a small geometry
        if (screenMap.canSimplify(e)) {
            // screenmap isn't thread safe
            // Both this and ScreenMapPredicate syncronize on the underlying ScreenMap object.
            // However, i don't think its possible for both to be running at the same
            // time (screen map filter will either occur on the index or the actual features)
            synchronized (screenMap) {
                try {
                    return !screenMap.checkAndSet(e);
                } catch (TransformException e1) {
                    e1.printStackTrace();
                    return true;
                }
            }
        }
        return true;
    }
}
