/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import java.util.List;
import java.util.stream.Collectors;

import org.geotools.renderer.ScreenMap;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.operation.TransformException;

import com.google.common.base.Predicate;

class DiffFeatureScreenMapPredicate implements Predicate<SimpleFeature> {

    private ScreenMap screenMap;

    private List<String> geometryAttributes;

    public DiffFeatureScreenMapPredicate(ScreenMap screenMap, SimpleFeatureType diffSchema) {
        this.screenMap = screenMap;
        geometryAttributes = diffSchema.getAttributeDescriptors().stream()
                .filter(d -> d instanceof GeometryDescriptor).map(d -> d.getLocalName())
                .collect(Collectors.toList());
    }

    /**
     * Filter out small features (<pixel) where that pixel already has a small feature in it.
     */
    public @Override boolean apply(SimpleFeature feature) {
        List<String> geometryAttributes = this.geometryAttributes;
        boolean apply = false;
        for (int i = 0; i < geometryAttributes.size(); i++) {
            String att = geometryAttributes.get(i);
            apply |= apply(feature, att);
        }
        return apply;
    }

    private boolean apply(SimpleFeature feature, final String att) {
        Geometry g = (Geometry) feature.getAttribute(att);
        if (g == null) {
            return false;// filter it out
        }
        Envelope e = g.getEnvelopeInternal();
        // only do work if its a small geometry
        final boolean canSimplify = screenMap.canSimplify(e);
        if (!canSimplify) {
            return true;
        }

        boolean includeFeature = true;
        try {
            includeFeature = screenMap.checkAndSet(e);
        } catch (TransformException e1) {
            e1.printStackTrace();
        }

        if (includeFeature && g.getNumPoints() > 4) {
            Geometry newGeom = screenMap.getSimplifiedShape(e.getMinX(), e.getMinY(), e.getMaxX(),
                    e.getMaxY(), g.getFactory(), g.getClass());
            feature.setAttribute(att, newGeom);
        }

        return includeFeature;
    }
}
