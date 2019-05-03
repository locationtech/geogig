/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.Literal;

/**
 *
 */
class ExtractBounds extends DefaultFilterVisitor {

    private List<Envelope> bounds = new ArrayList<>(2);

    @Override
    public List<Envelope> visit(Literal literal, @Nullable Object data) {

        Object value = literal.getValue();
        if (value instanceof Geometry) {
            Geometry geom = (Geometry) value;
            Envelope literalEnvelope = geom.getEnvelopeInternal();
            bounds.add(literalEnvelope);
        }
        return bounds;
    }

    @SuppressWarnings("unchecked")
    public static List<Envelope> getBounds(Filter filterInNativeCrs) {
        return (List<Envelope>) filterInNativeCrs.accept(new ExtractBounds(), null);
    }
}
