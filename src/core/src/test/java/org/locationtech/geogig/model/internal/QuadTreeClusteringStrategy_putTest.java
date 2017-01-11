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


import org.junit.Test;
import org.locationtech.geogig.model.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.locationtech.geogig.model.internal.QuadTreeClusteringStrategy_computeIdTest.*;

public class QuadTreeClusteringStrategy_putTest {

    @Test
    public void testSimpleSplitting() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        //giant one at the root
        putNode(quadStrategy, new Quadrant[]{}); // world node

        assertEquals(quadStrategy.root.getChildCount(), 1);
        assertEquals(quadStrategy.root.numChildren(), 1);
        assertEquals(quadStrategy.root.numUnpromotable(), 0);
        assertEquals(quadStrategy.root.numBuckets(), 0);

        //fill up children (not spit)
        putNodes(127,quadStrategy, new Quadrant[]{Quadrant.SW, Quadrant.NW, Quadrant.NE, Quadrant.SE});

        assertEquals(quadStrategy.root.getChildCount(), 128);
        assertEquals(quadStrategy.root.numChildren(), 128);
        assertEquals(quadStrategy.root.numUnpromotable(), 0);
        assertEquals(quadStrategy.root.numBuckets(), 0);

        //cause split - will be 1 at the root (non-promotable) and 128 in [0]
        putNode(quadStrategy, new Quadrant[]{Quadrant.SW, Quadrant.NW, Quadrant.NE, Quadrant.SE});

        assertEquals(quadStrategy.root.getChildCount(),129);
        assertEquals(quadStrategy.root.numChildren(), 0);
        assertEquals(quadStrategy.root.numUnpromotable(), 1);
        assertEquals(quadStrategy.root.numBuckets(), 1);


