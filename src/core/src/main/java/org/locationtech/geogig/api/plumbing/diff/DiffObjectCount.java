/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A class with the counts of changed elements between two commits, divided in trees and features
 * 
 */
public class DiffObjectCount {

    private final AtomicLong featuresAdded, featuresRemoved, featuresChanged;

    private final AtomicInteger treesAdded, treesRemoved, treesChanged;

    public DiffObjectCount() {
        featuresAdded = new AtomicLong();
        featuresRemoved = new AtomicLong();
        featuresChanged = new AtomicLong();
        treesAdded = new AtomicInteger();
        treesRemoved = new AtomicInteger();
        treesChanged = new AtomicInteger();
    }

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
     * @return
     */
    long addedFeatures(long count) {
        return featuresAdded.addAndGet(count);
    }

    /**
     * Increases the number of removed features by a given number
     * 
     * @return
     */
    long removedFeatures(long count) {
        return featuresRemoved.addAndGet(count);
    }

    /**
     * Increases the number of changed features by a given number
     * 
     * @return
     */
    long changedFeatures(long count) {
        return featuresChanged.addAndGet(count);
    }

    /**
     * Increases the number of added trees by a given number
     * 
     * @return
     */
    int addedTrees(int count) {
        return treesAdded.addAndGet(count);
    }

    /**
     * Increases the number of removed trees by a given number
     * 
     * @return
     */
    int removedTrees(int count) {
        return treesRemoved.addAndGet(count);
    }

    /**
     * Increases the number of changed trees by a given number
     * 
     * @return
     */
    int changedTrees(int count) {
        return treesChanged.addAndGet(count);
    }

    public long getFeaturesAdded() {
        return featuresAdded.longValue();
    }

    public long getFeaturesRemoved() {
        return featuresRemoved.longValue();
    }

    public long getFeaturesChanged() {
        return featuresChanged.longValue();
    }

    public int getTreesAdded() {
        return treesAdded.intValue();
    }

    public int getTreesRemoved() {
        return treesRemoved.intValue();
    }

    public int getTreesChanged() {
        return treesChanged.intValue();
    }

}
