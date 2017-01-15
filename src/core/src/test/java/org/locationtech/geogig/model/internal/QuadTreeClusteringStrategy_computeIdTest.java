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
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;

import com.google.common.hash.HashCode;
import com.vividsolutions.jts.geom.Envelope;

public class QuadTreeClusteringStrategy_computeIdTest {

    public static Envelope MAX_BOUNDS_WGS84 = new Envelope(-180, 180, -90, 90);

    @Test
    public void test_nullEnvelope() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        Node n = createNode("empty envelope", new Envelope());
        NodeId quadID = quadStrategy.computeId(n);
        assertNull(quadID);

        n = createNode("empty envelope", null);
        quadID = quadStrategy.computeId(n);
        assertNull(quadID);
    }

    // this polygon should go it the root node
    @Test
    public void test_level0() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        Node n = createNode("node", MAX_BOUNDS_WGS84, new Quadrant[] {});
        NodeId quadID = quadStrategy.computeId(n);

        assertEquals(0, quadStrategy.quadrantsByDepth(quadID).size());
        assertEquals(-1, quadStrategy.bucket(quadID, 0));
    }

    @Test
    public void test_level1() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        // create quad-sized node for each of the first level (1/4 world) quads
        for (Quadrant q : Quadrant.values()) {
            Quadrant[] location = new Quadrant[] { q };
            Node n = createNode("node", MAX_BOUNDS_WGS84, location);
            NodeId quadID = quadStrategy.computeId(n);

            // should only be one level deep (too big to go further)
            assertEquals(1, quadStrategy.quadrantsByDepth(quadID).size());
            assertEquals(location[0].getBucketNumber(), quadStrategy.bucket(quadID, 0));
        }
    }

    @Test
    public void test_level2() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        // create quad-sized node for each of the first level (1/8 world) quads
        for (Quadrant q1 : Quadrant.values()) {
            for (Quadrant q2 : Quadrant.values()) {
                Quadrant[] location = new Quadrant[] { q1, q2 };
                Node n = createNode("node", MAX_BOUNDS_WGS84, location);
                NodeId quadID = quadStrategy.computeId(n);

                // should only be 2 levels deep (too big to go further)
                assertEquals(2, quadStrategy.quadrantsByDepth(quadID).size());
                assertEquals(location[0].getBucketNumber(), quadStrategy.bucket(quadID, 0));
                assertEquals(location[1].getBucketNumber(), quadStrategy.bucket(quadID, 1));

            }
        }
    }

    @Test
    public void test_level3() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        // create quad-sized node for each of the first level (1/16 world) quads
        for (Quadrant q1 : Quadrant.values()) {
            for (Quadrant q2 : Quadrant.values()) {
                for (Quadrant q3 : Quadrant.values()) {
                    Quadrant[] location = new Quadrant[] { q1, q2, q3 };
                    Node n = createNode("node", MAX_BOUNDS_WGS84, location);
                    NodeId quadID = quadStrategy.computeId(n);

                    // should only be 3 levels deep (too big to go further)
                    assertEquals(3, quadStrategy.quadrantsByDepth(quadID).size());
                    assertEquals(location[0].getBucketNumber(), quadStrategy.bucket(quadID, 0));
                    assertEquals(location[1].getBucketNumber(), quadStrategy.bucket(quadID, 1));
                    assertEquals(location[2].getBucketNumber(), quadStrategy.bucket(quadID, 2));
                }
            }
        }
    }

    @Test
    public void test_maxlevel() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        // random path to depth
        Random rand = new Random();
        List<Quadrant> quads = new ArrayList<>(quadStrategy.getMaxDepth());
        for (int t = 0; t < quadStrategy.getMaxDepth(); t++) {
            quads.add(Quadrant.values()[rand.nextInt(3)]);
        }

        Quadrant[] location = (Quadrant[]) quads.toArray(new Quadrant[quads.size()]);
        Node n = createNode("node", MAX_BOUNDS_WGS84, location);
        NodeId quadID = quadStrategy.computeId(n);

        assertEquals(quadStrategy.quadrantsByDepth(quadID).size(), quads.size());
        for (int t = 0; t < quadStrategy.getMaxDepth(); t++) {
            assertEquals(location[t].getBucketNumber(), quadStrategy.bucket(quadID, t));
        }
    }

    @Test
    public void test_overMaxlevel() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        // random path to depth
        Random rand = new Random();
        List<Quadrant> quads = new ArrayList<>(quadStrategy.getMaxDepth() + 1);
        for (int t = 0; t < quadStrategy.getMaxDepth(); t++) {
            quads.add(Quadrant.values()[rand.nextInt(3)]);
        }

        Quadrant[] location = (Quadrant[]) quads.toArray(new Quadrant[quads.size()]);
        Node n = createNode("node", MAX_BOUNDS_WGS84, location);
        NodeId quadID = quadStrategy.computeId(n);

        assertEquals(quadStrategy.quadrantsByDepth(quadID).size(), quadStrategy.getMaxDepth());
        for (int t = 0; t < quadStrategy.getMaxDepth(); t++) {
            assertEquals(location[t].getBucketNumber(), quadStrategy.bucket(quadID, t));
        }
    }

    public static Node createNode(String name, Envelope bounds) {
        HashCode hc = ObjectId.HASH_FUNCTION.hashUnencodedChars(name);
        Node n = Node.create(name, new ObjectId(hc.asBytes()), ObjectId.NULL,
                RevObject.TYPE.FEATURE, bounds);
        return n;
    }

    // given a list of quandrants, create a node with a bounding box that JUST fits inside
    public static Node createNode(String name, Envelope projBounds, Quadrant[] quadrants) {
        Envelope envelope = projBounds;
        for (Quadrant quad : quadrants) {
            envelope = quad.slice(envelope);
        }
        envelope = new Envelope(envelope.getMinX() + envelope.getWidth() / 100.0,
                envelope.getMaxX() - envelope.getWidth() / 100.0,
                envelope.getMinY() + envelope.getHeight() / 100.0,
                envelope.getMaxY() - envelope.getHeight() / 100.0);

        return createNode(name, envelope);
    }

    public static QuadTreeClusteringStrategy createQuadStrategy() {
        ObjectStore store = new HeapObjectStore();

        int DEFAULT_MAX_DEPTH = 12;

        QuadTreeClusteringStrategy quadStrategy = ClusteringStrategyBuilder//
                .quadTree(store)//
                .original(RevTree.EMPTY)//
                .maxBounds(MAX_BOUNDS_WGS84)//
                .maxDepth(DEFAULT_MAX_DEPTH)//
                .build();

        return quadStrategy;
    }
}
