/* Copyright (c) 2013 Boundless and others.
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

/**
 * A class that counts the changes introduced by a branch to be merged, divided in categories
 * according to how they can (or can't) be merged into the destination branch
 */
public class MergeScenarioReport {

    private final AtomicLong conflicts, unconflicted, merged;

    public MergeScenarioReport() {
        conflicts = new AtomicLong();
        unconflicted = new AtomicLong();
        merged = new AtomicLong();
    }

    public void addConflict() {
        conflicts.incrementAndGet();

    }

    public void addUnconflicted() {
        unconflicted.incrementAndGet();
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
     * Returns the number of unconflicted features.
     * 
     * @return
     */
    public long getUnconflicted() {
        return unconflicted.get();
    }

    /**
     * Returns the number of merged features.
     * 
     * @return
     */
    public long getMerged() {
        return merged.get();
    }

}
