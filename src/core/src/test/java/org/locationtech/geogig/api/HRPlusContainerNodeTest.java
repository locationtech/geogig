/* Copyright (c) 2014 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.api;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.vividsolutions.jts.geom.Envelope;

public class HRPlusContainerNodeTest {

    @Test
        public void testGetObjectId(){
            // TODO the source method needs an implementation
            HRPlusContainerNode node = new HRPlusContainerNode();
            assertEquals(null, node.getObjectId());
        }

    @Test
        public void testGetParent(){
            // Can set parent in constructor
            HRPlusNode parent = new HRPlusNode(new ObjectId(), new Envelope(), new ObjectId());
            HRPlusContainerNode node = new HRPlusContainerNode(parent, new ObjectId());
            assertEquals(parent, node.getParentNode());
        }

    @Test
        public void testSetParent(){
            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusNode parent = new HRPlusNode(new ObjectId(), new Envelope(), new ObjectId());
            assertEquals(null, node.getParentNode());
            node.setParentNode(parent);
            assertEquals(parent, node.getParentNode());
        }

    @Test
        public void testGetNumNodesEmptyContainer(){
            HRPlusContainerNode node = new HRPlusContainerNode();
            assertEquals(0, node.getNumNodes());
        }

    @Test
        public void testGetNumNodesOneNode(){
            // Create one node, one container. numNodes should be 1.
            ObjectId vid = ObjectId.forString("version");
            HRPlusNode child = new HRPlusNode(new ObjectId(), new Envelope(), vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);
            assertEquals(1, node.getNumNodes());
        }

    @Test
        public void testGetNumNodesTwoNodesUniqueObjectIds(){
            // Two unique nodes => numNodes is 2
            ObjectId vid = ObjectId.forString("version");
            ObjectId idA = ObjectId.forString("zardoz");
            ObjectId idB = ObjectId.forString("zodraz");
            HRPlusNode childA = new HRPlusNode(idA, new Envelope(), vid);
            HRPlusNode childB = new HRPlusNode(idB, new Envelope(), vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(childA); node.addNode(childB);
            assertEquals(2, node.getNumNodes());
        }

    @Test
        public void testGetNumNodesManyNodes(){
            ObjectId vid = ObjectId.forString("version");
            // Make sure all object ids are unique
            ObjectId idA = ObjectId.forString("a");
            ObjectId idB = ObjectId.forString("b");
            ObjectId idC = ObjectId.forString("c");
            ObjectId idD = ObjectId.forString("d");
            ObjectId idE = ObjectId.forString("e");
            ObjectId idF = ObjectId.forString("f");
            ObjectId idG = ObjectId.forString("g");
            ObjectId idH = ObjectId.forString("h");
            ObjectId idI = ObjectId.forString("i");
            ObjectId idJ = ObjectId.forString("j");
            // Create nodes
            HRPlusNode childA = new HRPlusNode(idA, new Envelope(),vid);
            HRPlusNode childB = new HRPlusNode(idB, new Envelope(),vid);
            HRPlusNode childC = new HRPlusNode(idC, new Envelope(),vid);
            HRPlusNode childD = new HRPlusNode(idD, new Envelope(),vid);
            HRPlusNode childE = new HRPlusNode(idE, new Envelope(),vid);
            HRPlusNode childF = new HRPlusNode(idF, new Envelope(),vid);
            HRPlusNode childG = new HRPlusNode(idG, new Envelope(),vid);
            HRPlusNode childH = new HRPlusNode(idH, new Envelope(),vid);
            HRPlusNode childI = new HRPlusNode(idI, new Envelope(),vid);
            HRPlusNode childJ = new HRPlusNode(idJ, new Envelope(),vid);
            // Create a container, add nodes
            HRPlusContainerNode node = new HRPlusContainerNode();
            // A funny thing here is that addNode doesn't ever split the container.
            // So this is overflowed, but doesn't matter for this test.
            node.addNode(childA); node.addNode(childB);
            node.addNode(childC); node.addNode(childD);
            node.addNode(childE); node.addNode(childF);
            node.addNode(childG); node.addNode(childH);
            node.addNode(childI); node.addNode(childJ);

            assertEquals(10, node.getNumNodes());
        }

    @Test
        public void testGetNumNodesManyNodesManyContainers(){
            ObjectId vid = ObjectId.forString("version");
            ObjectId idA = ObjectId.forString("a");
            ObjectId idB = ObjectId.forString("b");
            ObjectId idC = ObjectId.forString("c");
            ObjectId idD = ObjectId.forString("d");
            ObjectId idE = ObjectId.forString("e");
            ObjectId idF = ObjectId.forString("f");
            ObjectId idG = ObjectId.forString("g");
            ObjectId idH = ObjectId.forString("h");
            ObjectId idI = ObjectId.forString("i");
            ObjectId idJ = ObjectId.forString("j");
            // Create nodes
            HRPlusNode childA = new HRPlusNode(idA, new Envelope(),vid);
            HRPlusNode childB = new HRPlusNode(idB, new Envelope(),vid);
            HRPlusNode childC = new HRPlusNode(idC, new Envelope(),vid);
            HRPlusNode childD = new HRPlusNode(idD, new Envelope(),vid);
            HRPlusNode childE = new HRPlusNode(idE, new Envelope(),vid);
            HRPlusNode childF = new HRPlusNode(idF, new Envelope(),vid);
            HRPlusNode childG = new HRPlusNode(idG, new Envelope(),vid);
            HRPlusNode childH = new HRPlusNode(idH, new Envelope(),vid);
            HRPlusNode childI = new HRPlusNode(idI, new Envelope(),vid);
            HRPlusNode childJ = new HRPlusNode(idJ, new Envelope(),vid);
            // Create a container, add nodes
            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNodeA = new HRPlusContainerNode();
            HRPlusContainerNode subNodeB1 = new HRPlusContainerNode();
            HRPlusContainerNode subNodeB2 = new HRPlusContainerNode();
            // Create a hierarchy with max depth 3
            node.addNode(childA); node.addNode(childB);
            subNodeA.addNode(childC); subNodeA.addNode(childD);
            subNodeA.addNode(childE);
            subNodeB1.addNode(childF); subNodeB1.addNode(childG);
            subNodeB1.addNode(childH);
            subNodeB1.addNode(childI); subNodeB1.addNode(childJ);
            childA.setChild(subNodeA);
            childB.setChild(subNodeB1);
            childF.setChild(subNodeB2);
            // Method is not recursive, only checks the number of nodes in one container
            // This is because `getNumNodes` is used to check if we should split
            assertEquals(2, node.getNumNodes());
        }

    @Test
        public void testAddNode(){
            ObjectId vid = ObjectId.forString("version");

            HRPlusContainerNode node = new HRPlusContainerNode();

            assertEquals(0, node.getNumNodes());

            HRPlusNode child = new HRPlusNode(new ObjectId(), new Envelope(),vid);
            node.addNode(child);

            assertEquals(1, node.getNumNodes());
            // Adding should also change parentContainer
            assertEquals(node, child.getParentContainer());
        }

    @Test
        public void testRemoveNodeEmptyContainer(){
            HRPlusContainerNode node = new HRPlusContainerNode();
            assertFalse(node.removeNode(new HRPlusNode(new Envelope(), new ObjectId())));
        }

    @Test
        public void testRemoveNodeNonEmptyContainer(){
            ObjectId vid = ObjectId.forString("version");
            ObjectId id = ObjectId.forString("objectid");
            HRPlusNode child = new HRPlusNode(id, new Envelope(),vid);

            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            assertTrue(node.removeNode(child));
        }


    @Test
        public void testRemoveNodeFromSubContainer(){
            ObjectId vid = ObjectId.forString("version");

            ObjectId idA = ObjectId.forString("objectid");
            ObjectId idB = ObjectId.forString("anotherobjectid");

            HRPlusNode childA = new HRPlusNode(idA, new Envelope(),vid);
            HRPlusNode childB = new HRPlusNode(idB, new Envelope(),vid);

            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNode = new HRPlusContainerNode();

            node.addNode(childA);
            childA.setChild(subNode);
            subNode.addNode(childB);
            // Shouldn't remove anything. Remove doesn't act recursively on containers
            assertFalse(node.removeNode(childB));
            // Should remove, just a sanity check
            assertTrue(node.removeNode(childA));
        }

    @Test
        public void testGetNodesEmptyContainer(){
            HRPlusContainerNode node = new HRPlusContainerNode();
            // empty list
            List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();
            assertEquals(nodes, node.getNodes());
        }

    @Test
        public void testGetNodesNonEmptyContainer(){
            ObjectId vid = ObjectId.forString("version");
            HRPlusNode child = new HRPlusNode(new ObjectId(), new Envelope(),vid);

            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            List<HRPlusNode> nodes = node.getNodes();

            assertEquals(1, nodes.size());
            assertTrue(nodes.contains(child));
        }

    @Test
        public void testGetNodesManyNodes(){
            ObjectId vid = ObjectId.forString("version");
            // Create many unique object ids
            ObjectId idA = ObjectId.forString("a");
            ObjectId idB = ObjectId.forString("b");
            ObjectId idC = ObjectId.forString("c");
            ObjectId idD = ObjectId.forString("d");
            ObjectId idE = ObjectId.forString("e");
            ObjectId idF = ObjectId.forString("f");
            ObjectId idG = ObjectId.forString("g");
            ObjectId idH = ObjectId.forString("h");
            ObjectId idI = ObjectId.forString("i");
            ObjectId idJ = ObjectId.forString("j");
            // Create many nodes
            HRPlusNode childA = new HRPlusNode(idA, new Envelope(),vid);
            HRPlusNode childB = new HRPlusNode(idB, new Envelope(),vid);
            HRPlusNode childC = new HRPlusNode(idC, new Envelope(),vid);
            HRPlusNode childD = new HRPlusNode(idD, new Envelope(),vid);
            HRPlusNode childE = new HRPlusNode(idE, new Envelope(),vid);
            HRPlusNode childF = new HRPlusNode(idF, new Envelope(),vid);
            HRPlusNode childG = new HRPlusNode(idG, new Envelope(),vid);
            HRPlusNode childH = new HRPlusNode(idH, new Envelope(),vid);
            HRPlusNode childI = new HRPlusNode(idI, new Envelope(),vid);
            HRPlusNode childJ = new HRPlusNode(idJ, new Envelope(),vid);
            // Create a container, add node
            HRPlusContainerNode node = new HRPlusContainerNode();

            node.addNode(childA); node.addNode(childB);
            node.addNode(childC); node.addNode(childD);
            node.addNode(childE); node.addNode(childF);
            node.addNode(childG); node.addNode(childH);
            node.addNode(childI); node.addNode(childJ);

            List<HRPlusNode> nodes = node.getNodes();
            assertEquals(10, nodes.size());
            List<HRPlusNode> expected = new ArrayList<HRPlusNode>();
            expected.add(childA); expected.add(childB);
            expected.add(childC); expected.add(childD);
            expected.add(childE); expected.add(childF);
            expected.add(childG); expected.add(childH);
            expected.add(childI); expected.add(childJ);
            assertTrue(nodes.containsAll(expected));
        }

    @Test
        public void testGetNodesSubContainer(){
            // getNodes ignores nodes in sub-containers
            ObjectId vid = ObjectId.forString("version");
            ObjectId idA = ObjectId.forString("idA");
            ObjectId idB = ObjectId.forString("idB");

            HRPlusNode childA = new HRPlusNode(idA, new Envelope(),vid);
            HRPlusNode childB = new HRPlusNode(idB, new Envelope(),vid);

            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNode = new HRPlusContainerNode();
            node.addNode(childA);
            subNode.addNode(childB);
            childA.setChild(subNode);

            List<HRPlusNode> nodes = node.getNodes();
            assertEquals(1, nodes.size());
            assertTrue(nodes.contains(childA));
        }

    @Test
        public void testIsLeafNonEmptyLeafChild(){
            ObjectId vid = ObjectId.forString("version");
            HRPlusNode child = new HRPlusNode(new ObjectId(), new Envelope(),vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            assertTrue(node.isLeaf());
        }

    @Test
        public void testIsLeafNonEmptyNonLeafChild(){
            ObjectId vid = ObjectId.forString("version");
            HRPlusNode child = new HRPlusNode(new ObjectId(), new Envelope(),vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNode = new HRPlusContainerNode();
            node.addNode(child);
            child.setChild(subNode);

            assertFalse(node.isLeaf());
            assertTrue(subNode.isLeaf());
        }

    @Test
        public void testIsLeafEmptyContainer(){
            HRPlusContainerNode node = new HRPlusContainerNode();
            assertTrue(node.isLeaf());
        }

    @Test
        public void testIsEmptyPass(){
            HRPlusContainerNode node = new HRPlusContainerNode();
            assertTrue(node.isEmpty());
        }

    @Test
        public void testIsEmptyFail(){
            ObjectId vid = ObjectId.forString("version");
            HRPlusNode child = new HRPlusNode(new ObjectId(), new Envelope(),vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            assertFalse(node.isEmpty());
        }

    @Test
        public void testIsOneStepAboveLeafLevelEmptyContainer(){
            HRPlusContainerNode node = new HRPlusContainerNode();
            assertFalse(node.isOneStepAboveLeafLevel());
        }

    @Test
        public void testIsOneStepAboveLeafLevelNonEmptyContainer(){
            ObjectId vid = ObjectId.forString("version");
            HRPlusNode child = new HRPlusNode(new ObjectId(), new Envelope(),vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            assertFalse(node.isOneStepAboveLeafLevel());
        }

    @Test
        public void testIsOneStepAboveLeafLevelHasEmptySubContainer(){
            ObjectId vid = ObjectId.forString("version");
            HRPlusNode child = new HRPlusNode(new ObjectId(), new Envelope(),vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNode = new HRPlusContainerNode();
            node.addNode(child);
            child.setChild(subNode);

            assertTrue(node.isOneStepAboveLeafLevel());
        }

    @Test
        public void testIsOneStepAboveLeafLevelHasNonEmptySubContainer(){
            ObjectId vid = ObjectId.forString("version");
            HRPlusNode childA = new HRPlusNode(new ObjectId(), new Envelope(),vid);
            HRPlusNode childB = new HRPlusNode(new ObjectId(), new Envelope(),vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNode = new HRPlusContainerNode();
            node.addNode(childA);
            childA.setChild(subNode);
            subNode.addNode(childB);
            // Still a leaf provided nodes in sub container are leaves
            assertTrue(node.isOneStepAboveLeafLevel());
        }

    @Test
        public void testIsOneStepAboveLeafLevelHasSubSubContainer(){
            ObjectId vid = ObjectId.forString("version");
            HRPlusNode childA = new HRPlusNode(new ObjectId(), new Envelope(),vid);
            HRPlusNode childB = new HRPlusNode(new ObjectId(), new Envelope(),vid);

            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNode = new HRPlusContainerNode();
            HRPlusContainerNode subSubNode = new HRPlusContainerNode();

            node.addNode(childA);
            childA.setChild(subNode);
            subNode.addNode(childB);
            childB.setChild(subSubNode);

            assertFalse(node.isOneStepAboveLeafLevel());
        }

    @Test
        public void testGetMBREmptyContainer(){
            HRPlusContainerNode node = new HRPlusContainerNode();

            assertEquals(new Envelope(), node.getMBR());
        }

    @Test
        public void testGetMBRNonEmptyContainer(){
            // Container with one node should have MBR identical to that node's envelope.
            ObjectId vid = ObjectId.forString("version");
            Envelope env = new Envelope(-10,10,-10,10);
            HRPlusNode childA = new HRPlusNode(new ObjectId(), env,vid);
            HRPlusContainerNode node = new HRPlusContainerNode();

            node.addNode(childA);

            assertEquals(env, node.getMBR());
        }

    @Test
        public void testGetMBRManyNodesDisjointEnvelopes(){
            // MBR of container should surround all disjoint envelopes
            ObjectId vid = ObjectId.forString("version");

            ObjectId idA = ObjectId.forString("a");
            ObjectId idB = ObjectId.forString("b");
            ObjectId idC = ObjectId.forString("c");
            ObjectId idD = ObjectId.forString("d");
            ObjectId idE = ObjectId.forString("e");
            ObjectId idF = ObjectId.forString("f");
            ObjectId idG = ObjectId.forString("g");
            ObjectId idH = ObjectId.forString("h");
            ObjectId idI = ObjectId.forString("i");
            ObjectId idJ = ObjectId.forString("j");
            // Create many nodes
            HRPlusNode childA = new HRPlusNode(idA, new Envelope(-10,-9,0,1),vid);
            HRPlusNode childB = new HRPlusNode(idB, new Envelope(-9,-8,1,2),vid);
            HRPlusNode childC = new HRPlusNode(idC, new Envelope(-8,-7,2,3),vid);
            HRPlusNode childD = new HRPlusNode(idD, new Envelope(-7,-6,3,4),vid);
            HRPlusNode childE = new HRPlusNode(idE, new Envelope(-6,-5,4,5),vid);
            HRPlusNode childF = new HRPlusNode(idF, new Envelope(-5,-4,5,6),vid);
            HRPlusNode childG = new HRPlusNode(idG, new Envelope(-4,-3,6,7),vid);
            HRPlusNode childH = new HRPlusNode(idH, new Envelope(-3,-2,7,8),vid);
            HRPlusNode childI = new HRPlusNode(idI, new Envelope(-2,-1,8,9),vid);
            HRPlusNode childJ = new HRPlusNode(idJ, new Envelope(-1,0,9,10),vid);
            // Create a container, add nodes
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(childA); node.addNode(childB);
            node.addNode(childC); node.addNode(childD);
            node.addNode(childE); node.addNode(childF);
            node.addNode(childG); node.addNode(childH);
            node.addNode(childI); node.addNode(childJ);

            assertEquals(new Envelope(-10,0,0,10), node.getMBR());
        }

    @Test
        public void testGetMBRManyNodesOverlapEnvelopes(){
            // Overlap should not change behavior. MBR should surround all nodes' envelopes.
            ObjectId vid = ObjectId.forString("version");

            ObjectId idA = ObjectId.forString("a");
            ObjectId idB = ObjectId.forString("b");
            ObjectId idC = ObjectId.forString("c");
            ObjectId idD = ObjectId.forString("d");
            ObjectId idE = ObjectId.forString("e");
            ObjectId idF = ObjectId.forString("f");
            ObjectId idG = ObjectId.forString("g");
            ObjectId idH = ObjectId.forString("h");
            ObjectId idI = ObjectId.forString("i");
            ObjectId idJ = ObjectId.forString("j");
            // Keep same bounds as overlap test, different envelopes inside
            HRPlusNode childA = new HRPlusNode(idA, new Envelope(-10,0,0,1),vid);
            HRPlusNode childB = new HRPlusNode(idB, new Envelope(-9,-7,0,10),vid);
            HRPlusNode childC = new HRPlusNode(idC, new Envelope(-8,-6,0,10),vid);
            HRPlusNode childD = new HRPlusNode(idD, new Envelope(-7,-5,0,10),vid);
            HRPlusNode childE = new HRPlusNode(idE, new Envelope(-4,-2,0,10),vid);
            HRPlusNode childF = new HRPlusNode(idF, new Envelope(-3,-1,0,10),vid);
            HRPlusNode childG = new HRPlusNode(idG, new Envelope(-2,0,0,10),vid);
            HRPlusNode childH = new HRPlusNode(idH, new Envelope(-5,-2,5,9),vid);
            HRPlusNode childI = new HRPlusNode(idI, new Envelope(-8,-4,3,6),vid);
            HRPlusNode childJ = new HRPlusNode(idJ, new Envelope(-1,0,0,1),vid);
            // Create a container, add nodes
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(childA); node.addNode(childB);
            node.addNode(childC); node.addNode(childD);
            node.addNode(childE); node.addNode(childF);
            node.addNode(childG); node.addNode(childH);
            node.addNode(childI); node.addNode(childJ);

            assertEquals(new Envelope(-10,0,0,10), node.getMBR());
        }

    @Test
        public void testGetMBRSubContainer(){
            // Nodes within sub-container do not affect MBR.
            ObjectId vid = ObjectId.forString("version");

            ObjectId idA = ObjectId.forString("idA");

            Envelope envA = new Envelope(5,6,5,6);
            Envelope envB = new Envelope(-5,-6,-5,-6);

            HRPlusNode childA = new HRPlusNode(idA, envA,vid);
            HRPlusNode childB = new HRPlusNode(idA, envB,vid);

            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNode = new HRPlusContainerNode();
            node.addNode(childA);
            subNode.addNode(childB);
            childA.setChild(subNode);
            // Sub container is ignored
            assertEquals(envA, node.getMBR());
        }

    @Test
        public void testGetOverlapEmptyContainer(){
            HRPlusContainerNode node = new HRPlusContainerNode();

            Envelope env1 = new Envelope();
            Envelope env2 = new Envelope(-9000, 9000, -9000, 9000);

            assertEquals(env1, node.getOverlap(env1));
            assertEquals(env1, node.getOverlap(env2));
        }    

    @Test
        public void testGetOverlapNonEmptyContainerPassNodeOverlapped(){
            ObjectId vid = ObjectId.forString("asecondversion");

            Envelope envA = new Envelope(0,1,0,1);
            HRPlusNode child = new HRPlusNode(new ObjectId(), envA,vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            Envelope envB = new Envelope(-9000, 9000, -9000, 9000);

            assertEquals(envA, node.getOverlap(envB));
        }      

    @Test
        public void testGetOverlapNonEmptyContainerPassEnvOverlapped(){
            ObjectId vid = ObjectId.forString("asecondversion");

            Envelope envA = new Envelope(-9000, 9000, -9000, 9000);
            HRPlusNode child = new HRPlusNode(new ObjectId(), envA,vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            Envelope envB = new Envelope(0,1,0,1);

            assertEquals(envB, node.getOverlap(envB));
        }

    @Test
        public void testGetOverlapNonEmptyContainerFail(){
            ObjectId vid = ObjectId.forString("asecondversion");

            Envelope envA = new Envelope(0,1,0,1);
            HRPlusNode child = new HRPlusNode(new ObjectId(), envA,vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            Envelope envC = new Envelope(0,1,-2,-1);
            assertEquals(new Envelope(), node.getOverlap(envC));
        }

    public void testGetOverlapSubContainer(){
        ObjectId vid = ObjectId.forString("asecondversion");

        ObjectId idA = ObjectId.forString("idA");

        Envelope envA = new Envelope(5,6,5,6);
        Envelope envB = new Envelope(-5,-6,-5,-6);

        HRPlusNode childA = new HRPlusNode(idA, envA,vid);
        HRPlusNode childB = new HRPlusNode(idA, envB,vid);

        HRPlusContainerNode node = new HRPlusContainerNode();
        HRPlusContainerNode subNode = new HRPlusContainerNode();
        node.addNode(childA);
        subNode.addNode(childB);
        childA.setChild(subNode);
        // Sub container is ignored
        assertEquals(new Envelope(), node.getOverlap(envB));
        assertEquals(envA, node.getOverlap(new Envelope(-100,100,-100,100)));
    }

    @Test
        public void testQueryEmptyContainer(){
            HRPlusContainerNode node = new HRPlusContainerNode();

            List<HRPlusNode> matches = new ArrayList<HRPlusNode>();

            node.query(new Envelope(), matches);
            assertEquals(0, matches.size());
            node.query(new Envelope(-9000,9000,-9000,9000), matches);
            assertEquals(0, matches.size());
        }

    @Test
        public void testQueryNonEmptyContainerPassFullOverlap(){
            ObjectId vid = ObjectId.forString("asecondversion");

            Envelope envA = new Envelope(5,34,-432,-20);
            HRPlusNode child = new HRPlusNode(new ObjectId(), envA,vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            List<HRPlusNode> matches = new ArrayList<HRPlusNode>();

            node.query(new Envelope(-9000, 9000, -9000, 9000), matches);
            assertEquals(1, matches.size());
            assertEquals(child, matches.get(0));
        }

    @Test
        public void testQueryNonEmptyContainerFailPartialOverlap(){
            ObjectId vid = ObjectId.forString("asecondversion");

            Envelope envA = new Envelope(-9000, 0, -9000, 0);
            HRPlusNode child = new HRPlusNode(new ObjectId(), envA,vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            List<HRPlusNode> matches = new ArrayList<HRPlusNode>();

            node.query(new Envelope(0, 9000, 0, 9000), matches);
            // Query fails unless node is completely contained
            assertEquals(0, matches.size());
        }

    @Test
        public void testQueryNonEmptyContainerFail(){
            ObjectId vid = ObjectId.forString("asecondversion");
            Envelope envA = new Envelope(0,1,0,1);
            HRPlusNode child = new HRPlusNode(new ObjectId(), envA,vid);
            HRPlusContainerNode node = new HRPlusContainerNode();
            node.addNode(child);

            List<HRPlusNode> matches = new ArrayList<HRPlusNode>();

            node.query(new Envelope(-10,-9,-10,-9), matches);
            assertEquals(0, matches.size());
        }

    @Test
        public void testQueryManyNodes(){
            ObjectId vid = ObjectId.forString("asecondversion");
            // Create many unique object ids
            ObjectId idA = ObjectId.forString("a");
            ObjectId idB = ObjectId.forString("b");
            ObjectId idC = ObjectId.forString("c");
            ObjectId idD = ObjectId.forString("d");
            ObjectId idE = ObjectId.forString("e");
            ObjectId idF = ObjectId.forString("f");
            ObjectId idG = ObjectId.forString("g");
            ObjectId idH = ObjectId.forString("h");
            ObjectId idI = ObjectId.forString("i");
            ObjectId idJ = ObjectId.forString("j");
            // Create many nodes
            HRPlusNode childA = new HRPlusNode(idA, new Envelope(10, 15, 3,4),vid);
            HRPlusNode childB = new HRPlusNode(idB, new Envelope(100,101,2000,2011),vid);
            HRPlusNode childC = new HRPlusNode(idC, new Envelope(0,1,0,1),vid);
            HRPlusNode childD = new HRPlusNode(idD, new Envelope(30,100,30,31),vid);
            HRPlusNode childE = new HRPlusNode(idE, new Envelope(42,43,5,77),vid);
            HRPlusNode childF = new HRPlusNode(idF, new Envelope(-1,-2,-1,-2),vid);
            HRPlusNode childG = new HRPlusNode(idG, new Envelope(-302,302,-10,-43),vid);
            HRPlusNode childH = new HRPlusNode(idH, new Envelope(-5,-6,-5,-6),vid);
            HRPlusNode childI = new HRPlusNode(idI, new Envelope(-5,-90,-10,10),vid);
            HRPlusNode childJ = new HRPlusNode(idJ, new Envelope(-3,-90,-3,-99),vid);
            // Create a container, add node
            HRPlusContainerNode node = new HRPlusContainerNode();

            node.addNode(childA); node.addNode(childB);
            node.addNode(childC); node.addNode(childD);
            node.addNode(childE); node.addNode(childF);
            node.addNode(childG); node.addNode(childH);
            node.addNode(childI); node.addNode(childJ);

            // Nodes in first quadrant will match
            List<HRPlusNode> matches = new ArrayList<HRPlusNode>();
            node.query(new Envelope(0, 9000,0, 9000), matches);
            assertEquals(5, matches.size());
            assertTrue(matches.contains(childA));
            assertTrue(matches.contains(childB));
            assertTrue(matches.contains(childC));
            assertTrue(matches.contains(childD));
            assertTrue(matches.contains(childE));
        }

    @Test
        public void testQuerySubContainerFail(){
            ObjectId vid = ObjectId.forString("asecondversion");

            ObjectId idA = ObjectId.forString("idA");

            Envelope envA = new Envelope(5,6,5,6);
            Envelope envB = new Envelope(-5,-6,-5,-6);

            HRPlusNode childA = new HRPlusNode(idA, envA,vid);
            HRPlusNode childB = new HRPlusNode(idA, envB,vid);

            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNode = new HRPlusContainerNode();
            node.addNode(childA);
            subNode.addNode(childB);
            childA.setChild(subNode);

            List<HRPlusNode> matches = new ArrayList<HRPlusNode>();
            // Sub container is ignored if container's envelope has no intersect with query
            node.query(envB, matches);
            assertEquals(0, matches.size());
        }

    @Test
        public void testQuerySubContainerPass(){
            ObjectId vid = ObjectId.forString("asecondversion");

            ObjectId idA = ObjectId.forString("idA");

            Envelope envA = new Envelope(5,6,5,6);
            Envelope envB = new Envelope(-5,-6,-5,-6);

            HRPlusNode childA = new HRPlusNode(idA, envA,vid);
            HRPlusNode childB = new HRPlusNode(idA, envB,vid);

            HRPlusContainerNode node = new HRPlusContainerNode();
            HRPlusContainerNode subNode = new HRPlusContainerNode();
            node.addNode(childA);
            subNode.addNode(childB);
            childA.setChild(subNode);

            List<HRPlusNode> matches = new ArrayList<HRPlusNode>();

            node.query(new Envelope(-10,10,-10,10), matches);

            // Parent node is skipped
            assertEquals(1, matches.size());
            assertEquals(childB, matches.get(0));
        }

    @Test
        public void testGetType(){
            // TODO implement that method!
            HRPlusContainerNode node = new HRPlusContainerNode();
            assertEquals(null, node.getType());
        }

    @Test
        public void testGetId(){
            // TODO implement that method!
            HRPlusContainerNode node = new HRPlusContainerNode();
            assertEquals(null, node.getId());
        }
}
