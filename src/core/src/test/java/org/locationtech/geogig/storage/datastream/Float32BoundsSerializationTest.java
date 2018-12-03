/* Copyright (c) 2012-2016 Boundless and others.
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

import org.junit.Test;
import org.locationtech.jts.geom.Envelope;

public class Float32BoundsSerializationTest {


    @Test
    public void testSerialization() {
        Envelope bounds = new Envelope(1, 2, 3, 4);
        int[] serializedForm = Float32BoundsSerializer.serialize(bounds);
        Envelope bounds2 = Float32BoundsSerializer.deserialize(serializedForm);
        assertEquals(bounds, bounds2);
    }

    @Test
    public void testSerializationNull() {
        Envelope bounds = new Envelope();
        int[] serializedForm = Float32BoundsSerializer.serialize(bounds);
        Envelope bounds2 = Float32BoundsSerializer.deserialize(serializedForm);
        assertEquals(bounds, bounds2);
    }
}

