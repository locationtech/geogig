/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;


import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.storage.datastream.v2_3.FloatPackedCoordinateSequence;
import org.locationtech.jts.geom.Coordinate;

public class FloatPackedCoordinateSequenceTest {

    @Test
    public void Empty2dTest() {
        assertEquals(2, FloatPackedCoordinateSequence.EMPTY_2D.getDimension() );
        assertEquals(0, FloatPackedCoordinateSequence.EMPTY_2D.size() );
    }

    @Test
    public void constructorTest() {
        List<Coordinate> coords = new ArrayList<>();
        coords.add( new Coordinate(1,2));
        coords.add( new Coordinate(3,4));

        FloatPackedCoordinateSequence seq = new FloatPackedCoordinateSequence(2, coords);
        assertEquals(seq.getOrdinate(0,0), coords.get(0).x,0);
        assertEquals(seq.getOrdinate(0,1), coords.get(0).y,0);
        assertEquals(seq.getOrdinate(1,0), coords.get(1).x,0);
        assertEquals(seq.getOrdinate(1,1), coords.get(1).y,0);
    }

    @Test
    public void testSerialized() {
        List<Coordinate> coords = new ArrayList<>();
        coords.add( new Coordinate(1,2));
        coords.add( new Coordinate(3,4));
        FloatPackedCoordinateSequence seq = new FloatPackedCoordinateSequence(2, coords);

        int[][] serialized = seq.toSerializedForm();
        FloatPackedCoordinateSequence seq2 = new FloatPackedCoordinateSequence(serialized);

        assertEquals(serialized.length,seq.getDimension());
        assertEquals(serialized[0].length,seq.size());

        assertEquals(seq.getOrdinate(0,0), seq2.getOrdinate(0,0),0);
        assertEquals(seq.getOrdinate(0,1), seq2.getOrdinate(0,1),0);

        assertEquals(seq.getOrdinate(1,0), seq2.getOrdinate(1,0),0);
        assertEquals(seq.getOrdinate(1,1), seq2.getOrdinate(1,1),0);

    }
}
