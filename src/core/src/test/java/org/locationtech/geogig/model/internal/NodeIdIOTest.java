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
import static org.locationtech.geogig.model.internal.Quadrant.NE;
import static org.locationtech.geogig.model.internal.Quadrant.NW;
import static org.locationtech.geogig.model.internal.Quadrant.SE;
import static org.locationtech.geogig.model.internal.Quadrant.SW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class NodeIdIOTest {

    @Test
    public void testCanonicalNodeIds() throws Exception {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        List<NodeId> nodes = new ArrayList<>();
        int size = 10;
        for (int i = 0; i < size; i++) {
            NodeId n = new CanonicalNodeId("node-" + i);
            nodes.add(n);
            NodeIdIO.write(n, out);
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(out.toByteArray());

        List<NodeId> decoded = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            NodeId n = NodeIdIO.read(in);
            decoded.add(n);
        }
        assertEquals(nodes, decoded);
    }

    @Test
    public void testEmptyQuads() throws IOException {
        testQuadrants(new Quadrant[0]);
    }

    @Test
    public void testQuads() throws IOException {
        testQuadrants(new Quadrant[] { NE, SE, NW, SW });
        testQuadrants(new Quadrant[] { NE, NE, NE, NE, NE, NE, SE, SE, SE, SE, SE, SE, SE });
        testQuadrants(new Quadrant[] { SW, NE, SE, NE, NW, NW });
    }

    private void testQuadrants(Quadrant[] quads) throws IOException {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        NodeIdIO.writeQuadrants(quads, out);
        byte[] encoded = out.toByteArray();

        Quadrant[] decoded = NodeIdIO.readQuadrants(ByteStreams.newDataInput(encoded));

        assertEquals(Lists.newArrayList(quads), Lists.newArrayList(decoded));
    }

    @Test
    public void testQuadTreeNodeIds() throws Exception {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        List<NodeId> nodes = new ArrayList<>();
        int size = 10;
        for (int i = 0; i < size; i++) {
            Random rnd = new Random();
            Quadrant[] quads = new Quadrant[2 * i];
            for (int j = 0; j < quads.length; j++) {
                quads[j] = Quadrant.VALUES[rnd.nextInt(4)];
            }
            NodeId n = new QuadTreeNodeId("node-" + i, quads);
            nodes.add(n);
            NodeIdIO.write(n, out);
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(out.toByteArray());

        List<NodeId> decoded = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            NodeId n = NodeIdIO.read(in);
            decoded.add(n);
        }
        assertEquals(nodes, decoded);
    }
}
