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
import static org.locationtech.geogig.model.internal.QuadTreeClusteringStrategy_computeIdTest.MAX_BOUNDS_WGS84;
import static org.locationtech.geogig.model.internal.QuadTreeClusteringStrategy_computeIdTest.createNode;
import static org.locationtech.geogig.model.internal.QuadTreeClusteringStrategy_computeIdTest.createQuadStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.locationtech.geogig.model.Node;

public class QuadTreeClusteringStrategy_putTest {

    @Test
    public void testSimpleSplitting() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        // giant one at the root
        putNode(quadStrategy, new Quadrant[] {}); // world node

        assertEquals(1, quadStrategy.root.getChildCount());
        assertEquals(1, quadStrategy.root.numChildren());
        assertEquals(0, quadStrategy.root.numBuckets());

        // fill up children (not spit)
        putNodes(127, quadStrategy,
                new Quadrant[] { Quadrant.SW, Quadrant.NW, Quadrant.NE, Quadrant.SE });

        assertEquals(128, quadStrategy.root.getChildCount());
        assertEquals(128, quadStrategy.root.numChildren());
        assertEquals(0, quadStrategy.root.numBuckets());

        // cause split - will be 1 at the root (non-promotable) and 128 in [0]
        putNode(quadStrategy,
                new Quadrant[] { Quadrant.SW, Quadrant.NW, Quadrant.NE, Quadrant.SE });

        assertEquals(129, quadStrategy.root.getChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(2, quadStrategy.root.numBuckets());

        DAG dag = findDAG(quadStrategy.treeBuff, "[0]");
        assertNotNull(dag);
        assertEquals(128, dag.getChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(128, dag.numChildren());

        // cause another multi-level split - will be 1 at the root (non-promotable) and 129
        // unpromotable in [0,1,2,3]
        // intermediate trees will be empty (just one bucket)
        putNode(quadStrategy,
                new Quadrant[] { Quadrant.SW, Quadrant.NW, Quadrant.NE, Quadrant.SE });

        assertEquals(130, quadStrategy.root.getChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(2, quadStrategy.root.numBuckets());

        // [0] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff, "[0]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [0,1] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff, "[0, 1]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [0,1,2] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff, "[0, 1, 2]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [0,1,2,3] -> will have the 129 unpromotable children
        dag = findDAG(quadStrategy.treeBuff, "[0, 1, 2, 3]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // another giant one at the root
        putNode(quadStrategy, new Quadrant[] {}); // world node
        assertEquals(131, quadStrategy.root.getChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(2, quadStrategy.root.numBuckets());

        // put one in a totally different quad
        putNode(quadStrategy,
                new Quadrant[] { Quadrant.NW, Quadrant.NE, Quadrant.NE, Quadrant.SE }); // world
                                                                                        // node
        assertEquals(132, quadStrategy.root.getChildCount());
        assertEquals(0, quadStrategy.root.numChildren());
        assertEquals(3, quadStrategy.root.numBuckets());

        // [1]
        dag = findDAG(quadStrategy.treeBuff, "[1]");
        assertNotNull(dag);
        assertEquals(1, dag.getChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(1, dag.numChildren());

        // fill up children of [1]
        putNodes(127, quadStrategy,
                new Quadrant[] { Quadrant.NW, Quadrant.NE, Quadrant.NE, Quadrant.SE });

        dag = findDAG(quadStrategy.treeBuff, "[1]");
        assertNotNull(dag);
        assertEquals(128, dag.getChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(128, dag.numChildren());

        // cause [1] to split
        putNode(quadStrategy,
                new Quadrant[] { Quadrant.NW, Quadrant.NE, Quadrant.NE, Quadrant.SE });

        // [1] will be empty (just a link)
        dag = findDAG(quadStrategy.treeBuff, "[1]");
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [1, 2] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff, "[1, 2]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [1,2,2] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff, "[1, 2, 2]");
        assertNotNull(dag);
        assertEquals(129, dag.getChildCount());
        assertEquals(1, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // [1,2,2,3] -> will have the 129 unpromotable children
        dag = findDAG(quadStrategy.treeBuff, "[1, 2, 2, 3]");
        assertNotNull(dag);
        // assertEquals(dag.numUnpromotable(), 129);
        assertEquals(129, dag.getChildCount());
        assertEquals(0, dag.numBuckets());
        assertEquals(0, dag.numChildren());

        // lets add some unpromotables to existing nodes

        // [1]
        putNode(quadStrategy, new Quadrant[] { Quadrant.NW });
        // [1] will have one un-promotable
        dag = findDAG(quadStrategy.treeBuff, "[1]");
        /// assertEquals(dag.numUnpromotable(), 1);

        // [1, 2]
        putNode(quadStrategy, new Quadrant[] { Quadrant.NW, Quadrant.NE });
        // [1, 2] will have one un-promotable
        dag = findDAG(quadStrategy.treeBuff, "[1, 2]");
        /// assertEquals(dag.numUnpromotable(), 1);

        // [1, 2, 2]
        putNode(quadStrategy, new Quadrant[] { Quadrant.NW, Quadrant.NE, Quadrant.NE });

        // [1, 2, 2] will have one un-promotable
        dag = findDAG(quadStrategy.treeBuff, "[1, 2, 2]");
        /// assertEquals(dag.numUnpromotable(), 1);
    }

    public static List<Node> putNodes(int n, QuadTreeClusteringStrategy quad, Quadrant[] location) {
        List<Node> result = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            result.add(putNode(quad, location));
        }
        return result;
    }

    public static Node putNode(QuadTreeClusteringStrategy quad, Quadrant[] location) {
        long fnumb = quad.root == null ? 0 : quad.root.getChildCount();
        String quadInfo = "[";
        for (Quadrant q : location) {
            quadInfo += q.name() + ",";
        }
        quadInfo += "] - [";
        for (Quadrant q : location) {
            quadInfo += q.getBucketNumber() + ",";
        }
        quadInfo += "]";

        Node n = createNode("node # " + fnumb + ", at " + quadInfo, MAX_BOUNDS_WGS84, location);

        quad.put(n);
        return n;
    }

    public static DAG findDAG(Map<TreeId, DAG> map, String key) {
        for (TreeId id : map.keySet()) {
            if (id.toString().equals(key))
                return map.get(id);
        }
        return null;
    }

}
