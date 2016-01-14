/* Copyright (c) 2014 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Base class for HRPlus tree implementation of the spatial index.
 * This class contains utility methods for manipulating HRPlus trees.
 * <p>
 * Utilities are factored out because we distinguish
 * between a plain HRPlus tree and an HRPlus tree partition. The latter is
 * used to speed up insertions.
 * <p>
 * For the original idea, see
 *     [1] {@link http://www.cs.ust.hk/faculty/dimitris/PAPERS/ssdbm01.pdf}
 * and for details on insertion, see Section 4 of:
 *     [2] {@link http://dbs.mathematik.uni-marburg.de/publications/myPapers/1990/BKSS90.pdf}
 */
public class HRPlusTreeUtils {

    /**
     * Minimum degree for any container node
     */
    private static final int MIN_DEGREE = 1;

    /**
     * Maximum degree for any container node
     */
    private static final int MAX_DEGREE = 3;

    /**
     * Gets the minimum degree of a container node within the tree.
     * 
     * @return the minumum allowed degree for a container node in the tree
     */
    public int getMinDegree() {
        return MIN_DEGREE;
    }

    /**
     * Gets the maximum degree of a container node within the tree.
     * <p>
     * If, on insert, a container acquires more nodes than this value, then it will be split.
     * 
     * @return the maximum allowed degree for a container node in the tree
     */
    public int getMaxDegree() {
        return MAX_DEGREE;
    }

    /**
     * Create an envelope covering all nodes in param list.
     * Used to create a container node for the HRPlus tree.
     * 
     * @param nodes  list of nodes to create a bounding box for
     * @return envelope covering the bounding boxes of all nodes in {@param nodes}
     */
    protected static Envelope boundingBoxOf(List<HRPlusNode> nodes){
        Envelope envelope = new Envelope();
        // Iterate over nodes, expand envelope to include each.
        // We don't care about the order of iteration. Nothing fancy here.
        for(HRPlusNode node : nodes){
            node.expand(envelope);
        }
        return envelope;
    }

    /**
     * Find the overlap between bounding boxes surrounding two groups of nodes.
     * 
     * @param firstGroup  list of nodes, not null
     * @param secondGroup  list of nodes, not null
     * @return the area of the enclosing bounding box
     */
    protected static double getOverlap(List<HRPlusNode> firstGroup,
            List<HRPlusNode> secondGroup){
        return boundingBoxOf(firstGroup).intersection(boundingBoxOf(secondGroup)).getArea();
    }

    /**
     * Finds the overlap between the bounding envelopes of two nodes.
     * @param nodeA  one HRPlusNode to overlap
     * @param nodeB  the other HRPlusNode to overlap
     * @return the area shared by the envelopes of both nodes
     */
    protected static double getOverlap(HRPlusNode nodeA, HRPlusNode nodeB) {
        return nodeA.getBounds().intersection(nodeB.getBounds()).getArea();
    }

    /**
     * Find how much the area of a container holding {@param nodeA} will increase if {@param nodeB} is added to it.
     * @param nodeA  the area of this node is the baseline
     * @param nodeB  add the area of this node to existing
     * @return
     */
    protected static double getAreaEnlargement(HRPlusNode nodeA, HRPlusNode nodeB) {
        Envelope env = new Envelope();
        nodeA.expand(env);
        nodeB.expand(env);
        return env.getArea() - nodeA.getBounds().getArea();
    }

    /**
     * Get total area of the envelopes covering two groups of nodes.
     * 
     * @param firstGroup  list of nodes, not null
     * @param secondGroup  list of nodes, not null
     * @return combined area of the two envelopes
     */
    protected static double getTotalAreaOfTwoRegions(List<HRPlusNode> firstGroup, List<HRPlusNode> secondGroup){
        return boundingBoxOf(firstGroup).getArea() + boundingBoxOf(secondGroup).getArea();
    }

    /**
     * Gets total perimeter of the envelopes covering two groups of nodes.
     * 
     * @param firstGroup  list of nodes
     * @param secondGroup  list of nodes
     * @return combined perimeter of the two envelopes
     */
    protected static double getTotalMarginOfTwoRegions(List<HRPlusNode> firstGroup, List<HRPlusNode> secondGroup){
        return marginOf(boundingBoxOf(firstGroup)) + marginOf(boundingBoxOf(secondGroup));
    }

    /**
     * Gets the perimeter of one envelope.
     * 
     * @param envelope  envelope to find perimeter of
     * @return the perimeter of the envelope
     */
    protected static double marginOf(Envelope envelope){
        double height = envelope.getHeight();
        double width = envelope.getWidth(); 
        return height + height + width + width;
    }

