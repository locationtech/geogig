/* Copyright (c) 2014 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Class for testing the HRPlusTree. 
 * Note that all nodes in an HRPlusTree belong to the same feature type, for now.
 */
public class HRPlusTreeTest {

    @Test
        public void testGetNodes() {
            /*
             * Create a tree with 3 features (Same Version). Tests the following:
             * 1. getNodes() - Returns all nodes of the tree after insertion. Check if
             *    this contains all the nodes we actually inserted.
             * 2. Size of expected result and actual result
             */
            HRPlusTree tree = new HRPlusTree();
            ObjectId versionId1 = ObjectId.forString("Version1");
            // Feature Nodes in Version 1
            Envelope[] envelopes = new Envelope[3];
            envelopes[0] = new Envelope(-12, -10, -2, 2);
            envelopes[1] = new Envelope(-8, -6, -2, 2);
            envelopes[2] = new Envelope(-4, -2, -2, 2);
            // Creating the tree
            for (int i = 0; i < envelopes.length; i++) {
                tree.insert(envelopes[i], versionId1);
            }

            List<HRPlusNode> result = tree.getNodes(versionId1);
            HRPlusContainerNode root = tree.getRootForVersionId(versionId1);

            assertEquals(envelopes.length, result.size());
            assertEquals(envelopes.length, root.getNumNodes());
            assertEquals(envelopes.length, tree.getLeaves(versionId1).size());
        }

    @Test
        public void testGetNumRoots() {
            /*
             * Tests the no. of roots(entry-points) in the tree.
             * This should be equal to the no. of versions the nodes come from.
             */
            HRPlusTree tree = new HRPlusTree();
            // 4 feature nodes
            Envelope[] envelopes = new Envelope[4];
            envelopes[0] = new Envelope(-12, -10, -2, 2);
            envelopes[1] = new Envelope(12, -10, -2, 2);
            envelopes[2] = new Envelope(-10, 12, -2, 2);
            envelopes[3] = new Envelope(12, 10, -2, 2);
            // Create two versions
            ObjectId versionId1 = ObjectId.forString("Version1");
            ObjectId versionId2 = ObjectId.forString("Version2");
            // Creating the tree
            for (int i = 0; i < envelopes.length; i++) {
                if (i < 2) {
                    tree.insert(envelopes[i], versionId1);
                } else {
                    tree.insert(envelopes[i], versionId2);
                }
            }
            assertEquals(envelopes.length, tree.getNodes().size());
            // No of roots of HR+Tree (entry points) = No of versions
            assertEquals(2, tree.getNumRoots());
        }

    @Test
        public void testInsertNoOverflow(){
            // Insert a few nodes, do not overflow first container
            // Resulting tree should have exactly one container node
            HRPlusTree tree = new HRPlusTree();

            ObjectId versionId = ObjectId.forString("testInsertNoOverflow");
            tree.insert(new Envelope(), versionId);
            tree.insert(new Envelope(), versionId);
            tree.insert(new Envelope(), versionId);

            // Should have inserted 3 nodes
            assertEquals(3, tree.getRootForVersionId(versionId).getNumNodes());
            // Should have one container in tree. 
            assertEquals(1, tree.getContainersForRoot(versionId).size());
            // This one container should have the 3 nodes (all leaves)
            assertEquals(3, tree.getLeaves(versionId).size());
        }

    @Test
        public void testInsertOneOverflow(){
            // Add enough nodes to trigger one overflow, should have three containers at end.
            // Two containers have leaves, one container is the shared root.
            HRPlusTree tree = new HRPlusTree();

            ObjectId versionId = ObjectId.forString("testInsertOneOverflow");
            Envelope envA = new Envelope(1,2,1,2);
            Envelope envB = new Envelope(-1,-2,1,2);
            Envelope envC = new Envelope(-1,-2,-1,-2);
            Envelope envD = new Envelope(1,2,-1,-2);
            tree.insert(envA, versionId);
            tree.insert(envB, versionId);
            tree.insert(envC, versionId);
            tree.insert(envD, versionId);
            // 4 inserts should trigger a split. Split divides current root nodes into sub-containers.
            // Should have 2 nodes left in root, one pointing to each sub-container.
            assertEquals(2, tree.getRootForVersionId(versionId).getNumNodes());
            // Should have three containers in tree.
            assertEquals(3, tree.getContainersForRoot(versionId).size());
            // Should have 4 leaves, one for each of the data points we inserted.
            List<HRPlusNode> leaves = tree.getLeaves(versionId);
            assertEquals(4, leaves.size());
            // Leaves should match inserted envelopes
            boolean foundA=false, foundB=false, foundC=false, foundD=false;
            for (HRPlusNode node : leaves){
                if (!foundA){ foundA = envA.equals(node.getBounds()); }
                if (!foundB) { foundB = envB.equals(node.getBounds()); }
                if (!foundC) { foundC = envC.equals(node.getBounds()); }
                if (!foundD) { foundD = envD.equals(node.getBounds()); }
            }
            assertTrue(foundA);
            assertTrue(foundB);
            assertTrue(foundC);
            assertTrue(foundD);
        }

