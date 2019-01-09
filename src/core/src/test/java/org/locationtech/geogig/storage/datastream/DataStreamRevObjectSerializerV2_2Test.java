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

import java.util.Iterator;

import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.impl.RevObjectSerializerConformanceTest;
import org.locationtech.jts.geom.Envelope;

public class DataStreamRevObjectSerializerV2_2Test extends RevObjectSerializerConformanceTest {

    @Override
    protected RevObjectSerializer newObjectSerializer() {
        return DataStreamRevObjectSerializerV2_2.INSTANCE;
    }

    // exact bounds check
    @Override
    public void assertTreesAreEqual(RevTree a, RevTree b) {
        super.assertTreesAreEqual(a, b); // do the original impl checks

        Iterator<? extends Bounded> ia;
        Iterator<? extends Bounded> ib;
        if (a.bucketsSize() == 0) {
            ia = RevObjects.children(a, CanonicalNodeOrder.INSTANCE);
            ib = RevObjects.children(b, CanonicalNodeOrder.INSTANCE);
        } else {
            ia = a.getBuckets().iterator();
            ib = b.getBuckets().iterator();
        }

        // bounds are not part of the Bounded.equals(Object) contract since it's auxiliary
        // information
        while (ia.hasNext()) {
            Bounded ba = ia.next();
            Bounded bb = ib.next();
            Envelope ea = new Envelope();
            Envelope eb = new Envelope();
            ba.expand(ea);
            bb.expand(eb);
            assertEquals(ea.getMinX(), eb.getMinX(), 0);
            assertEquals(ea.getMinY(), eb.getMinY(), 0);
            assertEquals(ea.getMaxX(), eb.getMaxX(), 0);
            assertEquals(ea.getMaxY(), eb.getMaxY(), 0);
        }
    }

}