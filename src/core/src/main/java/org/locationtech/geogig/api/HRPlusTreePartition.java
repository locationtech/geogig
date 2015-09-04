/* Copyright (c) 2014 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.api;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;

/**
 * Divides a list of nodes spatially.
 * Constructor accepts a list of nodes and then chooses the optimal point at which to divide them into two groups.
 * Tracks partition details, storing them in fields.
 * <p>
 * Based on the algorithm from {@link http://dbs.mathematik.uni-marburg.de/publications/myPapers/1990/BKSS90.pdf}
 * Create a spatial partition of a list of nodes, minimizing the overlap between envelopes and the total area of envelopes bounding the partitions.
 * These metrics were explicitly stated and experimentally tested in the paper.
 */
public class HRPlusTreePartition extends HRPlusTreeUtils{

    /**
     * Area of both partitions, combined.
     */
    double area;

    /**
     * Area shared by the partitions.
     */
    double overlap;

    /**
     *  Perimeter of both partitions, combined
     */
    double margin;

    /**
     *  Index of the input list where nodes were divided.
     */
    int splitPoint;

    /**
     * Accept a list of nodes sorted by position along a one-dimensional axis.
     * Create a partition of these nodes, split at one point along the axis.
     * <p> 
     * The paper by Beckmann et. al [2] recommends a 'goodness' measure
     * minimizing overlap with area as the tiebreaker.
     * <p>
     * Record the overlap between the two segments of the partition,
     * the total area covered by the nodes in the partition, and
     * the index of the list at which nodes are divided.
     * <p>
     * Should only be called on a group of nodes that overflowed the container.
     * (Otherwise, we have no meaningful way to divide nodes into subgroups.)
     * The {@code Preconditions} check asserts this invariant.
     * <p>
     * Following the paper, we partition nodes as follows:
     * <ul>
     * <li>{@code let M = this.getMaxDegree()}
     * <li>{@code and m = this.getMinDegree()}
     * <ul><p>
     * We pick the best distribution of these M+1 nodes by iterating over (M - 2m+1) distributions and choosing the one with the best 'goodness' rating. 
     * The kth distribution has the first (m-1)+k nodes in the first group and the others in the second.
     * 
     * @param sortedNodes  the nodes to partition
     */
    public HRPlusTreePartition(List<HRPlusNode> sortedNodes){
        // Check that we have the right number of nodes
        Preconditions.checkArgument(sortedNodes.size() == this.getMaxDegree()+1, 
                "expected [%s] nodes, got [%s] nodes", sortedNodes.size(), this.getMaxDegree()+1);
        // Set up initial partition
        List<HRPlusNode> firstGroup = new ArrayList<HRPlusNode>();
        List<HRPlusNode> secondGroup = new ArrayList<HRPlusNode>();
        int numInFirstGroup = this.getMinDegree(); // -1 + (1=k)
        firstGroup.addAll((sortedNodes.subList(0, numInFirstGroup)));
        secondGroup.addAll(sortedNodes.subList(numInFirstGroup, this.getMaxDegree()+1));
        // Initialize fields for first partition
        this.area = getTotalAreaOfTwoRegions(firstGroup, secondGroup);
        this.margin = getTotalMarginOfTwoRegions(firstGroup, secondGroup);
        this.overlap = getOverlap(firstGroup, secondGroup);
        this.splitPoint = 1;
        // Track the overlap and area
        double curOverlap = this.overlap;
        double curArea = this.area;
        // Iteratively add elements of second group to the first group.
        // This process explores all the partitions.
        while(!secondGroup.isEmpty()){
            if(curOverlap < this.overlap) {
                // Keep the smallest overlap
                this.area = curArea;
                this.overlap = curOverlap;
                this.margin = getTotalMarginOfTwoRegions(firstGroup, secondGroup);
                this.splitPoint = firstGroup.size();
            } else if(curOverlap == this.overlap && curArea < this.area){
                // Same overlap? Choose the smallest total area as tiebreaker
                this.area = curArea;
                this.margin = getTotalMarginOfTwoRegions(firstGroup, secondGroup);
                this.overlap = curOverlap;
                this.splitPoint = firstGroup.size();
            }
            // Move a node from one group to the next. This is the effect
            // achieved by incrementing `k`.
            firstGroup.add(secondGroup.remove(0));
            curOverlap = getOverlap(firstGroup, secondGroup);
            curArea = getTotalAreaOfTwoRegions(firstGroup, secondGroup);
        }
    }

    /**
     * Gets the area enclosed by the partition.  
     * <p>
     * Determines how much area is required to cover both partitions in total.
     * Value is the sum of the area of the bounding box enclosing the first with the area of the box enclosing the second. 
     * 
     * @return the area enclosed by both partitions, combined 
     */
    public double getArea() {
        return this.area;
    }

    /**
     * Gets the perimeter of both partitions, combined.
     *
     * @return the perimeter covered by both partitions combined
     */
    public double getMargin() {
        return this.margin;
    }

    /**
     * Gets the overlap between bounding rectangles of the partition.
     * 
     * @return overlap between the MBR of each group in the partition
     */
    public double getOverlap() {
        return this.overlap;
    }

    /**
     * Gets the split point of this partition.
     * <p>
     * The split point is the index at which to take a subList to divide the list of nodes (given as input to the constructor) according to this partition.
     * Equal to the number of nodes in the first group.
     * 
     * @return the index at which the original list was split to create this partition
     */
    public int getSplitPoint() {
        return this.splitPoint;
    }
}
