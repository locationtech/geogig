/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.data;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.filter.expression.Literal;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 *
 */
class ExtractBounds extends DefaultFilterVisitor {

    private CoordinateReferenceSystem nativeCrs;

    private List<ReferencedEnvelope> bounds = new ArrayList<>(2);

    public ExtractBounds(final CoordinateReferenceSystem nativeCrs) {
        this.nativeCrs = nativeCrs;
    }

    @Override
    public List<ReferencedEnvelope> visit(Literal literal, @Nullable Object data) {

        Object value = literal.getValue();
        if (value instanceof Geometry) {
            Geometry geom = (Geometry) value;
            Envelope literalEnvelope = geom.getEnvelopeInternal();
            CoordinateReferenceSystem crs = nativeCrs;
            if (geom.getUserData() instanceof CoordinateReferenceSystem) {
                crs = (CoordinateReferenceSystem) geom.getUserData();
            }
            ReferencedEnvelope bbox = new ReferencedEnvelope(literalEnvelope, crs);
            bounds.add(bbox);
        }
        return bounds;
    }
}
