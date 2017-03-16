/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;

import org.junit.Test;

public class TailTest {

    @Test
    public void testEncodeDecode() {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // some fake data
        for (int i = 0; i < 1024; i++) {
            out.write(i);
        }

        DataOutput dataOut = new DataOutputStream(out);
        int offsetOfTail = out.size();

        int offsetOfTreesNodeset = 100;
        int offsetOfFeaturesNodeset = 200;
        int offsetOfBuckets = 300;
        int offsetOfStringTable = 400;

        Tail.encode(dataOut, offsetOfTreesNodeset, offsetOfFeaturesNodeset, offsetOfBuckets,
                offsetOfStringTable, offsetOfTail);

        byte[] data = out.toByteArray();
        ByteBuffer buff = ByteBuffer.wrap(data);
        Tail tail = Tail.decode(buff);

        assertEquals(offsetOfTail, tail.getOffsetOfTail());
        assertEquals(offsetOfBuckets, tail.getOffsetOfBuckets());
        assertEquals(offsetOfFeaturesNodeset, tail.getOffsetOfFeaturesNodeset());
        assertEquals(offsetOfStringTable, tail.getOffsetOfStringTable());
        assertEquals(offsetOfTreesNodeset, tail.getOffsetOfTreesNodeset());
    }

}
