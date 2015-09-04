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

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Class for testing the utility methods in HRPLusTreeUtils
 */
public class HRPlusTreeUtilsTest {

    double DOUBLE_EPSILON = 0.000001;

    private List<HRPlusNode> getInputNodePoints() {
        /* Returns a list of HRPlusNodes, each representing a point in 2D. */
        List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();
        Envelope e1 = new Envelope(new Coordinate(-5, 4));
        Envelope e2 = new Envelope(new Coordinate(-2, -2));
        Envelope e3 = new Envelope(new Coordinate(1, 1));
        Envelope e4 = new Envelope(new Coordinate(3, 7));
        Envelope e5 = new Envelope(new Coordinate(2.5, -4));
        Envelope e6 = new Envelope(new Coordinate(5, 4));
        Envelope e7 = new Envelope(new Coordinate(7, -4));

        HRPlusNode node1 = new HRPlusNode(new ObjectId(), e1, new ObjectId());
        HRPlusNode node2 = new HRPlusNode(new ObjectId(), e2, new ObjectId());
        HRPlusNode node3 = new HRPlusNode(new ObjectId(), e3, new ObjectId());
        HRPlusNode node4 = new HRPlusNode(new ObjectId(), e4, new ObjectId());
        HRPlusNode node5 = new HRPlusNode(new ObjectId(), e5, new ObjectId());
        HRPlusNode node6 = new HRPlusNode(new ObjectId(), e6, new ObjectId());
        HRPlusNode node7 = new HRPlusNode(new ObjectId(), e7, new ObjectId());

        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);
        nodes.add(node4);
        nodes.add(node5);
        nodes.add(node6);
        nodes.add(node7);

