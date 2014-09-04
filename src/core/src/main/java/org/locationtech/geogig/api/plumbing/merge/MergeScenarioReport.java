/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.merge;

import java.util.List;

import org.locationtech.geogig.api.FeatureInfo;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A class that contains changes introduced by a branch to be merged, divided in categories
 * according to how they can (or can't) be merged into the destination branch
 * 
 * 
 */
public class MergeScenarioReport {

    List<Conflict> conflicts;

    List<DiffEntry> unconflicted;

    List<FeatureInfo> merged;

    public MergeScenarioReport() {
        conflicts = Lists.newArrayList();
        unconflicted = Lists.newArrayList();
        merged = Lists.newArrayList();
    }

    public void addConflict(Conflict conflict) {
        conflicts.add(conflict);

    }

    public void addUnconflicted(DiffEntry diff) {
        unconflicted.add(diff);
    }

    public void addMerged(FeatureInfo merged) {
        this.merged.add(merged);

    }

    /**
     * Returns a list of conflicts, with the corresponding versions involved in the conflict.
     * 
     * @return
     */
    public List<Conflict> getConflicts() {
        return ImmutableList.copyOf(conflicts);
    }

    /**
     * List of diff entries that can be applied as they are, without merging with the corresponding
     * features in the receiving branch, but overwriting them
     * 
     * @return
     */
    public List<DiffEntry> getUnconflicted() {
        return ImmutableList.copyOf(unconflicted);
    }

    /**
     * Returns a list of new features that result from the merge. These are the feature obtained as
     * output of the merge when that output is neither one of the input features to be merged and it
     * does not exist in the repository
     * 
     * @return
     */
    public List<FeatureInfo> getMerged() {
        return ImmutableList.copyOf(merged);
    }

}
