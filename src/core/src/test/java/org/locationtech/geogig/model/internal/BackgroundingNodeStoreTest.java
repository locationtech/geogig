/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 */
package org.locationtech.geogig.model.internal;


import com.vividsolutions.jts.geom.Envelope;
import org.junit.Test;
import org.locationtech.geogig.model.Node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class BackgroundingNodeStoreTest {

    @Test
    public void trivialPutGetTest() {
        HashNodeStore hns = new HashNodeStore();
        BackgroundingNodeStore bns = new BackgroundingNodeStore(hns);

        BackgroundingNodeStore.SaveNodeItem item = createItem();
        bns.put(item.nodeId, item.node);

        DAGNode node = bns.get(item.nodeId);

        assertEquals(node, item.node);
    }

    @Test
    public void testReadWriteReadWrite() {

        HashNodeStore hns = new HashNodeStore();
        BackgroundingNodeStore bns = new BackgroundingNodeStore(hns);

        BackgroundingNodeStore.SaveNodeItem item = createItem();
        bns.put(item.nodeId, item.node);

        DAGNode node = bns.get(item.nodeId);
        assertEquals(node, item.node);

        item = createItem();
        bns.put(item.nodeId, item.node);

        node = bns.get(item.nodeId);
        assertEquals(node, item.node);
    }

    @Test
    public void testCloseWorks() {
        HashNodeStore hns = new HashNodeStore();
        BackgroundingNodeStore bns = new BackgroundingNodeStore(hns);

        BackgroundingNodeStore.SaveNodeItem item = createItem();
        bns.put(item.nodeId, item.node);
        bns.close();

        assertTrue(hns.closed);
        assertTrue(bns.state != BackgroundingNodeStore.StateEnum.WRITING);
    }

    @Test(expected = RuntimeException.class)
    public void testConsumerException() throws InterruptedException {
        Exception except = new RuntimeException("test case error");
        BackgroundingNodeStore.SaveNodeItem item = createItem();

        NodeStore ns = mock(NodeStore.class);
        doThrow(except).when(ns).put(eq(item.nodeId), eq(item.node));

        BackgroundingNodeStore bns = new BackgroundingNodeStore(ns);

        bns.put(item.nodeId, item.node);

        bns.startReading();//do this so we don't have to do an actual read (waits for writes to finish)

    }

    //run this test to check for very sneeky low-probability issues
    // @Test
    public void t1() {
        for (int t = 0; t < 100000; t++) {
            try {
                System.out.println(t);
                testConsumerExceptionDoesNotCauseDeadLock();
                assertTrue(false); //shouldnt get here
            } catch (Exception e) {
                assertEquals(e.getMessage(), "test case error");
            }
        }
    }

    @Test(timeout = 1000, expected = RuntimeException.class)
    public void testConsumerExceptionDoesNotCauseDeadLock() {

        Exception except = new RuntimeException("test case error");
        BackgroundingNodeStore.SaveNodeItem item = createItem();

        NodeStore ns = mock(NodeStore.class);
        doThrow(except).when(ns).put(eq(item.nodeId), eq(item.node));

        BackgroundingNodeStore bns = new BackgroundingNodeStore(ns, 3);
        try {

            //this will fill up the queue - it might cause a deadlock
            //as the BlockingQueue will block waiting for the
            // (non-existing) consumer to empty the queue...
            bns.put(item.nodeId, item.node);
            bns.put(item.nodeId, item.node);
            bns.put(item.nodeId, item.node);
            bns.put(item.nodeId, item.node);
            bns.put(item.nodeId, item.node);
            bns.put(item.nodeId, item.node);
            bns.put(item.nodeId, item.node);
            bns.put(item.nodeId, item.node);
        } finally {
            bns.close();
        }

        //the above should report the issue
    }


    int nodeNum = 0;

    public BackgroundingNodeStore.SaveNodeItem createItem() {
        Envelope bounds = new Envelope(-1, -1, 0, 0);
        Node n = QuadTreeClusteringStrategy_computeIdTest.createNode("node: " + nodeNum++, bounds);
        NodeId id = new NodeId(n.getName(), bounds);
        return new BackgroundingNodeStore.SaveNodeItem(id, new DAGNode.DirectDAGNode(n));
    }
}