    @Test
        public void testInsertFiveNonOverlappingNodes(){
            // Trigger two overflows
            HRPlusTree tree = new HRPlusTree();

            ObjectId versionId = ObjectId.forString("testInsertOneOverflow");
            // For simplicity, keep nodes on a line
            Envelope envA = new Envelope(0,1,0,1);
            Envelope envB = new Envelope(2,3,0,1);
            Envelope envC = new Envelope(4,5,0,1);
            Envelope envD = new Envelope(6,7,0,1);
            Envelope envE = new Envelope(8,9,0,1);
            Envelope envF = new Envelope(10,11,0,1);
            tree.insert(envA, versionId);
            tree.insert(envB, versionId);
            tree.insert(envC, versionId);
            tree.insert(envD, versionId);
            tree.insert(envE, versionId);

            // Should split twice, first in the root to make partition [1], [2,3,4]
            // Second to split [1], [2,3], [4,5]
            assertEquals(3, tree.getRootForVersionId(versionId).getNumNodes());
            // Overall, 4 containers in the tree. Three have leaves, one is root.
            assertEquals(4, tree.getContainersForRoot(versionId).size());
            // Should have one leaf for each datum
            List<HRPlusNode> leaves = tree.getLeaves(versionId);
            assertEquals(5, leaves.size());
            boolean foundA=false, foundB=false, foundC=false, foundD=false, foundE=false, foundF=false;
            for (HRPlusNode node : leaves){
                if (!foundA) { foundA = envA.equals(node.getBounds()); }
                if (!foundB) { foundB = envB.equals(node.getBounds()); }
                if (!foundC) { foundC = envC.equals(node.getBounds()); }
                if (!foundD) { foundD = envD.equals(node.getBounds()); }
                if (!foundE) { foundE = envE.equals(node.getBounds()); }
                if (!foundF) { foundF = envF.equals(node.getBounds()); }
            }
            assertTrue(foundA);
            assertTrue(foundB);
            assertTrue(foundC);
            assertTrue(foundD);
            assertTrue(foundE);
        }       

    @Test
        public void testInsertSixNonOverlappingNodes(){
            // Trigger two overflows
            HRPlusTree tree = new HRPlusTree();

            ObjectId versionId = ObjectId.forString("testInsertOneOverflow");
            // For simplicity, keep nodes on a line
            Envelope envA = new Envelope(0,1,0,1);
            Envelope envB = new Envelope(2,3,0,1);
            Envelope envC = new Envelope(4,5,0,1);
            Envelope envD = new Envelope(6,7,0,1);
            Envelope envE = new Envelope(8,9,0,1);
            Envelope envF = new Envelope(10,11,0,1);
            tree.insert(envA, versionId);
            tree.insert(envB, versionId);
            tree.insert(envC, versionId);
            tree.insert(envD, versionId);
            tree.insert(envE, versionId);
            tree.insert(envF, versionId);

            // Should split twice, first in the root to make partition [1], [2,3,4]
            // Second to split root, result root is container with: [1], [2,3], [4,5]
            // Third will break up the root to have two dummies
            assertEquals(2, tree.getRootForVersionId(versionId).getNumNodes());
            // Overall, 4 containers in the tree. Three have leaves, one is root.
            assertEquals(7, tree.getContainersForRoot(versionId).size());
            // Should have one leaf for each datum
            List<HRPlusNode> leaves = tree.getLeaves(versionId);
            assertEquals(6, leaves.size());
            boolean foundA=false, foundB=false, foundC=false, foundD=false, foundE=false, foundF=false;
            for (HRPlusNode node : leaves){
                if (!foundA) { foundA = envA.equals(node.getBounds()); }
                if (!foundB) { foundB = envB.equals(node.getBounds()); }
                if (!foundC) { foundC = envC.equals(node.getBounds()); }
                if (!foundD) { foundD = envD.equals(node.getBounds()); }
                if (!foundE) { foundE = envE.equals(node.getBounds()); }
                if (!foundF) { foundF = envF.equals(node.getBounds()); }
            }
            assertTrue(foundA);
            assertTrue(foundB);
            assertTrue(foundC);
            assertTrue(foundD);
            assertTrue(foundE);
            assertTrue(foundF);
        }