    /**
     * The paper [2] suggests minimizing perimeters as a useful way to optimize an index.
     * Sum the perimeters of all possible partitions of nodes along one axis.
     * <p>
     * This method iterates of the valid ways of partitioning {@param nodes} and calculates the total perimeter of each partition.
     * Returns the sum of all these total perimeters.
     * 
     * @param nodes  list of nodes
     * @return total sum over all perimeters of partitioned nodes
     */
    protected static double sumOfMargins(List<HRPlusNode> nodes){
        if (nodes.isEmpty() || nodes.size() == 1) {
            return marginOf(boundingBoxOf(nodes));
        } else {
            // Begin with a one-element list and an (n-1) element list.
            List<HRPlusNode> firstGroup = new ArrayList<HRPlusNode>();
            firstGroup.addAll((nodes.subList(0, 1)));
            List<HRPlusNode> secondGroup = new ArrayList<HRPlusNode>();
            secondGroup.addAll(nodes.subList(1, nodes.size()));

            double marginValueSum = 0;
            // Iteratively add one element of the second group to the first.
            while(!secondGroup.isEmpty()){
                marginValueSum +=  marginOf(boundingBoxOf(firstGroup)) + marginOf(boundingBoxOf(secondGroup));
                HRPlusNode removed = secondGroup.remove(0);
                firstGroup.add(removed);
            }
            return marginValueSum;
        }
    }

    /**
     * Partition a list of nodes, minimizing the overlap across the partition.
     * <p>
     * Take a list of nodes, sorted by position on a one-dimensional axis.
     * Return the subset of these nodes that is closest together based on the one-dimensional axis and the total perimeter and area of their enclosing envelope.
     * 
     * @param minSort  nodes sorted by minimum position along some axis 
     * @param maxSort  same nodes, sorted by maximum position along the same axis.
     * @return sublist containing the nodes closest together
     */
    protected static List<HRPlusNode> partitionByMinOverlap(List<HRPlusNode>minSort, 
            List<HRPlusNode>maxSort){
        // Create two partitions corresponding to the two arguments.
        // This determines the minimum area/perimeter split of the nodes.
        HRPlusTreePartition minPartition = new HRPlusTreePartition(minSort);
        HRPlusTreePartition maxPartition = new HRPlusTreePartition(maxSort);
        // Extract fields from the partitions 
        double overlapMinSort = minPartition.getOverlap();
        double areaValueMinSort = minPartition.getArea();
        double overlapMaxSort = maxPartition.getOverlap();
        double areaValueMaxSort = maxPartition.getArea();
        // Choose the partition with the smallest overlap.
        // If overlaps are equal, choose the partition with the smallest area.
        if (overlapMinSort < overlapMaxSort || 
                (overlapMinSort == overlapMaxSort && 
                 areaValueMinSort <= areaValueMaxSort)) {
            int splitPointMinSort = minPartition.getSplitPoint();
            // Get the spatially smallest partition of nodes from the minsort list
            return minSort.subList(0, splitPointMinSort);
        } else {
            int splitPointMaxSort = maxPartition.getSplitPoint();
            // Get the smallest (least perimeter/area) partition of nodes from the maxsort list
            return maxSort.subList(0, splitPointMaxSort);
        }
    }

    /**
     * Sort a list of nodes by their minimum x coordinate.
     * 
     * @param nodes  list of nodes
     * @return {@param nodes} in sorted order
     */
    protected static List<HRPlusNode> minXSort(List<HRPlusNode> nodes){
        List<HRPlusNode> minXSort = new ArrayList<HRPlusNode>(nodes);
        Collections.sort(minXSort, new Comparator<HRPlusNode>() {
                public int compare(HRPlusNode n1, HRPlusNode n2) {
                return Double.compare(n1.getMinX(), n2.getMinX());
                }
                });
        return minXSort;
    }

    /**
     * Sort a list of nodes by their minimum y coordinate.
     * 
     * @param nodes  list of nodes
     * @return {@param nodes} in sorted order 
     */
    protected static List<HRPlusNode> minYSort(List<HRPlusNode> nodes){
        List<HRPlusNode> minYSort = new ArrayList<HRPlusNode>(nodes);
        Collections.sort(minYSort, new Comparator<HRPlusNode>() {
                public int compare(HRPlusNode n1, HRPlusNode n2) {
                return Double.compare(n1.getMinY(), n2.getMinY());
                }
                });
        return minYSort;
    }

    /**
     * Sort a list of nodes by their maximum x coordinate
     * 
     * @param nodes  list of nodes
     * @return {@param nodes} in sorted order
     */
    protected static List<HRPlusNode> maxXSort(List<HRPlusNode> nodes){
        List<HRPlusNode> maxXSort = new ArrayList<HRPlusNode>(nodes);
        Collections.sort(maxXSort, new Comparator<HRPlusNode>() {
                public int compare(HRPlusNode n1, HRPlusNode n2) {
                return Double.compare(n1.getMaxX(), n2.getMaxX());
                }
                });
        return maxXSort;
    }

    /**
     * Sort a list of nodes by their maximum y coordinate.
     * 
     * @param nodes  list of nodes
     * @return {@param nodes} in sorted order
     */
    protected static List<HRPlusNode> maxYSort(List<HRPlusNode> nodes){
        List<HRPlusNode> maxYSort = new ArrayList<HRPlusNode>(nodes);
        Collections.sort(maxYSort, new Comparator<HRPlusNode>() {
                public int compare(HRPlusNode n1, HRPlusNode n2) {
                return Double.compare(n1.getMaxY(), n2.getMaxY());
                }
                });
        return maxYSort;
    }

}