        DAG dag = findDAG(quadStrategy.treeBuff,"[0]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 0);
        assertEquals(dag.getChildCount(), 128);
        assertEquals(dag.numBuckets(), 0);
        assertEquals(dag.numChildren(), 128);

        //cause another multi-level split - will be 1 at the root (non-promotable) and 129 unpromotable in [0,1,2,3]
        // intermediate trees will be empty (just one bucket)
        putNode(quadStrategy, new Quadrant[]{Quadrant.SW, Quadrant.NW, Quadrant.NE, Quadrant.SE});

        assertEquals(quadStrategy.root.getChildCount(), 130);
        assertEquals(quadStrategy.root.numChildren(), 0);
        assertEquals(quadStrategy.root.numUnpromotable(), 1);
        assertEquals(quadStrategy.root.numBuckets(), 1);

        //[0] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff,"[0]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 0);
        assertEquals(dag.getChildCount(), 129);
        assertEquals(dag.numBuckets(), 1);
        assertEquals(dag.numChildren(), 0);

        //[0,1] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff,"[0, 1]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 0);
        assertEquals(dag.getChildCount(), 129);
        assertEquals(dag.numBuckets(), 1);
        assertEquals(dag.numChildren(), 0);

        //[0,1,2] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff,"[0, 1, 2]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 0);
        assertEquals(dag.getChildCount(), 129);
        assertEquals(dag.numBuckets(), 1);
        assertEquals(dag.numChildren(), 0);

        //[0,1,2,3] -> will have the 129 unpromotable children
        dag = findDAG(quadStrategy.treeBuff,"[0, 1, 2, 3]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 129);
        assertEquals(dag.getChildCount(), 129);
        assertEquals(dag.numBuckets(), 0);
        assertEquals(dag.numChildren(), 0);


        //another giant one at the root
        putNode(quadStrategy, new Quadrant[]{}); // world node
        assertEquals(quadStrategy.root.getChildCount(), 131);
        assertEquals(quadStrategy.root.numChildren(), 0);
        assertEquals(quadStrategy.root.numUnpromotable(), 2);
        assertEquals(quadStrategy.root.numBuckets(), 1);

        //put one in a totally different quad
        putNode(quadStrategy, new Quadrant[]{Quadrant.NW, Quadrant.NE, Quadrant.NE, Quadrant.SE}); // world node
        assertEquals(quadStrategy.root.getChildCount(), 132);
        assertEquals(quadStrategy.root.numChildren(), 0);
        assertEquals(quadStrategy.root.numUnpromotable(), 2);
        assertEquals(quadStrategy.root.numBuckets(), 2);


        //[1]
        dag = findDAG(quadStrategy.treeBuff,"[1]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 0);
        assertEquals(dag.getChildCount(), 1);
        assertEquals(dag.numBuckets(), 0);
        assertEquals(dag.numChildren(), 1);

        //fill up children of [1]
        putNodes(127,quadStrategy, new Quadrant[]{Quadrant.NW, Quadrant.NE, Quadrant.NE, Quadrant.SE});

        dag = findDAG(quadStrategy.treeBuff,"[1]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 0);
        assertEquals(dag.getChildCount(), 128);
        assertEquals(dag.numBuckets(), 0);
        assertEquals(dag.numChildren(), 128);

        //cause [1] to split
        putNode(quadStrategy, new Quadrant[]{Quadrant.NW, Quadrant.NE, Quadrant.NE, Quadrant.SE});

        //[1] will be empty (just a link)
        dag = findDAG(quadStrategy.treeBuff,"[1]");
        assertEquals(dag.numUnpromotable(), 0);
        assertEquals(dag.getChildCount(), 129);
        assertEquals(dag.numBuckets(), 1);
        assertEquals(dag.numChildren(), 0);

        //[1, 2] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff,"[1, 2]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 0);
        assertEquals(dag.getChildCount(), 129);
        assertEquals(dag.numBuckets(), 1);
        assertEquals(dag.numChildren(), 0);

        //[1,2,2] -> will be empty (just a link node)
        dag = findDAG(quadStrategy.treeBuff,"[1, 2, 2]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 0);
        assertEquals(dag.getChildCount(), 129);
        assertEquals(dag.numBuckets(), 1);
        assertEquals(dag.numChildren(), 0);

        //[1,2,2,3] -> will have the 129 unpromotable children
        dag = findDAG(quadStrategy.treeBuff,"[1, 2, 2, 3]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), 129);
        assertEquals(dag.getChildCount(), 129);
        assertEquals(dag.numBuckets(), 0);
        assertEquals(dag.numChildren(), 0);

        //lets add some unpromotables to existing nodes


        //[1]
        putNode(quadStrategy, new Quadrant[]{Quadrant.NW});
        //[1] will have one un-promotable
        dag = findDAG(quadStrategy.treeBuff,"[1]");
        assertEquals(dag.numUnpromotable(), 1);

        //[1, 2]
        putNode(quadStrategy, new Quadrant[]{Quadrant.NW, Quadrant.NE});
        //[1, 2] will have one un-promotable
        dag = findDAG(quadStrategy.treeBuff,"[1, 2]");
        assertEquals(dag.numUnpromotable(), 1);

        //[1, 2, 2]
        putNode(quadStrategy, new Quadrant[]{Quadrant.NW, Quadrant.NE, Quadrant.NE});

        //[1, 2, 2] will have one un-promotable
        dag = findDAG(quadStrategy.treeBuff,"[1, 2, 2]");
        assertEquals(dag.numUnpromotable(), 1);
    }



    public static List<Node> putNodes(int n, QuadTreeClusteringStrategy quad, Quadrant[] location) {
        List<Node> result = new ArrayList<>(n);
       for (int t=0;t<n;t++) {
           result.add(putNode(quad,location));
       }
       return result;
    }

    public static Node putNode(QuadTreeClusteringStrategy quad, Quadrant[] location) {
        long fnumb = quad.root == null ? 0 : quad.root.getChildCount();
        String quadInfo = "[";
        for(Quadrant q : location) {
            quadInfo += q.name() +",";
        }
        quadInfo += "] - [";
        for(Quadrant q : location) {
            quadInfo += q.getBucketNumber() +",";
        }
        quadInfo += "]";

        Node n = createNode("node # "+fnumb+", at "+quadInfo,MAX_BOUNDS_WGS84, location);

        quad.put(n);
        return n;
    }


    public static DAG findDAG(Map<TreeId, DAG> map, String key) {
        for(TreeId id : map.keySet() ) {
            if (id.toString().equals(key))
                 return map.get(id);
        }
        return null;
    }

}
