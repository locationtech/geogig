/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import java.util.concurrent.atomic.AtomicLong;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.RevObject.TYPE;

/**
 * A class that counts the changes introduced by a branch to be merged, divided in categories
 * according to how they can (or can't) be merged into the destination branch
 */
public class MergeScenarioReport {

    private final AtomicLong conflicts = new AtomicLong(), unconflictedFeatures = new AtomicLong(),
            unconflictedTrees = new AtomicLong(), merged = new AtomicLong();

    public void addConflict() {
        conflicts.incrementAndGet();

    }

    public void addUnconflicted(DiffEntry diff) {
        TYPE type = (diff.isAdd() ? diff.getNewObject() : diff.getOldObject()).getType();
        if (TYPE.FEATURE == type) {
            unconflictedFeatures.incrementAndGet();
        } else {
            unconflictedTrees.incrementAndGet();
        }
    }

    public void addMerged() {
        merged.incrementAndGet();
    }

    /**
     * Returns the number of conflicts.
     * 
     * @return
     */
    public long getConflicts() {
        return conflicts.get();
    }

    /**
     * @return the number of unconflicted features and trees.
     */
    public long getUnconflicted() {
        return unconflictedFeatures.get() + unconflictedTrees.get();
    }

    /**
     * @return the number of unconflicted features.
     */
    public long getUnconflictedFeatures() {
        return unconflictedFeatures.get();
    }

    /**
     * @return the number of unconflicted trees.
     */
    public long getUnconflictedTrees() {
        return unconflictedTrees.get();
    }

    /**
     * Returns the number of merged features.
     * 
     * @return
     */
    public long getMerged() {
        return merged.get();
    }

    @Override
    public String toString() {
        return String.format("Conflicts: %,d, merged: %,d, unconflicted: %,d", getConflicts(),
                getMerged(), getUnconflicted());
    }

}