    @Test
        public void testOverflowTwelveInserts() {
            /*
             *  Keep adding nodes belonging to the same version which are very
             *  close to each other.
             *  Check the no. of containers for the root.
             */
            HRPlusTree tree = new HRPlusTree();
            ObjectId versionId1 = ObjectId.forString("Version1");
            Envelope envelopes[] = new Envelope[12];

            envelopes[0] = new Envelope(12, 10, 4, 2);
            envelopes[1] = new Envelope(-12, -10, 4, 2);
            envelopes[2] = new Envelope(-12, -10, -4, -2);
            envelopes[3] = new Envelope(12, 10, -4, -2);

            // subsets of the above
            envelopes[4] = new Envelope(11, 9, 3, 2);
            envelopes[5] = new Envelope(-11, -9, 3, 2);
            envelopes[6] = new Envelope(-11, -9, -3, -2);
            envelopes[7] = new Envelope(11, 9, -3, -2);

            // subsets of the above
            envelopes[8] = new Envelope(10, 8, 2.5, 1.5);
            envelopes[9] = new Envelope(-10, -8, 2.5, 1.5);
            envelopes[10] = new Envelope(-10, -8, -2.5, -1.5);
            envelopes[11] = new Envelope(10, 8, -2.5, -1.5);

            // Creating the tree
            for (int i = 0; i < envelopes.length; i++) {
                tree.insert(envelopes[i], versionId1);
            }
            HRPlusContainerNode container = tree.getRootForVersionId(versionId1);

            assertEquals(2, container.getNumNodes());
            assertEquals(envelopes.length, tree.getLeaves(versionId1).size());
            assertEquals(24, tree.getNodes(versionId1).size());
        }

    @Test
        public void testQueries() {
            /*
             * Inserts certain no. of non-overlapping nodes belonging to same version.
             * Tests the following queries: 
             * 1. A large bbox query - should return all the nodes.
             * 2. Query (BBox of Node1) - should return node 1
             * 3. Query (BBox of Node2) - should return node 2
             * 4. Query (BBox of Node3) - should return node 3
             */
            HRPlusTree tree = new HRPlusTree();
            ObjectId versionId1 = ObjectId.forString("Version1");
            Envelope envelopes[] = new Envelope[3];
            envelopes[0] = new Envelope(-12, -10, -2, 2);
            envelopes[1] = new Envelope(-8, -6, -2, 2);
            envelopes[2] = new Envelope(-4, -2, -2, 2);

            // Creating the tree
            for (int i = 0; i < envelopes.length; i++) {
                tree.insert(envelopes[i], versionId1);
            }

            Envelope e = new Envelope(-50, 50, -50, 50);
            List<HRPlusNode> query1 = tree.query(e);
            List<HRPlusNode> query2 = tree.query(envelopes[0]);
            List<HRPlusNode> query3 = tree.query(envelopes[1]);
            List<HRPlusNode> query4 = tree.query(envelopes[2]);

            // Test Query1
            assertEquals(3, query1.size());
            // Test Query2
            assertTrue(envelopes[0].contains(HRPlusTreeUtils.boundingBoxOf(query2)));
            // Test Query3
            assertTrue(envelopes[1].contains(HRPlusTreeUtils.boundingBoxOf(query3)));
            // Test Query4
            assertTrue(envelopes[2].contains(HRPlusTreeUtils.boundingBoxOf(query4)));

        }

