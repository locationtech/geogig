/* Copyright (c) 2014 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.vividsolutions.jts.geom.Envelope;

/**
 * TODO these tests are brittle. They depend on the values of 
 * MAX_DEGREE and MIN_DEGREE being 3 and 1, respectively, in the HRPlusTree implementation.
 * 
 */
public class HRPlusTreePartitionTest {

    double DOUBLE_EPSILON = 0.000001;

    @Test(expected=IllegalArgumentException.class)
        public void testZeroNodes() {
            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();
            // Constructor should raise Preconditions exception
            new HRPlusTreePartition(sortedNodes);
            // Should never reach this
            assertTrue(false);
        }

    @Test(expected=IllegalArgumentException.class)
        public void testOneNode() {
            Envelope envA = new Envelope(-2,2,-2,2);
            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), envA,new ObjectId());
            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();

            sortedNodes.add(nodeA);
            // Constructor should raise Preconditions exception
            new HRPlusTreePartition(sortedNodes);
            // Should never reach this
            assertTrue(false);
        }

    @Test(expected=IllegalArgumentException.class)
        public void testTooManyNodes(){
            ObjectId oid = new ObjectId();
            Envelope env = new Envelope();
            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();
            int numNodes = 0;
            // Add nodes to list until we've over-overflowed

            for (HRPlusTree referenceTree = new HRPlusTree(); numNodes < referenceTree.getMaxDegree()+2; ++numNodes){
                sortedNodes.add(new HRPlusNode(oid, env, oid));
            }
            new HRPlusTreePartition(sortedNodes);
            // Should never reach this
            assertTrue(false);
        }

    @Test
        public void testUniformNonOverlappingNodes() {
            // 4 envelopes, one per quadrant. None overlap.
            Envelope envA = new Envelope(-2,-4,-2,-4);
            Envelope envB = new Envelope(-2,-4,2,4);
            Envelope envC = new Envelope(2,4,-2,-4);
            Envelope envD = new Envelope(2,4,2,4);
            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), envA, new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(new ObjectId(), envB, new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(new ObjectId(), envC, new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(new ObjectId(), envD, new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();

            sortedNodes.add(nodeA); sortedNodes.add(nodeB);
            sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);
            // Partition should have nodeA in first group and the rest in second
            assertEquals(32, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(40, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(0, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(2, pt.getSplitPoint());
        }

    @Test
        public void testNonIntersectingLine(){
            // Nodes in a line, do not intersect
            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), new Envelope(-8, -6, -2, 2), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(new ObjectId(), new Envelope(-5, -3, -8, 4), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(new ObjectId(), new Envelope(-2, 0, -1, 0), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(new ObjectId(), new Envelope(1, 4, 0, 8), new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();
            sortedNodes.add(nodeA); sortedNodes.add(nodeB); sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(114, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(64, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(0, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(2, pt.getSplitPoint());
        }

    @Test
        public void testIntersectingLine(){
            // Nodes in a line, each intersects the predecessor and successor
            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), new Envelope(-8, -6, -2, 2), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(new ObjectId(), new Envelope(-7, -1, -8, 4), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(new ObjectId(), new Envelope(-2, 2, -1, 0), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(new ObjectId(), new Envelope(1, 4, 0, 8), new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();
            sortedNodes.add(nodeA); sortedNodes.add(nodeB); sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(144, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(66, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(4, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(3, pt.getSplitPoint());
        }

    @Test
        public void testFullyOverlappingNodes(){
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"), new Envelope(1,2,1,2), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"), new Envelope(1,2,1,2), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"), new Envelope(1,2,1,2), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"), new Envelope(1,2,1,2), new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();
            sortedNodes.add(nodeA); sortedNodes.add(nodeB); sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(2, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(8, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(1, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(1, pt.getSplitPoint());
        }

    @Test
        public void testUniformOverlappingNodes() {
            // Envelopes mirror each other across y-axis, intersecting across origin.
            Envelope envA = new Envelope(-8,2,-1,1);
            Envelope envB = new Envelope(-2,2,-1,8);
            Envelope envC = new Envelope(-2,8,-1,1);
            Envelope envD = new Envelope(-2,2,1,-8);
            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), envA, new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(new ObjectId(), envB, new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(new ObjectId(), envC, new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(new ObjectId(), envD, new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();

            sortedNodes.add(nodeA); sortedNodes.add(nodeB);
            sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(180, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(76, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(8, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(1, pt.getSplitPoint());
        }

    @Test
        public void testThreeOverlappingOneNonOverlapping(){
            // Three overlap in II quadrant, one non-overlapping in I quadrant.
            Envelope envA = new Envelope(-8,-4,4,8);
            Envelope envB = new Envelope(-11,-6,7,8);
            Envelope envC = new Envelope(-10,-9,5,8);
            Envelope envD = new Envelope(5,8,5,8);
            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), envA, new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(new ObjectId(), envB,new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(new ObjectId(), envC,new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(new ObjectId(), envD,new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();

            sortedNodes.add(nodeA); sortedNodes.add(nodeB);
            sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(37, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(34, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(0, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(3, pt.getSplitPoint());
        }

    @Test
        public void testParallelOverlapOne(){
            // Three parallel lines, one box overlaps one of the lines
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"), new Envelope(-10,-4,-10,10), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"), new Envelope(-3,3,-10,10), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"), new Envelope(4,10,-10,10), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"), new Envelope(-11,-5,8,10), new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();

            sortedNodes.add(nodeA); sortedNodes.add(nodeB);
            sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(412, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(96, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(10, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(3, pt.getSplitPoint());        
        }    

    @Test
        public void testParallelOverlapTwo(){
            // Three parallel lines, fourth box overlaps two lines
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"), new Envelope(-10,-4,-10,10), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"), new Envelope(-3,3,-10,10), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"), new Envelope(4,10,-10,10), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"), new Envelope(-11,4,8,10), new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();

            sortedNodes.add(nodeA); sortedNodes.add(nodeB);
            sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(430, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(114, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(28, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(3, pt.getSplitPoint());        
        }    

    @Test
        public void testParallelOverlapThree(){
            // Three parallel lines, fourth box overlaps three lines
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"), new Envelope(-10,-4,-10,10), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"), new Envelope(-3,3,-10,10), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"), new Envelope(4,10,-10,10), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"), new Envelope(-11,11,8,10), new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();

            sortedNodes.add(nodeA); sortedNodes.add(nodeB);
            sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(444, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(128, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(40, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(3, pt.getSplitPoint());        
        }

    @Test
        public void testPairsOfOverlappingSquares(){
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"), new Envelope(-10,-8,6,8), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"), new Envelope(-9,-7,5,7), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"), new Envelope(10,8,-6,-8), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"), new Envelope(9,7,-5,-7), new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();

            sortedNodes.add(nodeA); sortedNodes.add(nodeB);
            sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(18, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(24, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(0, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(2, pt.getSplitPoint());        
        }

    @Test
        public void testThreeOverlappingSquares(){
            // Three squares overlap, one does not
            HRPlusNode nodeA = new HRPlusNode(ObjectId.forString("A"), new Envelope(-10,-8,6,8), new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(ObjectId.forString("B"), new Envelope(-9,-7,5,7), new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(ObjectId.forString("C"), new Envelope(-8,-6,6,4), new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(ObjectId.forString("D"), new Envelope(9,7,-5,-7), new ObjectId());

            List<HRPlusNode> sortedNodes = new ArrayList<HRPlusNode>();

            sortedNodes.add(nodeA); sortedNodes.add(nodeB);
            sortedNodes.add(nodeC); sortedNodes.add(nodeD);

            HRPlusTreePartition pt = new HRPlusTreePartition(sortedNodes);

            assertEquals(20, pt.getArea(), DOUBLE_EPSILON);
            assertEquals(24, pt.getMargin(), DOUBLE_EPSILON);
            assertEquals(0, pt.getOverlap(), DOUBLE_EPSILON);
            assertEquals(3, pt.getSplitPoint());        
        }


}
