/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import java.io.Serializable;

import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.CoordinateSequenceFactory;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;

/**
 * A {@link CoordinateSequenceFactory} that produces packed sequences of coordinates as
 * {@code int[]} as per {@link OSMCoordinateSequence}
 */
public class OSMCoordinateSequenceFactory implements CoordinateSequenceFactory, Serializable {

    private static final long serialVersionUID = -1245500277641319804L;

    private static final OSMCoordinateSequenceFactory INSTANCE = new OSMCoordinateSequenceFactory();

    @Override
    public OSMCoordinateSequence create(Coordinate[] coordinates) {
        return new OSMCoordinateSequence(coordinates);
    }

    @Override
    public CoordinateSequence create(CoordinateSequence coordSeq) {
        return new CoordinateArraySequence(coordSeq);
    }

    @Override
    public OSMCoordinateSequence create(int size, int dimension) {
        Preconditions.checkArgument(dimension == 2);
        return create(size);
    }

    public OSMCoordinateSequence create(int size) {
        return new OSMCoordinateSequence(size);
    }

    public static OSMCoordinateSequenceFactory instance() {
        return INSTANCE;
    }

}
