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

import com.vividsolutions.jts.geom.Envelope;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.impl.ObjectSerializationFactoryTest;
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;

public class DataStreamSerializationFactoryV2_3Test extends ObjectSerializationFactoryTest {

    @Override
    protected ObjectSerializingFactory getObjectSerializingFactory() {
        return DataStreamSerializationFactoryV2_3.INSTANCE;
    }

    //exact bounds check
    @Override
    public void assertTreesAreEqual(RevTree a, RevTree b) {
        assertEquals(a.getId(), b.getId());
        assertEquals(a.buckets(), b.buckets());
        assertEquals(a.features(), b.features());
        assertEquals(a.trees(), b.trees());
        assertEquals(a.numTrees(), b.numTrees());
        assertEquals(a.size(), b.size());

        Iterator<? extends Bounded> ia;
        Iterator<? extends Bounded> ib;
        if (a.buckets().isEmpty()) {
            ia = RevObjects.children(a, CanonicalNodeOrder.INSTANCE);
            ib = RevObjects.children(b, CanonicalNodeOrder.INSTANCE);
        } else {
            ia = a.buckets().values().iterator();
            ib = b.buckets().values().iterator();
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
