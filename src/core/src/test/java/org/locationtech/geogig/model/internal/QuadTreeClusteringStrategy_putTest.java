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

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.locationtech.geogig.model.internal.QuadTreeClusteringStrategy_computeIdTest.*;

public class QuadTreeClusteringStrategy_putTest {

    @Test
    public void testSimpleQuadDAG() {
        QuadTreeClusteringStrategy quadStrategy = createQuadStrategy();

        Quadrant[] location = new Quadrant[]{Quadrant.SW, Quadrant.NE};

        int t=0;
        Node n = null;
        DAG dag = null;

        //fill up children to max # of children - but not enough to split
        for (t=1;t<129;t++) {
            n = createNode("node A: "+t,MAX_BOUNDS_WGS84, location);
            quadStrategy.put(n);
            assertEquals(quadStrategy.root.getChildCount(), t);
            assertEquals(quadStrategy.root.numChildren(), t);
            assertEquals(quadStrategy.root.numUnpromotable(), 0);
            assertEquals(quadStrategy.root.numBuckets(), 0);
        }

        //next  add should cause a split - all the items will be put into the non-promotable section
        // (another possible situation (different algorithm) is that there will be 128 unpromotable and 1 child)
        n = createNode("node B: "+t,MAX_BOUNDS_WGS84, location);
        quadStrategy.put(n);

        assertEquals(quadStrategy.root.getChildCount(),t);
        assertEquals(quadStrategy.root.numChildren(), 0);
        assertEquals(quadStrategy.root.numUnpromotable(), 0);
        assertEquals(quadStrategy.root.numBuckets(), 1);


        assertEquals(quadStrategy.treeBuff.size(), 2);
        dag = findDAG(quadStrategy.treeBuff,"[0, 2]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), t);
        assertEquals(dag.getChildCount(), t);
        assertEquals(dag.numBuckets(), 0);
        assertEquals(dag.numChildren(), 0);

//we fill up the children with another 128 features
        for (t++ ;t<(128+128+2);t++) {
            n = createNode("node A: "+t,MAX_BOUNDS_WGS84, location);
            quadStrategy.put(n);
            assertEquals(quadStrategy.root.getChildCount(), t);
            assertEquals(quadStrategy.root.numChildren(), 0);
            assertEquals(quadStrategy.root.numUnpromotable(), 0);
            assertEquals(quadStrategy.root.numBuckets(), 1);

            dag = findDAG(quadStrategy.treeBuff,"[0, 2]");
            assertNotNull(dag  );
            assertEquals(dag.numUnpromotable()+dag.numChildren(), t);
            assertEquals(dag.getChildCount(), t);
            assertEquals(dag.numBuckets(), 0);
        }

        //not we have the children full
        dag = findDAG(quadStrategy.treeBuff,"[0, 2]");
        assertNotNull(dag  );
        assertEquals(dag.getChildCount(), t-1);
        assertEquals(dag.numBuckets(), 0);

        //we add another one - this will fill up the children.  The node will attempt to split, but be unable to
        // all the children (128 from before and this one) will be put into nonPromotable.
        // NOTE: possible situation (different algo) would be to have one child left over

        n = createNode("node B: "+t,MAX_BOUNDS_WGS84, location);
        quadStrategy.put(n);

        assertEquals(quadStrategy.root.getChildCount(),t);
        assertEquals(quadStrategy.root.numChildren(), 0);
        assertEquals(quadStrategy.root.numUnpromotable(), 0);
        assertEquals(quadStrategy.root.numBuckets(), 1);


        assertEquals(quadStrategy.treeBuff.size(), 2);
        dag = findDAG(quadStrategy.treeBuff,"[0, 2]");
        assertNotNull(dag  );
        assertEquals(dag.numUnpromotable(), t);
        assertEquals(dag.getChildCount(), t);
        assertEquals(dag.numBuckets(), 0);
        assertEquals(dag.numChildren(), 0);


        int ttt=0;
    }


    public static DAG findDAG(Map<TreeId, DAG> map, String key) {
        for(TreeId id : map.keySet() ) {
            if (id.toString().equals(key))
                 return map.get(id);
        }
        return null;
    }

}
