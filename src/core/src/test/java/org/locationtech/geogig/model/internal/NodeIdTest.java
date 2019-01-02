/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.locationtech.jts.geom.Envelope;

public class NodeIdTest {

    @Test
    public void testEquals() {
        NodeId n1 = new NodeId("name1");
        NodeId n2 = new NodeId("name1", null);
        NodeId n3 = new NodeId("name1", new Envelope());
        NodeId n4 = new NodeId("name1", new Envelope(-1, -1, 0, 0));
        NodeId n5 = new NodeId("name1", new Envelope(-1, -1, 0, 0));
        NodeId n6 = new NodeId("name2", new Envelope(-1, -1, 0, 0));

        assertEquals(n1, n2);
        assertNotEquals(n1, n3);
        assertEquals(n4, n5);
        assertNotEquals(n4, n6);
    }

}