    @Test
        public void testQueries2() {
            /*
             * Inserts 4 non-overlapping nodes. One in each quadrant.
             *                    | 
             *                __  |  __           
             *               |__| | |__| 
             *            --------|---------
             *                __  |  __           
             *               |__| | |__|  
             *                      
             * Tests the following queries: 
             * 1. A large bbox query - should return all the nodes.
             * 2. Query (BBox of Node1) - should return node 1
             * 3. Query (BBox of Node2) - should return node 2
             * 4. Query (BBox of Node3) - should return node 3
             */
            HRPlusTree tree = new HRPlusTree();
            ObjectId versionId1 = ObjectId.forString("Version1");

            Envelope envelopes[] = new Envelope[4];
            envelopes[0] = new Envelope(5, 10, 5, 10);
            envelopes[1] = new Envelope(-10, -5, 5, 10);
            envelopes[2] = new Envelope(-10, -5, -5, -10);
            envelopes[3] = new Envelope(10, 5, -5, -10);

            // Creating the tree
            for (int i = 0; i < envelopes.length; i++) {
                // nodes.add(new HRPlusNode(id[i], envelopes[i], versionId1));
                tree.insert(envelopes[i], versionId1);

            }

            // large bbox query - should return all nodes
            List<HRPlusNode> query1 = tree.query(new Envelope(-10, 10, -10, 10));
            // dummy query - should return nothing
            List<HRPlusNode> query2 = tree.query(new Envelope(-4, 4, -4, 4));

            List<HRPlusNode> query3 = tree.query(new Envelope(11, 4, 4, 11));
            List<HRPlusNode> query4 = tree.query(new Envelope(-11, -4, 4, 11));
            List<HRPlusNode> query5 = tree.query(new Envelope(-11, -4, -4, -11));
            List<HRPlusNode> query6 = tree.query(new Envelope(11, 4, -4, -11));

            assertEquals(6, tree.getNodes().size());
            assertEquals(0, query2.size());
            assertTrue(envelopes[0].contains(HRPlusTreeUtils.boundingBoxOf(query3))
                    && query3.size() == 1);
            assertTrue(envelopes[1].contains(HRPlusTreeUtils.boundingBoxOf(query4))
                    && query4.size() == 1);
            assertTrue(envelopes[2].contains(HRPlusTreeUtils.boundingBoxOf(query5))
                    && query5.size() == 1);
            assertTrue(envelopes[3].contains(HRPlusTreeUtils.boundingBoxOf(query6))
                    && query6.size() == 1);

        }

    @Test
        public void testOverflow16nodes() {
            /*
             * Inserts 16 non-overlapping nodes, 4 in each quadrant in the
             * following order
             *                              | 
             *                __    __      |  __    __     
             *               |_8|  |_6|     | |2_|  |4_|
             *                __    __      |  __    __     
             *               |_7|  |_5|     | |1_|  |3_|
             *                              |
             *      ----------------------- |------------------
             *                __    __      |  __    __     
             *               |11|  |_9|     | |13|  |15|
             *                __    __      |  __    __     
             *               |12|  |10|     | |14|  |16|
             *                      
             *
             */
            HRPlusTree tree = new HRPlusTree();
            ObjectId versionId = ObjectId.forString("Version1");

            Envelope[] envelopes = new Envelope[16];
            // Quadrant 1
            envelopes[0] = new Envelope(2, 4, 2, 4);
            envelopes[1] = new Envelope(2, 4, 6, 8);
            envelopes[2] = new Envelope(6, 8, 2, 4);
            envelopes[3] = new Envelope(6, 8, 6, 8);
            // Quadrant 2
            envelopes[4] = new Envelope(-2, -4, 2, 4);
            envelopes[5] = new Envelope(-2, -4, 6, 8);
            envelopes[6] = new Envelope(-6, -8, 2, 4);
            envelopes[7] = new Envelope(-6, -8, 6, 8);
            // Quadrant 3
            envelopes[8] = new Envelope(-2, -4, -2, -4);
            envelopes[9] = new Envelope(-2, -4, -6, -8);
            envelopes[10] = new Envelope(-6, -8, -2, -4);
            envelopes[11] = new Envelope(-6, -8, -6, -8);
            // Quadrant 4
            envelopes[12] = new Envelope(2, 4, -2, -4);
            envelopes[13] = new Envelope(2, 4, -6, -8);
            envelopes[14] = new Envelope(6, 8, -2, -4);
            envelopes[15] = new Envelope(6, 8, -6, -8);

            ObjectId id[] = new ObjectId[16];
            // list of nodes
            List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();

            // Creating the tree
            for (int i = 0; i < 16; i++) {
                id[i] = ObjectId.forString("building" + i);
                nodes.add(new HRPlusNode(id[i], envelopes[i], versionId));
                tree.insert(envelopes[i], versionId);
            }

            //No of leaves = No of feature nodes added
            assertEquals(envelopes.length, tree.getLeaves(versionId).size());
        }

