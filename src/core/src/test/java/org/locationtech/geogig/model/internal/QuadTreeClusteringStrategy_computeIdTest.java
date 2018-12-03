/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.jts.geom.Envelope;

public class QuadTreeClusteringStrategy_computeIdTest {

    @Rule
    public QuadTreeTestSupport support = new QuadTreeTestSupport();

    @Test
    public void test_nullEnvelope() {
        QuadTreeClusteringStrategy quadStrategy = support.newStrategy();

        Node n = support.createNode("empty envelope", new Envelope());
        NodeId quadID = quadStrategy.computeId(n);
        assertNotNull(quadID);
        assertNull(quadID.value());

        n = support.createNode("empty envelope", (Envelope) null);
        quadID = quadStrategy.computeId(n);
        assertNotNull(quadID);
        assertNull(quadID.value());
    }

    // this polygon should go it the root node
    @Test
    public void test_level0() {
        QuadTreeClusteringStrategy quadStrategy = support.newStrategy();

        Node n = support.createNode("node", new Quadrant[] {});
        NodeId quadID = quadStrategy.computeId(n);

        assertEquals(0, quadStrategy.quadrantsByDepth(quadID, 8).size());
        assertEquals(-1, quadStrategy.bucket(quadID, 0));
    }

    @Test
    public void test_level1() {
        QuadTreeClusteringStrategy quadStrategy = support.newStrategy();

        // create quad-sized node for each of the first level (1/4 world) quads
        for (Quadrant q : Quadrant.values()) {
            Node n = support.createNode("node", q);
            NodeId quadID = quadStrategy.computeId(n);

            // should only be one level deep (too big to go further)
            assertEquals(1, quadStrategy.quadrantsByDepth(quadID, 8).size());
            assertEquals(q.getBucketNumber(), quadStrategy.bucket(quadID, 0));
        }
    }

    @Test
    public void test_level2() {
        QuadTreeClusteringStrategy quadStrategy = support.newStrategy();

        // create quad-sized node for each of the first level (1/8 world) quads
        for (Quadrant q1 : Quadrant.values()) {
            for (Quadrant q2 : Quadrant.values()) {
                Node n = support.createNode("node", q1, q2);
                NodeId quadID = quadStrategy.computeId(n);

                // should only be 2 levels deep (too big to go further)
                assertEquals(2, quadStrategy.quadrantsByDepth(quadID, 8).size());
                assertEquals(q1.getBucketNumber(), quadStrategy.bucket(quadID, 0));
                assertEquals(q2.getBucketNumber(), quadStrategy.bucket(quadID, 1));

            }
        }
    }

    @Test
    public void test_level3() {
        QuadTreeClusteringStrategy quadStrategy = support.newStrategy();

        // create quad-sized node for each of the first level (1/16 world) quads
        for (Quadrant q1 : Quadrant.values()) {
            for (Quadrant q2 : Quadrant.values()) {
                for (Quadrant q3 : Quadrant.values()) {
                    Node n = support.createNode("node", q1, q2, q3);
                    NodeId quadID = quadStrategy.computeId(n);

                    // should only be 3 levels deep (too big to go further)
                    assertEquals(3, quadStrategy.quadrantsByDepth(quadID, 8).size());
                    assertEquals(q1.getBucketNumber(), quadStrategy.bucket(quadID, 0));
                    assertEquals(q2.getBucketNumber(), quadStrategy.bucket(quadID, 1));
                    assertEquals(q3.getBucketNumber(), quadStrategy.bucket(quadID, 2));
                }
            }
        }
    }

    public @Test void test_maxlevelWGS84() {
        testMaxLevel(QuadTreeTestSupport.wgs84Bounds());
    }

    public @Test void test_maxlevelPseudoMercator() {
        testMaxLevel(QuadTreeTestSupport.epsg3857Bounds());
    }

    private void testMaxLevel(final Envelope maxBounds) {
        support.setMaxBounds(maxBounds);
        QuadTreeClusteringStrategy quadStrategy = support.newStrategy();

        final int maxDepth = quadStrategy.getMaxDepth();

        Random rnd = new Random();
        List<Quadrant> location = new ArrayList<>(maxDepth);

        for (int i = 0; i < maxDepth - 1; i++) {
            final Quadrant rndquad = Quadrant.VALUES[rnd.nextInt(3)];
            location.add(rndquad);

            Node node = support.createNode("node", location);
            Envelope nodeBounds = node.bounds().get();
            NodeId nodeId = new NodeId("node", nodeBounds);

            List<Quadrant> quadrantsByDepth;
            quadrantsByDepth = quadStrategy.quadrantsByDepth(nodeId, location.size());
            assertEquals("at index " + i, location.size(), quadrantsByDepth.size());
            assertEquals("at index " + i, location.get(i), quadrantsByDepth.get(i));
        }
    }

    @Test
    public void test_overMaxlevel() {
        QuadTreeClusteringStrategy quadStrategy = support.newStrategy();
        int maxDepth = quadStrategy.getMaxDepth();

        // random path to depth
        Random rand = new Random();
        List<Quadrant> quads = new ArrayList<>(8);
        for (int t = 0; t < maxDepth; t++) {
            quads.add(Quadrant.values()[rand.nextInt(3)]);
        }

        Node n = support.createNode("node", quads);
        NodeId quadID = quadStrategy.computeId(n);
        Envelope nodeBounds = quadID.value();

        assertEquals(quads.get(maxDepth - 1),
                quadStrategy.computeQuadrant(nodeBounds, maxDepth - 1));
        assertNull(quadStrategy.computeQuadrant(nodeBounds, maxDepth));
        assertNull(quadStrategy.computeQuadrant(nodeBounds, maxDepth + 1));

        assertEquals(maxDepth, quadStrategy.quadrantsByDepth(quadID, maxDepth + 10).size());
        for (int t = 0; t < maxDepth; t++) {
            assertEquals(quads.get(t).getBucketNumber(), quadStrategy.bucket(quadID, t));
        }
    }
}
