/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A class with the counts of changed elements between two commits, divided in trees and features
 * 
 * @since 1.0
 */
public class DiffObjectCount {

    private final AtomicLong featuresAdded, featuresRemoved, featuresChanged;

    private final AtomicInteger treesAdded, treesRemoved, treesChanged;

    /**
     * Constructs a new {@code DiffObjectCount} with all counters initialized to 0.
     */
    public DiffObjectCount() {
        featuresAdded = new AtomicLong();
        featuresRemoved = new AtomicLong();
        featuresChanged = new AtomicLong();
        treesAdded = new AtomicInteger();
        treesRemoved = new AtomicInteger();
        treesChanged = new AtomicInteger();
    }

    /**
     * @return a readable summary of the counters
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("trees   [ added: ").append(treesAdded).append(", changed: ").append(treesChanged)
                .append(", removed: ").append(treesRemoved).append("]\n");
        b.append("features[ added: ").append(featuresAdded).append(", changed: ")
                .append(featuresChanged).append(", removed: ").append(featuresRemoved).append("]");
        return b.toString();
    }

    /**
     * Returns the total count of modified elements (i.e. sum of added, changed, and removed trees
     * and features)
     */
    public long count() {
        return featureCount() + treeCount();
    }

    /**
     * Returns the sum of added, modified, and removed features
     */
    public long featureCount() {
        return featuresAdded.longValue() + featuresChanged.longValue()
                + featuresRemoved.longValue();
    }

    /**
     * Returns the sum of added, modified, and removed trees
     */
    public int treeCount() {
        return treesAdded.intValue() + treesChanged.intValue() + treesRemoved.intValue();
    }

    /**
     * Increases the number of added features by a given number
     * 
     * @return the number of added features
     */
    public long addedFeatures(long count) {
        return featuresAdded.addAndGet(count);
    }

    /**
     * Increases the number of removed features by a given number
     * 
     * @return the number of removed features
     */
    public long removedFeatures(long count) {
        return featuresRemoved.addAndGet(count);
    }

    /**
     * Increases the number of changed features by a given number
     * 
     * @return the number of changed features
     */
    public long changedFeatures(long count) {
        return featuresChanged.addAndGet(count);
    }

    /**
     * Increases the number of added trees by a given number
     * 
     * @return the number of added trees
     */
    public int addedTrees(int count) {
        return treesAdded.addAndGet(count);
    }

    /**
     * Increases the number of removed trees by a given number
     * 
     * @return the number of removed trees
     */
    public int removedTrees(int count) {
        return treesRemoved.addAndGet(count);
    }

    /**
     * Increases the number of changed trees by a given number
     * 
     * @return the number of changed trees
     */
    public int changedTrees(int count) {
        return treesChanged.addAndGet(count);
    }

    /**
     * @return the number of added features
     */
    public long getFeaturesAdded() {
        return featuresAdded.longValue();
    }

    /**
     * @return the number of removed features
     */
    public long getFeaturesRemoved() {
        return featuresRemoved.longValue();
    }

    /**
     * @return the number of changed features
     */
    public long getFeaturesChanged() {
        return featuresChanged.longValue();
    }

    /**
     * @return the number of added trees
     */
    public int getTreesAdded() {
        return treesAdded.intValue();
    }

    /**
     * @return the number of removed trees
     */
    public int getTreesRemoved() {
        return treesRemoved.intValue();
    }

    /**
     * @return the number of changed trees
     */
    public int getTreesChanged() {
        return treesChanged.intValue();
    }

}
