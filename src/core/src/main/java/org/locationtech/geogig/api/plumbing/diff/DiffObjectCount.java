/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.plumbing.diff;

/**
 * A class with the counts of changed elements between two commits, divided in trees and features
 * 
 */
public class DiffObjectCount {

    private long featuresAdded, featuresRemoved, featuresChanged;

    private int treesAdded, treesRemoved, treesChanged;

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
        return featuresAdded + featuresChanged + featuresRemoved;
    }

    /**
     * Returns the sum of added, modified, and removed trees
     */
    public int treeCount() {
        return treesAdded + treesChanged + treesRemoved;
    }

    /**
     * Increases the number of added features by a given number
     */
    void addedFeatures(long count) {
        featuresAdded += count;
    }

    /**
     * Increases the number of removed features by a given number
     */
    void removedFeatures(long count) {
        featuresRemoved += count;
    }

    /**
     * Increases the number of changed features by a given number
     */
    void changedFeatures(long count) {
        featuresChanged += count;
    }

    /**
     * Increases the number of added trees by a given number
     */
    void addedTrees(int count) {
        treesAdded += count;
    }

    /**
     * Increases the number of removed trees by a given number
     */
    void removedTrees(int count) {
        treesRemoved += count;
    }

    /**
     * Increases the number of changed trees by a given number
     */
    void changedTrees(int count) {
        treesChanged += count;
    }

    public long getFeaturesAdded() {
        return featuresAdded;
    }

    public long getFeaturesRemoved() {
        return featuresRemoved;
    }

    public long getFeaturesChanged() {
        return featuresChanged;
    }

    public int getTreesAdded() {
        return treesAdded;
    }

    public int getTreesRemoved() {
        return treesRemoved;
    }

    public int getTreesChanged() {
        return treesChanged;
    }

}