    @Test(expected = IllegalArgumentException.class)
        public void testKeySplitContainerNodeNull() {
            HRPlusTree hr = new HRPlusTree();
            hr.keySplitContainerNode(null);
        }

    @Test(expected = IllegalArgumentException.class)
        public void testKeySplitContainerNodeTooFewNodes() {
            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), new Envelope(0, 1, 0, 1), new ObjectId());
            HRPlusContainerNode cont = new HRPlusContainerNode();
            cont.addNode(nodeA);

            HRPlusTree hr = new HRPlusTree();
            hr.keySplitContainerNode(cont);
        }

    @Test(expected = IllegalArgumentException.class)
        public void testKeySplitContainerNodeTooManyNodes() {
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"),
                    new Envelope(0, 1, 0, 1), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"),
                    new Envelope(0, 1, 0, 1), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"),
                    new Envelope(0, 1, 0, 1), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"),
                    new Envelope(0, 1, 0, 1), new ObjectId());
            HRPlusNode nodeE = new HRPlusNode(ObjectId.forString("E"),
                    new Envelope(0, 1, 0, 1), new ObjectId());

            HRPlusContainerNode cont = new HRPlusContainerNode();
            cont.addNode(nodeA);
            cont.addNode(nodeB);
            cont.addNode(nodeC);
            cont.addNode(nodeD);
            cont.addNode(nodeE);

            HRPlusTree hr = new HRPlusTree();
            hr.keySplitContainerNode(cont);
        }

    @Test
        public void testKeySplitContainerNodeNonIntersectingSquare() {
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"),
                    new Envelope(1, 2, 1, 2), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"),
                    new Envelope(-1, -2, 1, 2), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"),
                    new Envelope(-1, -2, -1, -2), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"),
                    new Envelope(1, 2, -1, -2), new ObjectId());

            HRPlusContainerNode contA = new HRPlusContainerNode();
            contA.addNode(nodeA);
            contA.addNode(nodeB);
            contA.addNode(nodeC);
            contA.addNode(nodeD);

            HRPlusTree hr = new HRPlusTree();
            HRPlusContainerNode contB = hr.keySplitContainerNode(contA);

            assertEquals(2, contA.getNumNodes());
            assertEquals(2, contB.getNumNodes());
            assertEquals(new Envelope(1, 2, -2, 2), contA.getMBR());
            assertEquals(new Envelope(-1, -2, -2, 2), contB.getMBR());
        }

    @Test
        public void testKeySplitContainerNodeNonIntersectingLine() {
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"),
                    new Envelope(1, 2, 1, 2), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"),
                    new Envelope(3, 4, 1, 2), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"),
                    new Envelope(5, 6, 1, 2), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"),
                    new Envelope(7, 8, 1, 2), new ObjectId());

            HRPlusContainerNode contA = new HRPlusContainerNode();
            contA.addNode(nodeA);
            contA.addNode(nodeB);
            contA.addNode(nodeC);
            contA.addNode(nodeD);

            HRPlusTree hr = new HRPlusTree();
            HRPlusContainerNode contB = hr.keySplitContainerNode(contA);

            assertEquals(3, contA.getNumNodes());
            assertEquals(1, contB.getNumNodes());
            assertEquals(new Envelope(3, 8, 1, 2), contA.getMBR());
            assertEquals(new Envelope(1, 2, 1, 2), contB.getMBR());
        }

    @Test
        public void testKeySplitContainerNodeFullyOverlappingSquare() {
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"),
                    new Envelope(1, 2, 1, 2), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"),
                    new Envelope(1, 2, 1, 2), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"),
                    new Envelope(1, 2, 1, 2), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"),
                    new Envelope(1, 2, 1, 2), new ObjectId());

            HRPlusContainerNode contA = new HRPlusContainerNode();
            contA.addNode(nodeA);
            contA.addNode(nodeB);
            contA.addNode(nodeC);
            contA.addNode(nodeD);

            HRPlusTree hr = new HRPlusTree();
            HRPlusContainerNode contB = hr.keySplitContainerNode(contA);

            assertEquals(3, contA.getNumNodes());
            assertEquals(1, contB.getNumNodes());
            assertEquals(new Envelope(1, 2, 1, 2), contA.getMBR());
            assertEquals(new Envelope(1, 2, 1, 2), contB.getMBR());
        }

    @Test
        public void testKeySplitContainerNodeParallelOverlapOne() {
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"),
                    new Envelope(-10, -4, -10, 10), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"),
                    new Envelope(-3, 3, -10, 10), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"),
                    new Envelope(4, 10, -10, 10), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"),
                    new Envelope(-11, -5, 8, 10), new ObjectId());

            HRPlusContainerNode contA = new HRPlusContainerNode();
            contA.addNode(nodeA);
            contA.addNode(nodeB);
            contA.addNode(nodeC);
            contA.addNode(nodeD);

            HRPlusTree hr = new HRPlusTree();
            HRPlusContainerNode contB = hr.keySplitContainerNode(contA);

            assertEquals(2, contA.getNumNodes());
            assertEquals(2, contB.getNumNodes());
            assertEquals(new Envelope(-3, 10, -10, 10), contA.getMBR());
            assertEquals(new Envelope(-11, -4, -10, 10), contB.getMBR());
        }

    @Test
        public void testKeySplitContainerNodeParallelOverlapTwo() {
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"),
                    new Envelope(-10, -4, -10, 10), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"),
                    new Envelope(-3, 3, -10, 10), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"),
                    new Envelope(4, 10, -10, 10), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"),
                    new Envelope(-11, 4, 8, 10), new ObjectId());

            HRPlusContainerNode contA = new HRPlusContainerNode();
            contA.addNode(nodeA);
            contA.addNode(nodeB);
            contA.addNode(nodeC);
            contA.addNode(nodeD);

            HRPlusTree hr = new HRPlusTree();
            HRPlusContainerNode contB = hr.keySplitContainerNode(contA);

            assertEquals(1, contA.getNumNodes());
            assertEquals(3, contB.getNumNodes());
            assertEquals(new Envelope(4, 10, -10, 10), contA.getMBR());
            assertEquals(new Envelope(-11, 4, -10, 10), contB.getMBR());
        }

    @Test
        public void testKeySplitContainerNodeParallelOverlapThree() {
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"),
                    new Envelope(-10, -4, -10, 10), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"),
                    new Envelope(-3, 3, -10, 10), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"),
                    new Envelope(4, 10, -10, 10), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"),
                    new Envelope(-11, 11, 8, 10), new ObjectId());

            HRPlusContainerNode contA = new HRPlusContainerNode();
            contA.addNode(nodeA);
            contA.addNode(nodeB);
            contA.addNode(nodeC);
            contA.addNode(nodeD);

            HRPlusTree hr = new HRPlusTree();
            HRPlusContainerNode contB = hr.keySplitContainerNode(contA);

            assertEquals(3, contA.getNumNodes());
            assertEquals(1, contB.getNumNodes());
            assertEquals(new Envelope(-10, 10, -10, 10), contA.getMBR());
            assertEquals(new Envelope(-11, 11, 8, 10), contB.getMBR());
        }

    @Test
        public void testKeySplitContainerNodePairsOfIntersectingSquares() {
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"),
                    new Envelope(-10, -8, 6, 8), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"),
                    new Envelope(-9, -7, 5, 7), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"),
                    new Envelope(10, 8, -6, -8), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"),
                    new Envelope(9, 7, -5, -7), new ObjectId());

            HRPlusContainerNode contA = new HRPlusContainerNode();
            contA.addNode(nodeA);
            contA.addNode(nodeB);
            contA.addNode(nodeC);
            contA.addNode(nodeD);

            HRPlusTree hr = new HRPlusTree();
            HRPlusContainerNode contB = hr.keySplitContainerNode(contA);

            assertEquals(2, contA.getNumNodes());
            assertEquals(2, contB.getNumNodes());
            assertEquals(new Envelope(7, 10, -5, -8), contA.getMBR());
            assertEquals(new Envelope(-10, -7, 5, 8), contB.getMBR());
        }
}