        return nodes;
    }

    private List<HRPlusNode> getInputNodeEnvelopes() {
        /* Returns a list of HRPlusNodes, each having a bounding box. */
        List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();
        Envelope e1 = new Envelope(-10, -8, 7.7, 9);
        Envelope e2 = new Envelope(-9, -2, -9, -3);
        Envelope e3 = new Envelope(-3, -1, -4, 0.7);
        Envelope e4 = new Envelope(-0.5, 6, 0.5, 11);
        Envelope e5 = new Envelope(9, 12, -12, -11);
        Envelope e6 = new Envelope(13, 15, -19, -13);
        HRPlusNode node1 = new HRPlusNode(new ObjectId(), e1, new ObjectId());
        HRPlusNode node2 = new HRPlusNode(new ObjectId(), e2, new ObjectId());
        HRPlusNode node3 = new HRPlusNode(new ObjectId(), e3, new ObjectId());
        HRPlusNode node4 = new HRPlusNode(new ObjectId(), e4, new ObjectId());
        HRPlusNode node5 = new HRPlusNode(new ObjectId(), e5, new ObjectId());
        HRPlusNode node6 = new HRPlusNode(new ObjectId(), e6, new ObjectId());

        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);
        nodes.add(node4);
        nodes.add(node5);
        nodes.add(node6);
        return nodes;
    }

    @Test
        public void testGetOverlap() {
            List<HRPlusNode> nodes = getInputNodeEnvelopes();

            List<HRPlusNode> group1 = new ArrayList<HRPlusNode>();
            List<HRPlusNode> group2 = new ArrayList<HRPlusNode>();
            List<HRPlusNode> group3 = new ArrayList<HRPlusNode>();
            List<HRPlusNode> group4 = new ArrayList<HRPlusNode>();
            List<HRPlusNode> group5 = new ArrayList<HRPlusNode>();

            // case where one bbox is completely inside another
            group1.add(nodes.get(1));
            group1.add(nodes.get(0));
            group1.add(nodes.get(3));
            group2.add(nodes.get(2));
            assertEquals(9.4, HRPlusTreeUtils.getOverlap(group1, group2),
                    DOUBLE_EPSILON);

            // case where no intersection
            group3.add(nodes.get(4));
            group3.add(nodes.get(5));
            assertEquals(0, HRPlusTreeUtils.getOverlap(group1, group3),
                    DOUBLE_EPSILON);

            // case when intersection
            group4.add(nodes.get(0));
            group4.add(nodes.get(1));
            group5.add(nodes.get(2));
            assertEquals(4.7, HRPlusTreeUtils.getOverlap(group4, group5),
                    DOUBLE_EPSILON);

        }

    @Test
        public void testMaxXSortPoints() {

            List<HRPlusNode> nodes = getInputNodePoints();
            List<HRPlusNode> expected = new ArrayList<HRPlusNode>();

            expected.add(nodes.get(0));
            expected.add(nodes.get(1));
            expected.add(nodes.get(2));
            expected.add(nodes.get(4));
            expected.add(nodes.get(3));
            expected.add(nodes.get(5));
            expected.add(nodes.get(6));

            assertEquals(expected, HRPlusTreeUtils.maxXSort(nodes));

        }

    @Test
        public void testMaxXSortEnvelopes() {

            List<HRPlusNode> nodes = getInputNodeEnvelopes();
            List<HRPlusNode> expected = new ArrayList<HRPlusNode>();

            expected.add(nodes.get(0));
            expected.add(nodes.get(1));
            expected.add(nodes.get(2));
            expected.add(nodes.get(3));
            expected.add(nodes.get(4));
            expected.add(nodes.get(5));

            assertEquals(expected, HRPlusTreeUtils.maxXSort(nodes));
        }

    @Test
        public void testMaxYSortPoints() {
            List<HRPlusNode> nodes = getInputNodePoints();
            List<HRPlusNode> expected = new ArrayList<HRPlusNode>();

            expected.add(nodes.get(4));
            expected.add(nodes.get(6));
            expected.add(nodes.get(1));
            expected.add(nodes.get(2));
            expected.add(nodes.get(0));
            expected.add(nodes.get(5));
            expected.add(nodes.get(3));

            assertEquals(expected, HRPlusTreeUtils.maxYSort(nodes));
        }

    @Test
        public void testMaxYSortEnvelopes() {

            List<HRPlusNode> nodes = getInputNodeEnvelopes();
            List<HRPlusNode> expected = new ArrayList<HRPlusNode>();

            expected.add(nodes.get(5));
            expected.add(nodes.get(4));
            expected.add(nodes.get(1));
            expected.add(nodes.get(2));
            expected.add(nodes.get(0));
            expected.add(nodes.get(3));

            assertEquals(expected, HRPlusTreeUtils.maxYSort(nodes));
        }

    @Test
        public void testMinXSortPoints() {
            List<HRPlusNode> nodes = getInputNodePoints();
            List<HRPlusNode> expected = new ArrayList<HRPlusNode>();

            expected.add(nodes.get(0));
            expected.add(nodes.get(1));
            expected.add(nodes.get(2));
            expected.add(nodes.get(4));
            expected.add(nodes.get(3));
            expected.add(nodes.get(5));
            expected.add(nodes.get(6));

            assertEquals(expected, HRPlusTreeUtils.minXSort(nodes));
        }

    @Test
        public void testMinXSortEnvelopes() {

            List<HRPlusNode> nodes = getInputNodeEnvelopes();
            List<HRPlusNode> expected = new ArrayList<HRPlusNode>();

            expected.add(nodes.get(0));
            expected.add(nodes.get(1));
            expected.add(nodes.get(2));
            expected.add(nodes.get(3));
            expected.add(nodes.get(4));
            expected.add(nodes.get(5));

            assertEquals(expected, HRPlusTreeUtils.minXSort(nodes));
        }

    @Test
        public void testMinYSortPoints() {
            List<HRPlusNode> nodes = getInputNodePoints();
            List<HRPlusNode> expected = new ArrayList<HRPlusNode>();

            expected.add(nodes.get(4));
            expected.add(nodes.get(6));
            expected.add(nodes.get(1));
            expected.add(nodes.get(2));
            expected.add(nodes.get(0));
            expected.add(nodes.get(5));
            expected.add(nodes.get(3));

            assertEquals(expected, HRPlusTreeUtils.minYSort(nodes));
        }

    @Test
        public void testMinYSortEnvelopes() {

            List<HRPlusNode> nodes = getInputNodeEnvelopes();
            List<HRPlusNode> expected = new ArrayList<HRPlusNode>();

            expected.add(nodes.get(5));
            expected.add(nodes.get(4));
            expected.add(nodes.get(1));
            expected.add(nodes.get(2));
            expected.add(nodes.get(3));
            expected.add(nodes.get(0));

            assertEquals(expected, HRPlusTreeUtils.minYSort(nodes));
        }

    @Test
        public void testBoundingBox() {
            List<HRPlusNode> nodes = getInputNodeEnvelopes();
            Envelope expected = new Envelope(15, -10, 11, -19);
            assertEquals(expected, HRPlusTreeUtils.boundingBoxOf(nodes));
        }

    @Test
        public void testGetTotalAreaOfTwoRegions() {
            List<HRPlusNode> nodes = getInputNodeEnvelopes();
            List<HRPlusNode> group1 = new ArrayList<HRPlusNode>();
            List<HRPlusNode> group2 = new ArrayList<HRPlusNode>();

            // Area is just
            group1.add(nodes.get(0));
            group1.add(nodes.get(1));
            group1.add(nodes.get(2));
            group2.add(nodes.get(3));
            double g1Area = 162;
            double g2Area = 68.25;

            assertEquals(g1Area + g2Area,
                    HRPlusTreeUtils.getTotalAreaOfTwoRegions(group1, group2),
                    DOUBLE_EPSILON);
        }

    @Test
        public void testGetTotalMarginOfTwoRegionsEmptyLists() {
            List<HRPlusNode> group1 = new ArrayList<HRPlusNode>();
            List<HRPlusNode> group2 = new ArrayList<HRPlusNode>();
            assertEquals(0,
                    HRPlusTreeUtils.getTotalMarginOfTwoRegions(group1, group2),
                    DOUBLE_EPSILON);
        }

    @Test
        public void testGetTotalMarginOfTwoRegionsSingletonNonOverlappingLists() {
            Envelope envA = new Envelope(4, 5, 4, 5);
            Envelope envB = new Envelope(1, 2, 1, 2);
            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), envA, new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(new ObjectId(), envB, new ObjectId());
            List<HRPlusNode> group1 = new ArrayList<HRPlusNode>();
            List<HRPlusNode> group2 = new ArrayList<HRPlusNode>();

            group1.add(nodeA);
            group2.add(nodeB);

            assertEquals(8,
                    HRPlusTreeUtils.getTotalMarginOfTwoRegions(group1, group2),
                    DOUBLE_EPSILON);
        }

    @Test
        public void testGetTotalMarginOfTwoRegionsSingletonOverlappingLists() {
            Envelope envA = new Envelope(4, 5, 4, 5);
            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), envA, new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(new ObjectId(), envA, new ObjectId());
            List<HRPlusNode> group1 = new ArrayList<HRPlusNode>();
            List<HRPlusNode> group2 = new ArrayList<HRPlusNode>();

            group1.add(nodeA);
            group2.add(nodeB);

            assertEquals(8,
                    HRPlusTreeUtils.getTotalMarginOfTwoRegions(group1, group2),
                    DOUBLE_EPSILON);
        }

    @Test
        public void testGetTotalMarginOfTwoRegionsAssorted() {
            // Create some overlapping envelopes
            Envelope envA = new Envelope(10, 20, 30, 40);
            Envelope envB = new Envelope(-5, 30, -10, -20);
            Envelope envC = new Envelope(8, 16, -44, 100);
            Envelope envD = new Envelope(-5, 5, -5, 5);
            Envelope envE = new Envelope(-90, 110, 50, 80);

            HRPlusNode nodeA = new HRPlusNode(new ObjectId(), envA, new ObjectId());
            HRPlusNode nodeB = new HRPlusNode(new ObjectId(), envB, new ObjectId());
            HRPlusNode nodeC = new HRPlusNode(new ObjectId(), envC, new ObjectId());
            HRPlusNode nodeD = new HRPlusNode(new ObjectId(), envD, new ObjectId());
            HRPlusNode nodeE = new HRPlusNode(new ObjectId(), envE, new ObjectId());

            List<HRPlusNode> group1 = new ArrayList<HRPlusNode>();
            List<HRPlusNode> group2 = new ArrayList<HRPlusNode>();

            // Get a few results.
            double val1, val2, val3, val4, val5;
            // First, all nodes in one group
            // group1.add(nodeA);
            group1.add(nodeB);
            group1.add(nodeC);
            group1.add(nodeD);
            group1.add(nodeE);
            val1 = HRPlusTreeUtils.getTotalMarginOfTwoRegions(group1, group2);
            group1.clear();
            // Next, mix across groups
            group1.add(nodeA);
            group1.add(nodeB);
            group2.add(nodeC);
            group2.add(nodeD);
            group2.add(nodeE);
            val2 = HRPlusTreeUtils.getTotalMarginOfTwoRegions(group1, group2);
            group1.clear();
            group2.clear();
            // Ditto, a 'random' mix
            group1.add(nodeA);
            group1.add(nodeD);
            group2.add(nodeC);
            group2.add(nodeB);
            val3 = HRPlusTreeUtils.getTotalMarginOfTwoRegions(group1, group2);
            group1.clear();
            group2.clear();
            // Something with only one env in each group
            group1.add(nodeC);
            group2.add(nodeD);
            val4 = HRPlusTreeUtils.getTotalMarginOfTwoRegions(group1, group2);
            group1.clear();
            group2.clear();
            // Same thing in both groups
            group1.add(nodeA);
            group2.add(nodeA);
            val5 = HRPlusTreeUtils.getTotalMarginOfTwoRegions(group1, group2);
            group1.clear();
            group2.clear();
            // Asserts!
            assertEquals(688, val1, DOUBLE_EPSILON);
            assertEquals(878, val2, DOUBLE_EPSILON);
            assertEquals(140 + 358, val3, DOUBLE_EPSILON);
            assertEquals(344, val4, DOUBLE_EPSILON);
            assertEquals(40 + 40, val5, DOUBLE_EPSILON);
        }

    @Test
        public void testGetMargin() {
            Envelope a = new Envelope(-10, -8, 7.7, 9);
            assertEquals(6.6, HRPlusTreeUtils.marginOf(a), DOUBLE_EPSILON);
        }

    @Test
        public void testSumOfMarginsNoOverlap() {
            Envelope a = new Envelope(-10, -2, 2, 10);
            Envelope b = new Envelope(-10, -2, -2, -10);
            Envelope c = new Envelope(2, 10, 2, 10);

            List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();

            nodes.add(new HRPlusNode(new ObjectId(), a, new ObjectId()));
            nodes.add(new HRPlusNode(new ObjectId(), b, new ObjectId()));
            nodes.add(new HRPlusNode(new ObjectId(), c, new ObjectId()));

            assertEquals(200, HRPlusTreeUtils.sumOfMargins(nodes), DOUBLE_EPSILON);
        }

    @Test
        public void testSumOfMarginsOverlap() {

            List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();
            Envelope a = new Envelope(-5, 0, 0, 5);
            Envelope b = new Envelope(1, 5, 0, 4);
            Envelope c = new Envelope(3, 6, -4, 1);
            Envelope d = new Envelope(2, 8, 0, 8);

            nodes.add(new HRPlusNode(new ObjectId(), a, new ObjectId()));
            nodes.add(new HRPlusNode(new ObjectId(), b, new ObjectId()));
            nodes.add(new HRPlusNode(new ObjectId(), c, new ObjectId()));
            nodes.add(new HRPlusNode(new ObjectId(), d, new ObjectId()));

            assertEquals(192, HRPlusTreeUtils.sumOfMargins(nodes), 0.000001);

        }

    @Test
        public void testPartitionByMinOverlap() {
            List<HRPlusNode> nodes = new ArrayList<HRPlusNode>();
            List<HRPlusNode> result = new ArrayList<HRPlusNode>();

            // Test a: One a line , one cluster in x-space (maybe add one for y)
            HRPlusNode a1 = new HRPlusNode(new ObjectId(), 
                    new Envelope(-12, -10, -2, 2), new ObjectId());
            HRPlusNode a2 = new HRPlusNode(new ObjectId(), 
                    new Envelope(-8, -6, -2, 2), new ObjectId());
            HRPlusNode a3 = new HRPlusNode(new ObjectId(), 
                    new Envelope(-4, -2, -2, 2), new ObjectId());
            HRPlusNode a4 = new HRPlusNode(new ObjectId(),
                    new Envelope(2, 4, -2, 2), new ObjectId());

            nodes.add(a1);
            nodes.add(a2);
            nodes.add(a3);
            nodes.add(a4);
            result.add(a1);
            result.add(a2);
            result.add(a3);

            assertEquals(result,
                    HRPlusTreeUtils.partitionByMinOverlap(
                        HRPlusTreeUtils.minXSort(nodes),
                        HRPlusTreeUtils.maxXSort(nodes)));
            assertEquals(result,
                    HRPlusTreeUtils.partitionByMinOverlap(
                        HRPlusTreeUtils.minYSort(nodes),
                        HRPlusTreeUtils.maxYSort(nodes)));
            nodes.clear();
            result.clear();

            // Test b: Evenly spaced along x-axis

            HRPlusNode b1 = new HRPlusNode(new ObjectId(), 
                    new Envelope(-12, -10, -2, 2), new ObjectId());
            HRPlusNode b2 = new HRPlusNode(new ObjectId(), 
                    new Envelope(-8, -6, -2, 2), new ObjectId());
            HRPlusNode b3 = new HRPlusNode(new ObjectId(), 
                    new Envelope(-4, -2, -2, 2), new ObjectId());
            HRPlusNode b4 = new HRPlusNode(new ObjectId(),
                    new Envelope(0, 2, -2, 2), new ObjectId());
            nodes.add(b1);
            nodes.add(b2);
            nodes.add(b3);
            nodes.add(b4);
            result.add(b1);

            assertEquals(result,
                    HRPlusTreeUtils.partitionByMinOverlap(
                        HRPlusTreeUtils.minXSort(nodes),
                        HRPlusTreeUtils.maxXSort(nodes)));
            assertEquals(result,
                    HRPlusTreeUtils.partitionByMinOverlap(
                        HRPlusTreeUtils.minYSort(nodes),
                        HRPlusTreeUtils.maxYSort(nodes)));

            nodes.clear();
            result.clear();
            // Test 3: Clusters in II and IV quadrants

            HRPlusNode c1 = new HRPlusNode(new ObjectId(), 
                    new Envelope(-6, -4, 2, 3), new ObjectId());
            HRPlusNode c2 = new HRPlusNode(new ObjectId(), 
                    new Envelope(-3, -2, 4, 5), new ObjectId());
            HRPlusNode c3 = new HRPlusNode(new ObjectId(),
                    new Envelope(-1, 0, 0, 1), new ObjectId());
            HRPlusNode c4 = new HRPlusNode(new ObjectId(), 
                    new Envelope(1, 2, -4, -3), new ObjectId());
            nodes.add(c1);
            nodes.add(c2);
            nodes.add(c3);
            nodes.add(c4);
            result.addAll(nodes.subList(0, 2));
            assertTrue(result.containsAll(
                        HRPlusTreeUtils.partitionByMinOverlap(
                            HRPlusTreeUtils.minXSort(nodes),
                            HRPlusTreeUtils.maxXSort(nodes))));

            result.clear();
            result.addAll(nodes.subList(2, 4));
            assertTrue(result.containsAll(
                        HRPlusTreeUtils.partitionByMinOverlap(
                            HRPlusTreeUtils.minYSort(nodes),
                            HRPlusTreeUtils.maxYSort(nodes))));
            result.clear();
            nodes.clear();

            // Test 4: 3 intersecting stairs , one far away

            nodes.add(c1);
            nodes.add(c2);
            nodes.add(c3);
            nodes.add(c4);
            result.addAll(nodes.subList(0, 3));
            assertTrue(result.containsAll(
                        HRPlusTreeUtils.partitionByMinOverlap(
                            HRPlusTreeUtils.minXSort(nodes),
                            HRPlusTreeUtils.maxXSort(nodes))));
            result.clear();
            nodes.clear();

        }

}
